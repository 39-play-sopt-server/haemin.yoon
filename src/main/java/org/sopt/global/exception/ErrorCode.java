package org.sopt.global.exception;

/**
 * 오류의 식별 코드와 안내 메시지를 제공하는 공통 계약입니다.
 * 서로 다른 오류 Enum을 같은 방식으로 처리하기 위해 구체적인 Enum 대신 인터페이스를 사용합니다.
 */
public interface ErrorCode {
  /** 응답에서 오류 종류를 구분하는 코드입니다. */
  String getCode();

  /** 클라이언트에 전달할 안내 메시지입니다. */
  String getMessage();
}
