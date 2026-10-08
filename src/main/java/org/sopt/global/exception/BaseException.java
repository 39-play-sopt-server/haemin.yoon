package org.sopt.global.exception;

/**
 * 예상 가능한 처리 실패를 ErrorCode와 함께 전달하는 공통 예외입니다.
 * 개별 도메인 예외가 이를 상속하면 전역 핸들러는 도메인별 예외 타입을 몰라도 응답을 만들 수 있습니다.
 */
public class BaseException extends RuntimeException {
  private final ErrorCode errorCode;

  public BaseException(ErrorCode errorCode) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }
}
