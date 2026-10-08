package org.sopt.global.response;

import org.sopt.global.exception.ErrorCode;

/**
 * 모든 서버 작업에 공통으로 사용하는 응답 봉투입니다. T는 작업별 결과 데이터의 타입입니다.
 * 클라이언트는 성공 여부·코드·메시지를 동일하게 처리하고, 성공일 때만 결과를 사용합니다.
 * record로 응답 필드의 재할당을 막지만, T 자체의 불변성까지 보장하지는 않습니다.
 */
public record BaseResponse<T>(boolean success, String code, String message, T data) {
  /** 공개 생성자를 직접 사용해도 응답 상태가 모순되지 않도록 공통 규칙을 검증합니다. */
  public BaseResponse {
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("응답 코드는 비어 있을 수 없습니다.");
    }
    if (success != "SUCCESS".equals(code)) {
      throw new IllegalArgumentException("응답 성공 여부와 코드가 일치하지 않습니다.");
    }
    if (!success && data != null) {
      throw new IllegalArgumentException("실패 응답에는 데이터를 포함할 수 없습니다.");
    }
  }

  /** 성공 코드와 성공 여부를 함께 설정하며, 반환값 없는 작업의 null 데이터도 허용합니다. */
  public static <T> BaseResponse<T> success(T data, String message) {
    return new BaseResponse<>(true, "SUCCESS", message, data);
  }

  /** 실패에는 결과 데이터를 넣지 않아 부분 결과를 정상 결과로 오해하지 않게 합니다. */
  public static <T> BaseResponse<T> failure(ErrorCode errorCode) {
    return new BaseResponse<>(false, errorCode.getCode(), errorCode.getMessage(), null);
  }
}
