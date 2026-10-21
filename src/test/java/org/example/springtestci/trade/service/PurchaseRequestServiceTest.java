package org.example.springtestci.trade.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.example.springtestci.chat.domain.ChatRoom;
import org.example.springtestci.chat.repository.ChatRoomRepository;
import org.example.springtestci.common.error.CommonException;
import org.example.springtestci.trade.entity.PurchaseRequest;
import org.example.springtestci.trade.entity.PurchaseRequestStatus;
import org.example.springtestci.trade.entity.TransactionStatus;
import org.example.springtestci.trade.repository.PurchaseRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PurchaseRequestServiceTest {

  private static final long REQUEST_ID = 41L;
  private static final long ROOM_ID = 51L;
  private static final long LISTING_ID = 61L;
  private static final long PRODUCT_ID = 71L;
  private static final long SELLER_ID = 81L;
  private static final long BUYER_ID = 91L;

  @Mock private PurchaseRequestRepository requestRepository;
  @Mock private ChatRoomRepository roomRepository;
  @Mock private PublishedListingAccess listingAccess;
  @Mock private InternalTransferPendingWriter transferWriter;

  private PurchaseRequestService service;

  @BeforeEach
  void setUp() {
    service =
        new PurchaseRequestService(
            requestRepository, roomRepository, listingAccess, transferWriter);
  }

  @Test
  void createsPendingRequestForRoomInquirer() {
    givenRoom();
    when(requestRepository.findPendingByRoomId(ROOM_ID)).thenReturn(Optional.empty());
    when(listingAccess.findByIdForUpdate(LISTING_ID)).thenReturn(Optional.of(activeListing()));
    when(requestRepository.save(any()))
        .thenReturn(
            PurchaseRequest.restore(
                REQUEST_ID, ROOM_ID, LISTING_ID, PurchaseRequestStatus.PENDING, null));

    PurchaseRequestService.CreateResult result = service.create(LISTING_ID, ROOM_ID, BUYER_ID);

    assertThat(result.created()).isTrue();
    assertThat(result.response().requestStatus()).isEqualTo(PurchaseRequestStatus.PENDING);
    assertThat(result.response().transactionStatus()).isNull();
  }

  @Test
  void reusesExistingPendingRequestWithoutLockingListing() {
    givenRoom();
    PurchaseRequest existing =
        PurchaseRequest.restore(
            REQUEST_ID, ROOM_ID, LISTING_ID, PurchaseRequestStatus.PENDING, null);
    when(requestRepository.findPendingByRoomId(ROOM_ID)).thenReturn(Optional.of(existing));
    when(listingAccess.findById(LISTING_ID)).thenReturn(Optional.of(activeListing()));

    PurchaseRequestService.CreateResult result = service.create(LISTING_ID, ROOM_ID, BUYER_ID);

    assertThat(result.created()).isFalse();
    assertThat(result.response().requestId()).isEqualTo(REQUEST_ID);
    verify(listingAccess, never()).findByIdForUpdate(LISTING_ID);
    verify(requestRepository, never()).save(any());
  }

  @Test
  void hidesForeignRoomAsNotFound() {
    when(roomRepository.findById(ROOM_ID))
        .thenReturn(
            Optional.of(new ChatRoom(ROOM_ID, LISTING_ID, SELLER_ID, BUYER_ID, Instant.now())));

    assertThatThrownBy(() -> service.create(LISTING_ID, ROOM_ID, 999L))
        .isInstanceOf(CommonException.class);
  }

  @Test
  void acceptsRequestAndCreatesTransferForSelectedBuyer() {
    PurchaseRequest pending =
        PurchaseRequest.restore(
            REQUEST_ID, ROOM_ID, LISTING_ID, PurchaseRequestStatus.PENDING, null);
    when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pending));
    when(requestRepository.findByIdForUpdate(REQUEST_ID)).thenReturn(Optional.of(pending));
    when(requestRepository.findReservedByListingId(LISTING_ID)).thenReturn(Optional.empty());
    givenRoom();
    when(listingAccess.findByIdForUpdate(LISTING_ID)).thenReturn(Optional.of(activeListing()));

    var response = service.accept(REQUEST_ID, SELLER_ID);

    assertThat(response.requestStatus()).isEqualTo(PurchaseRequestStatus.ACCEPTED);
    assertThat(response.transactionStatus()).isEqualTo(TransactionStatus.RESERVED);
    verify(listingAccess).reserve(LISTING_ID);
    ArgumentCaptor<InternalTransferPendingWriter.Command> command =
        ArgumentCaptor.forClass(InternalTransferPendingWriter.Command.class);
    verify(transferWriter).createPending(command.capture());
    assertThat(command.getValue().productId()).isEqualTo(PRODUCT_ID);
    assertThat(command.getValue().purchaseRequestId()).isEqualTo(REQUEST_ID);
    assertThat(command.getValue().fromUserId()).isEqualTo(SELLER_ID);
    assertThat(command.getValue().toUserId()).isEqualTo(BUYER_ID);
    assertThat(command.getValue().transferType()).isEqualTo("INTERNAL");
    assertThat(command.getValue().status()).isEqualTo("PENDING");
  }

  @Test
  void rejectsSellerAttemptToCreateBuyerRequest() {
    givenRoom();

    assertThatThrownBy(() -> service.create(LISTING_ID, ROOM_ID, SELLER_ID))
        .isInstanceOf(CommonException.class);
    verify(requestRepository, never()).save(any());
  }

  private void givenRoom() {
    when(roomRepository.findById(ROOM_ID))
        .thenReturn(
            Optional.of(new ChatRoom(ROOM_ID, LISTING_ID, SELLER_ID, BUYER_ID, Instant.now())));
  }

  private PublishedListing activeListing() {
    return new PublishedListing(
        LISTING_ID, PRODUCT_ID, SELLER_ID, SELLER_ID, PublishedListingStatus.ACTIVE);
  }
}
