package org.sopt.global.exception;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sopt.global.response.BaseResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** 도메인 실패와 Spring MVC 오류를 동일한 JSON 계약으로 변환합니다. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @Override
  protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    var body =
        BaseResponse.failure(
            GlobalErrorCode.VALIDATION_FAILED,
            RequestValidationErrors.from(exception.getBindingResult()));
    return handleExceptionInternal(exception, body, headers, status, request);
  }

  @ExceptionHandler(BaseException.class)
  public ResponseEntity<BaseResponse<Void>> handleBaseException(BaseException exception) {
    ErrorCode code = exception.getErrorCode();
    if (code.getHttpStatus().is5xxServerError()) {
      LOGGER.error("서버 요청 처리 실패: code={}", code.getCode(), exception);
    }
    return ResponseEntity.status(code.getHttpStatus())
        .contentType(MediaType.APPLICATION_JSON)
        .body(BaseResponse.failure(code));
  }

  /** MVC가 결정한 상태와 Allow 등의 헤더, 응답 커밋 여부 처리는 상위 구현에 맡깁니다. */
  @Override
  protected @Nullable ResponseEntity<Object> handleExceptionInternal(
      Exception exception,
      @Nullable Object body,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    if (status.is5xxServerError()) {
      LOGGER.error("Spring MVC 요청 처리 실패: status={}", status.value(), exception);
    }
    return super.handleExceptionInternal(exception, body, headers, status, request);
  }

  @Override
  protected ResponseEntity<Object> createResponseEntity(
      @Nullable Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    HttpHeaders responseHeaders = new HttpHeaders();
    responseHeaders.putAll(headers);
    // 오류 응답은 항상 JSON입니다. Accept 불일치(406)도 같은 계약으로 설명합니다.
    responseHeaders.setContentType(MediaType.APPLICATION_JSON);
    Object responseBody =
        body instanceof BaseResponse<?>
            ? body
            : BaseResponse.failure(GlobalErrorCode.fromStatus(status));
    return new ResponseEntity<>(responseBody, responseHeaders, status);
  }

  /** checked exception도 누락하지 않되, JVM의 Error까지 복구 가능한 예외로 취급하지 않습니다. */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<BaseResponse<Void>> handleUnexpectedException(Exception exception) {
    LOGGER.error("서버 요청 처리 중 예기치 않은 오류가 발생했습니다.", exception);
    return ResponseEntity.internalServerError()
        .contentType(MediaType.APPLICATION_JSON)
        .body(BaseResponse.failure(GlobalErrorCode.INTERNAL_SERVER_ERROR));
  }
}
