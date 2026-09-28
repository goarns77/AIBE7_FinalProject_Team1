package org.example.springtestci.common.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "test"})
public class LocalFileStorage implements FileStorage {

  private final Path root;

  public LocalFileStorage(@Value("${app.storage.local-root}") String root) {
    this.root = Path.of(root).toAbsolutePath().normalize();
  }

  @Override
  public String store(String originalFilename, InputStream content) throws IOException {
    Files.createDirectories(root);
    String key = UUID.randomUUID() + "-" + safeFilename(originalFilename);
    Path destination = resolve(key);
    try {
      Files.copy(content, destination, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException exception) {
      try {
        Files.deleteIfExists(destination);
      } catch (IOException cleanupException) {
        exception.addSuppressed(cleanupException);
      }
      throw exception;
    }
    return key;
  }

  @Override
  public Resource load(String key) throws IOException {
    return new UrlResource(resolve(key).toUri());
  }

  @Override
  public void delete(String key) throws IOException {
    Files.deleteIfExists(resolve(key));
  }

  private Path resolve(String key) {
    Path resolved = root.resolve(key).normalize();
    if (!resolved.startsWith(root)) {
      throw new IllegalArgumentException("Invalid storage key");
    }
    return resolved;
  }

  private String safeFilename(String originalFilename) {
    String filename =
        originalFilename == null ? "file" : Path.of(originalFilename).getFileName().toString();
    String sanitized = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    return sanitized.isBlank() ? "file" : sanitized;
  }
}
