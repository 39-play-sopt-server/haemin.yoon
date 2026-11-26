package org.sopt.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.sopt.global.exception.ErrorCode;
import org.sopt.global.exception.GlobalErrorCode;
import org.sopt.global.response.BaseResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

/** 컨트롤러에 도달하기 전 보안 필터에서 거부한 요청도 공통 오류 응답으로 반환합니다. */
public class SecurityErrorResponder implements AuthenticationEntryPoint, AccessDeniedHandler {
  private final JsonMapper jsonMapper;

  public SecurityErrorResponder(JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    write(response, GlobalErrorCode.UNAUTHORIZED);
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    write(response, GlobalErrorCode.FORBIDDEN);
  }

  private void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
    response.setStatus(errorCode.getHttpStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    jsonMapper.writeValue(response.getOutputStream(), BaseResponse.failure(errorCode));
  }
}
