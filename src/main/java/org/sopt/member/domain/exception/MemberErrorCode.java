package org.sopt.member.domain.exception;

import org.sopt.global.exception.ErrorCode;
import org.sopt.member.domain.Member;
import org.springframework.http.HttpStatus;

public enum MemberErrorCode implements ErrorCode {
  MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "존재하지 않거나 삭제된 회원입니다."),
  INVALID_MEMBER_NAME(HttpStatus.BAD_REQUEST, "INVALID_MEMBER_NAME", "회원 이름은 비어 있을 수 없습니다."),
  MEMBER_NAME_TOO_LONG(
      HttpStatus.BAD_REQUEST,
      "MEMBER_NAME_TOO_LONG",
      "회원 이름은 " + Member.MAX_NAME_LENGTH + "자를 초과할 수 없습니다."),
  MEMBER_NAME_NUL_NOT_ALLOWED(
      HttpStatus.BAD_REQUEST, "MEMBER_NAME_NUL_NOT_ALLOWED", "회원 이름에는 NUL 문자를 포함할 수 없습니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;

  MemberErrorCode(HttpStatus httpStatus, String code, String message) {
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
