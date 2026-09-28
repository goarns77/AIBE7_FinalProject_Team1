package org.example.springtestci.common.error;

public class CommonException extends RuntimeException {

  private final ErrorCode errorCode;

  public CommonException(ErrorCode errorCode) {
    super(errorCode.message());
    this.errorCode = errorCode;
  }

  public ErrorCode errorCode() {
    return errorCode;
  }
}
