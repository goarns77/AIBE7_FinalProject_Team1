package org.example.springtestci.common.error;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
    Instant timestamp,
    int status,
    String code,
    String message,
    String path,
    String requestId,
    List<FieldError> fieldErrors) {

  public ErrorResponse {
    fieldErrors = List.copyOf(fieldErrors);
  }

  public record FieldError(String field, String message) {}
}
