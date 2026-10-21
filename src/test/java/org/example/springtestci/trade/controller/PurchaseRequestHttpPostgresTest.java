package org.example.springtestci.trade.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.example.springtestci.trade.service.InternalTransferPendingWriter;
import org.example.springtestci.trade.service.PublishedListing;
import org.example.springtestci.trade.service.PublishedListingAccess;
import org.example.springtestci.trade.service.PublishedListingStatus;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@EnabledIfEnvironmentVariable(named = "P2_TRADE_TEST_ENABLED", matches = "true")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "app.features.trade-purchase-requests.enabled=true",
      "app.features.chat-api.enabled=false",
      "spring.flyway.enabled=false",
      "spring.datasource.driver-class-name=org.postgresql.Driver",
      "spring.jpa.hibernate.ddl-auto=validate"
    })
class PurchaseRequestHttpPostgresTest {

  private static final String SCHEMA =
      "p2_trade_http_" + UUID.randomUUID().toString().replace("-", "");
  private static final String JWT_SECRET = "p2-http-test-secret-that-is-at-least-32-bytes";
  private static DataSource adminDataSource;

  @LocalServerPort private int port;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private JwtEncoder jwtEncoder;

  @DynamicPropertySource
  static void configureDatabase(DynamicPropertyRegistry registry) {
    String url = requiredEnvironment("P2_TRADE_TEST_DB_URL");
    String user = requiredEnvironment("P2_TRADE_TEST_DB_USER");
    String password = requiredEnvironment("P2_TRADE_TEST_DB_PASSWORD");
    adminDataSource = new DriverManagerDataSource(url, user, password);
    new JdbcTemplate(adminDataSource).execute("create schema " + SCHEMA);
    String schemaUrl = withCurrentSchema(url, SCHEMA);
    DataSource schemaDataSource = new DriverManagerDataSource(schemaUrl, user, password);
    Flyway.configure()
        .dataSource(schemaDataSource)
        .locations("classpath:db/migration")
        .load()
        .migrate();
    createExternalFixtureTables(new JdbcTemplate(schemaDataSource));
    Flyway.configure()
        .dataSource(schemaDataSource)
        .locations("classpath:db/migration", "classpath:db/pending/trade-p2")
        .outOfOrder(true)
        .load()
        .migrate();
    createTransferFixtureTable(new JdbcTemplate(schemaDataSource));

    registry.add("spring.datasource.url", () -> schemaUrl);
    registry.add("spring.datasource.username", () -> user);
    registry.add("spring.datasource.password", () -> password);
    registry.add("app.jwt.secret", () -> JWT_SECRET);
  }

  @AfterAll
  static void tearDownDatabase() {
    if (adminDataSource != null) {
      new JdbcTemplate(adminDataSource).execute("drop schema " + SCHEMA + " cascade");
    }
  }

  @BeforeEach
  void setUpFixture() {
    jdbcTemplate.execute(
        "truncate table ownership_transfers, purchase_requests, chat_read_positions, chat_messages, chat_rooms, listings, products, users restart identity cascade");
    jdbcTemplate.update(
        "insert into users (id, email, password_hash, nickname, role, created_at, updated_at) values (1, 'seller@http.test', 'hash', 'seller', 'USER', current_timestamp, current_timestamp), (2, 'buyer@http.test', 'hash', 'buyer', 'USER', current_timestamp, current_timestamp)");
    jdbcTemplate.update(
        "insert into products (id, category, manufacturer, model_name, serial_number_fingerprint, title, owner_id, serial_number_ciphertext) values (1, 'LAPTOP', 'maker', 'model', 'http-fingerprint', 'title', 1, 'ciphertext')");
    jdbcTemplate.update(
        "insert into listings (id, product_id, seller_id, current_owner_id, status) values (1, 1, 1, 1, 'ACTIVE')");
    jdbcTemplate.update(
        "insert into chat_rooms (id, listing_id, seller_id, inquirer_id) values (1, 1, 1, 2)");
  }

  @Test
  void createsPurchaseRequestThroughRunningHttpServerAndBearerJwt() throws Exception {
    String token = tokenFor(2L);
    HttpRequest request =
        HttpRequest.newBuilder(
                URI.create(
                    "http://127.0.0.1:" + port + "/api/v1/market/listings/1/purchase-requests"))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"roomId\":1}"))
            .build();

    HttpResponse<String> response =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(201);
    assertThat(response.body()).contains("\"requestStatus\":\"PENDING\"");
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from purchase_requests where room_id = 1 and request_status = 'PENDING'",
                Long.class))
        .isEqualTo(1L);
  }

  private String tokenFor(long userId) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(Long.toString(userId))
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .build();
    return jwtEncoder
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }

  private static void createExternalFixtureTables(JdbcTemplate jdbcTemplate) {
    jdbcTemplate.execute(
        """
        create table listings (
          id bigint generated by default as identity primary key,
          product_id bigint not null references products(id),
          seller_id bigint not null references users(id),
          current_owner_id bigint not null references users(id),
          status varchar(20) not null check (status in ('ACTIVE', 'RESERVED', 'SOLD', 'CANCELLED'))
        )
        """);
  }

  private static void createTransferFixtureTable(JdbcTemplate jdbcTemplate) {
    jdbcTemplate.execute(
        """
        create table ownership_transfers (
          id bigint generated by default as identity primary key,
          product_id bigint not null references products(id),
          purchase_request_id bigint not null unique references purchase_requests(id),
          from_user_id bigint not null references users(id),
          to_user_id bigint not null references users(id),
          transfer_type varchar(20) not null,
          status varchar(20) not null,
          token_hash varchar(255),
          recipient_approved_at timestamp with time zone,
          owner_approved_at timestamp with time zone,
          created_at timestamp with time zone not null default current_timestamp
        )
        """);
  }

  private static String requiredEnvironment(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(name + " must be configured");
    }
    return value;
  }

  private static String withCurrentSchema(String url, String schema) {
    return url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
  }

  @TestConfiguration
  static class FixtureConfiguration {

    @Bean
    @Primary
    PublishedListingAccess publishedListingAccess(JdbcTemplate jdbcTemplate) {
      return new PublishedListingAccess() {
        @Override
        public Optional<PublishedListing> findById(long listingId) {
          return find(listingId, false);
        }

        @Override
        public Optional<PublishedListing> findByIdForUpdate(long listingId) {
          return find(listingId, true);
        }

        @Override
        public Map<Long, PublishedListingStatus> findStatuses(Collection<Long> listingIds) {
          return Map.of();
        }

        @Override
        public void reserve(long listingId) {
          jdbcTemplate.update(
              "update listings set status = 'RESERVED' where id = ? and status = 'ACTIVE'",
              listingId);
        }

        private Optional<PublishedListing> find(long listingId, boolean lock) {
          return jdbcTemplate
              .query(
                  "select id, product_id, seller_id, current_owner_id, status from listings where id = ?"
                      + (lock ? " for update" : ""),
                  (resultSet, rowNumber) ->
                      new PublishedListing(
                          resultSet.getLong("id"),
                          resultSet.getLong("product_id"),
                          resultSet.getLong("seller_id"),
                          resultSet.getLong("current_owner_id"),
                          PublishedListingStatus.valueOf(resultSet.getString("status"))),
                  listingId)
              .stream()
              .findFirst();
        }
      };
    }

    @Bean
    @Primary
    InternalTransferPendingWriter internalTransferPendingWriter(JdbcTemplate jdbcTemplate) {
      return command ->
          jdbcTemplate.update(
              "insert into ownership_transfers (product_id, purchase_request_id, from_user_id, to_user_id, transfer_type, status) values (?, ?, ?, ?, ?, ?)",
              command.productId(),
              command.purchaseRequestId(),
              command.fromUserId(),
              command.toUserId(),
              command.transferType(),
              command.status());
    }
  }
}
