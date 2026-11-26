package org.sopt.auth.adapter.in.web.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.sopt.auth.application.port.in.command.dto.LoginResult;

public record LoginResponse(
    @Schema(description = "Authorization: Bearer 헤더로 전달할 Access Token") String accessToken,
    @Schema(description = "토큰 타입", example = "Bearer") String tokenType,
    @Schema(description = "Access Token 유효 시간(초)", example = "1800") long expiresIn) {
  public static LoginResponse from(LoginResult result) {
    return new LoginResponse(result.accessToken(), "Bearer", result.expiresInSeconds());
  }
}
