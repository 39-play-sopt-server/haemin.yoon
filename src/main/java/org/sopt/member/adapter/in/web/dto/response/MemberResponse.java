package org.sopt.member.adapter.in.web.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.sopt.member.application.port.in.dto.MemberInfo;

public record MemberResponse(
    @Schema(description = "회원 ID", example = "1") long id,
    @Schema(description = "회원 이름", example = "홍길동") String name,
    @Schema(
            description = "로그인 이메일. 이메일 도입 전에 생성된 회원은 null",
            example = "hong@example.com",
            nullable = true)
        String email) {
  public static MemberResponse from(MemberInfo member) {
    return new MemberResponse(member.id(), member.name(), member.email());
  }
}
