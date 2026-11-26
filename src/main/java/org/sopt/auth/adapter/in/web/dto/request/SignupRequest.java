package org.sopt.auth.adapter.in.web.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.sopt.auth.domain.PasswordPolicy;
import org.sopt.member.domain.Member;

public record SignupRequest(
    @NotBlank
        @Size(max = Member.MAX_NAME_LENGTH)
        @Schema(description = "회원 이름. 같은 이름의 회원도 가입할 수 있습니다.", example = "홍길동")
        String name,
    @NotBlank
        @Email
        @Size(max = Member.MAX_EMAIL_LENGTH)
        @Schema(description = "로그인 이메일. 앞뒤 공백을 제거하고 소문자로 저장합니다.", example = "hong@example.com")
        String email,
    @NotBlank
        @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_BYTES)
        @Schema(
            description = "비밀번호. 8자 이상이며 UTF-8 기준 72바이트 이하입니다.",
            example = "password1234",
            format = "password")
        String password) {
  @Override
  public String toString() {
    return "SignupRequest[name=" + name + ", email=" + email + ", password=****]";
  }
}
