package org.sopt.member.adapter.in.web.docs;

import java.util.List;
import org.sopt.global.docs.OpenApiResponses;
import org.sopt.global.exception.GlobalErrorCode;
import org.sopt.member.domain.exception.MemberErrorCode;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 회원 전용 응답은 회원 웹 어댑터에서 등록합니다. 다른 도메인의 API도 이 응답을 참조합니다. */
@Configuration
public class MemberOpenApiConfig {
  @Bean
  public OpenApiCustomizer memberOpenApiCustomizer() {
    return openApi ->
        openApi
            .getComponents()
            .addResponses(
                "MemberNotFound",
                OpenApiResponses.errorResponse(
                    "존재하지 않거나 삭제된 회원입니다.", MemberErrorCode.MEMBER_NOT_FOUND, List.of()))
            .addResponses(
                "MemberEmailDuplicated",
                OpenApiResponses.errorResponse(
                    "탈퇴 회원을 포함해 이미 가입된 이메일입니다. 대소문자만 다른 이메일도 같은 이메일입니다.",
                    MemberErrorCode.MEMBER_EMAIL_DUPLICATED,
                    List.of()))
            .addResponses(
                "InvalidMemberId",
                OpenApiResponses.errorResponse(
                    "회원 ID는 long 범위의 정수여야 합니다.", GlobalErrorCode.INVALID_REQUEST, List.of()));
  }
}
