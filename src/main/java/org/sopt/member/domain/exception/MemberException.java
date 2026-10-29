package org.sopt.member.domain.exception;

import org.sopt.global.exception.BaseException;

/** 회원 전용 오류 코드만 받아 공통 예외 처리에 전달합니다. */
public class MemberException extends BaseException {
  public MemberException(MemberErrorCode errorCode) {
    super(errorCode);
  }
}
