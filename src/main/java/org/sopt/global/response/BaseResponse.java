package org.sopt.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.sopt.global.exception.ErrorCode;

/**
 * 모든 서버 작업에 공통으로 사용하는 응답 봉투입니다. T는 작업별 결과 데이터의 타입입니다. 클라이언트는 성공 여부·코드·메시지를 동일하게 처리하고, 성공일 때만 결과를
 * 사용합니다. record로 응답 필드의 재할당을 막지만, T 자체의 불변성까지 보장하지는 않습니다.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record BaseResponse<T>(
    @Schema(description = "요청 성공 여부", example = "true") boolean success,
    @Schema(description = "성공은 SUCCESS, 실패는 오류 코드", example = "SUCCESS") String code,
    @Schema(description = "처리 결과 안내") String message,
    @Schema(description = "조회 결과. 생성·수정·삭제 및 실패 응답에서는 null") T data,
    @Schema(description = "필드별 검증 오류. 성공과 일반 오류 응답에서는 빈 배열", example = "[]")
        List<ValidationViolation> violations) {
  /** 기존 성공·일반 실패 응답의 생성 경로는 빈 검증 목록을 사용합니다. */
  public BaseResponse(boolean success, String code, String message, T data) {
    this(success, code, message, data, List.of());
  }

  /** 공개 생성자를 직접 사용해도 응답 상태가 모순되지 않도록 공통 규칙을 검증합니다. */
  public BaseResponse {
    violations = List.copyOf(violations);
    if (success && !violations.isEmpty()) {
      throw new IllegalArgumentException("성공 응답에는 검증 오류를 포함할 수 없습니다.");
    }
    if (message == null || message.isBlank()) {
      throw new IllegalArgumentException("응답 메시지는 비어 있을 수 없습니다.");
    }
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

  public static <T> BaseResponse<T> failure(
      ErrorCode errorCode, List<ValidationViolation> violations) {
    return new BaseResponse<>(false, errorCode.getCode(), errorCode.getMessage(), null, violations);
  }
}
