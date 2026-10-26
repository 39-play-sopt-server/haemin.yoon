package org.sopt.member.adapter.in.web.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.sopt.member.domain.Member;

public record MemberResponse(
    @Schema(description = "게시글 생성 시 memberId로 전달할 회원 ID", example = "1") long id,
    @Schema(description = "회원 이름", example = "홍길동") String name) {
  public static MemberResponse from(Member member) {
    return new MemberResponse(member.getId(), member.getName());
  }
}
