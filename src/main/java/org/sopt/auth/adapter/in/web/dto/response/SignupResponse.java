package org.sopt.auth.adapter.in.web.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.sopt.auth.application.port.in.command.dto.SignupResult;

public record SignupResponse(
    @Schema(description = "가입한 회원 ID", example = "1") long id,
    @Schema(description = "회원 이름", example = "홍길동") String name,
    @Schema(description = "정규화된 로그인 이메일", example = "hong@example.com") String email) {
  public static SignupResponse from(SignupResult result) {
    return new SignupResponse(result.memberId(), result.name(), result.email());
  }
}
