package org.sopt.auth.adapter.in.web.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 로그인은 형식 오류도 가입 여부와 무관한 동일한 실패로 처리하도록 필수값만 검증합니다. */
public record LoginRequest(
    @NotBlank @Schema(description = "가입한 이메일", example = "hong@example.com") String email,
    @NotBlank @Schema(description = "비밀번호", example = "password1234", format = "password")
        String password) {
  @Override
  public String toString() {
    return "LoginRequest[email=" + email + ", password=****]";
  }
}
