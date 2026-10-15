package org.sopt.global.exception;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sopt.global.response.BaseResponse;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** MVC 밖의 서블릿 오류 디스패치도 Boot 기본 HTML/Map 대신 공통 응답으로 처리합니다. */
@Hidden
@RestController
public class ApiErrorController implements ErrorController {
  private static final Logger LOGGER = LoggerFactory.getLogger(ApiErrorController.class);

  @RequestMapping("${server.error.path:${error.path:/error}}")
  public ResponseEntity<BaseResponse<Void>> error(HttpServletRequest request) {
    Object attribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
    int value =
        attribute instanceof Integer status && status >= 400 && status <= 599 ? status : 500;
    HttpStatusCode status = HttpStatusCode.valueOf(value);
    if (status.is5xxServerError()) {
      Object cause = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
      LOGGER.error(
          "서블릿 요청 처리 실패: status={}",
          value,
          cause instanceof Throwable throwable ? throwable : null);
    }
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_JSON)
        .body(BaseResponse.failure(GlobalErrorCode.fromStatus(status)));
  }
}
