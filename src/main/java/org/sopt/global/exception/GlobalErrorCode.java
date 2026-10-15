package org.sopt.global.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/** 외부에 공개해도 되는 메시지만 선언합니다. 예외 메시지와 요청 원문은 응답에 사용하지 않습니다. */
public enum GlobalErrorCode implements ErrorCode {
  INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 형식이나 값이 올바르지 않습니다."),
  VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "입력값을 확인해주세요."),
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다."),
  FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다."),
  RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "지원하지 않는 HTTP 메서드입니다."),
  NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE", "지원하지 않는 응답 형식입니다."),
  CONFLICT(HttpStatus.CONFLICT, "CONFLICT", "리소스의 현재 상태와 요청이 충돌합니다."),
  PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "PAYLOAD_TOO_LARGE", "요청 크기가 허용 범위를 초과했습니다."),
  UNSUPPORTED_MEDIA_TYPE(
      HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "지원하지 않는 요청 본문 형식입니다."),
  TOO_MANY_REQUESTS(
      HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", "요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),
  INTERNAL_SERVER_ERROR(
      HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다."),
  SERVICE_UNAVAILABLE(
      HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", "일시적으로 서비스를 이용할 수 없습니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;

  GlobalErrorCode(HttpStatus httpStatus, String code, String message) {
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

  /** 프레임워크 오류의 본문을 선택합니다. 실제 HTTP 상태와 헤더는 호출자가 보존합니다. */
  public static GlobalErrorCode fromStatus(HttpStatusCode status) {
    for (GlobalErrorCode errorCode : values()) {
      if (errorCode.httpStatus.value() == status.value()) {
        return errorCode;
      }
    }
    return status.is4xxClientError() ? INVALID_REQUEST : INTERNAL_SERVER_ERROR;
  }
}
