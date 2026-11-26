package org.sopt.auth.domain.exception;

import org.sopt.global.exception.BaseException;

/** 인증 전용 오류 코드만 받아 공통 예외 처리에 전달합니다. */
public class AuthException extends BaseException {
  public AuthException(AuthErrorCode errorCode) {
    super(errorCode);
  }
}
