package org.sopt.global.exception;

/** 요청 객체 누락이나 예상하지 못한 서버 오류처럼 특정 도메인에 속하지 않는 오류를 정의합니다. */
public enum GlobalErrorCode implements ErrorCode {
  INVALID_REQUEST("요청 정보가 없습니다."),
  INTERNAL_SERVER_ERROR("서버 오류가 발생했습니다.");

  private final String message;

  GlobalErrorCode(String message) {
    this.message = message;
  }

  @Override
  public String getCode() {
    // Enum 이름을 응답 코드로 사용하므로 상수명 변경 시 클라이언트에 전달되는 코드도 바뀝니다.
    return name();
  }

  @Override
  public String getMessage() {
    return message;
  }
}
