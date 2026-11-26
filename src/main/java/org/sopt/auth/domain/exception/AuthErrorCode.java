package org.sopt.auth.domain.exception;

import org.sopt.auth.domain.PasswordPolicy;
import org.sopt.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AuthErrorCode implements ErrorCode {
  /** 이메일이 없는 경우와 비밀번호가 틀린 경우를 구분하지 않아 가입 여부를 드러내지 않습니다. */
  INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다."),
  INVALID_PASSWORD(
      HttpStatus.BAD_REQUEST,
      "INVALID_PASSWORD",
      "비밀번호는 "
          + PasswordPolicy.MIN_LENGTH
          + "자 이상, UTF-8 기준 "
          + PasswordPolicy.MAX_BYTES
          + "바이트 이하여야 합니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;

  AuthErrorCode(HttpStatus httpStatus, String code, String message) {
    this.httpStatus = httpStatus;
    this.code = code;
    this.message = message;
  }

  @Override
  public HttpStatus getHttpStatus() {
    return httpStatus;
  }

  @Override
  public String getCode() {
    return code;
  }

  @Override
  public String getMessage() {
    return message;
  }
}
