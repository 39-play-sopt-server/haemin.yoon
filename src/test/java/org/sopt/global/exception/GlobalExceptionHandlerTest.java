package org.sopt.global.exception;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.sopt.global.response.BaseResponse;
import org.sopt.post.domain.exception.PostErrorCode;
import org.sopt.post.domain.exception.PostException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.ServletWebRequest;

class GlobalExceptionHandlerTest {
  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void validationResponseDoesNotExposeRejectedValuesOrInternalMessages() throws Exception {
    var binding =
        new org.springframework.validation.BeanPropertyBindingResult(
            new Object(), "internalRequestType");
    binding.addError(
        new org.springframework.validation.FieldError(
            "internalRequestType",
            "title",
            "secret rejected value",
            false,
            new String[] {"NotBlank"},
            null,
            "secret validation message"));
    binding.addError(
        new org.springframework.validation.FieldError(
            "internalRequestType",
            "title",
            "another secret",
            false,
            new String[] {"NotNull"},
            null,
            "another secret message"));
    binding.addError(
        new org.springframework.validation.ObjectError(
            "internalRequestType",
            new String[] {"InternalConstraintName"},
            null,
            "secret object validation message"));
    var parameter =
        new org.springframework.core.MethodParameter(
            GlobalExceptionHandlerTest.class.getDeclaredMethod("validationTarget", Object.class),
            0);
    var response =
        handler.handleException(
            new org.springframework.web.bind.MethodArgumentNotValidException(parameter, binding),
            new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse()));
    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
    String serialized = mapper.writeValueAsString(response.getBody());
    var json = mapper.readTree(serialized);
    assertEquals("VALIDATION_FAILED", json.get("code").stringValue());
    assertEquals(2, json.get("violations").size());
    assertTrue(json.get("violations").get(0).get("field").isNull());
    assertEquals("INVALID_VALUE", json.get("violations").get(0).get("code").stringValue());
    assertEquals("title", json.get("violations").get(1).get("field").stringValue());
    assertEquals("REQUIRED", json.get("violations").get(1).get("code").stringValue());
    assertFalse(serialized.contains("secret"));
    assertFalse(serialized.contains("internalRequestType"));
    assertFalse(serialized.contains("InternalConstraintName"));
  }

  private static void validationTarget(Object request) {}

  @Test
  void supportsNewDomainErrorWithoutChangingHandler() {
    ErrorCode conflict =
        new ErrorCode() {
          public String getCode() {
            return "DUPLICATE_RESOURCE";
          }

          public String getMessage() {
            return "이미 존재하는 리소스입니다.";
          }

          public HttpStatus getHttpStatus() {
            return HttpStatus.CONFLICT;
          }
        };
    var response = handler.handleBaseException(new BaseException(conflict));
    assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    assertEquals("DUPLICATE_RESOURCE", response.getBody().code());
  }

  @Test
  void translatesMissingPostInto404() {
    var response = handler.handleBaseException(new PostException(PostErrorCode.POST_NOT_FOUND));
    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    assertFalse(response.getBody().success());
    assertEquals("POST_NOT_FOUND", response.getBody().code());
    assertEquals("존재하지 않는 게시글입니다.", response.getBody().message());
    assertNull(response.getBody().data());
  }

  @Test
  void translatesDomainValidationInto400() {
    var response = handler.handleBaseException(new PostException(PostErrorCode.INVALID_TITLE));
    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertEquals("INVALID_TITLE", response.getBody().code());
  }

  @Test
  void preservesExplicitInternalServerErrorStatus() {
    var response =
        handler.handleBaseException(new BaseException(GlobalErrorCode.INTERNAL_SERVER_ERROR));
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
  }

  @Test
  void translatesUnreadableBodyInto400() throws Exception {
    var response =
        handler.handleException(
            new HttpMessageNotReadableException(
                "invalid JSON", new MockHttpInputMessage(new byte[0])),
            new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse()));
    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertEquals("INVALID_REQUEST", ((BaseResponse<?>) response.getBody()).code());
    assertNull(((BaseResponse<?>) response.getBody()).data());
  }

  @Test
  void hidesUnexpectedErrorDetailsAndLogsCause() {
    var cause = new IllegalStateException("internal secret details");
    Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    ListAppender<ILoggingEvent> capture = new ListAppender<>();
    capture.start();
    logger.addAppender(capture);
    try {
      var response = handler.handleUnexpectedException(cause);
      assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
      assertFalse(response.getBody().success());
      assertEquals("INTERNAL_SERVER_ERROR", response.getBody().code());
      assertEquals("서버 오류가 발생했습니다.", response.getBody().message());
      assertNull(response.getBody().data());
      assertEquals(1, capture.list.size());
      assertSame(
          cause, ((ThrowableProxy) capture.list.getFirst().getThrowableProxy()).getThrowable());
    } finally {
      logger.detachAppender(capture);
      capture.stop();
    }
  }
}
