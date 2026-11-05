package org.sopt.member.adapter.in.web.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.sopt.member.domain.Member;

public record CreateMemberRequest(
    @NotBlank
        @Size(max = Member.MAX_NAME_LENGTH)
        @Schema(description = "회원 이름. 같은 이름의 회원도 생성할 수 있습니다.", example = "홍길동")
        String name) {}
