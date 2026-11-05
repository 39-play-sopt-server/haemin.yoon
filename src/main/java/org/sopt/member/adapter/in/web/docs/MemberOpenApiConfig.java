package org.sopt.member.adapter.in.web.docs;

import java.util.LinkedHashMap;
import java.util.List;
import org.sopt.global.docs.OpenApiResponses;
import org.sopt.global.exception.GlobalErrorCode;
import org.sopt.global.response.BaseResponse;
import org.sopt.global.response.ValidationViolation;
import org.sopt.member.domain.exception.MemberErrorCode;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 회원 전용 응답은 회원 웹 어댑터에서 등록합니다. */
@Configuration
public class MemberOpenApiConfig {
  @Bean
  public OpenApiCustomizer memberOpenApiCustomizer() {
    return openApi -> {
      var examples = new LinkedHashMap<String, BaseResponse<Void>>();
      examples.put(
          "ValidationFailed",
          BaseResponse.failure(
              GlobalErrorCode.VALIDATION_FAILED,
              List.of(new ValidationViolation("name", "REQUIRED", "필수 입력값입니다."))));
      examples.put("InvalidRequest", BaseResponse.failure(GlobalErrorCode.INVALID_REQUEST));
      examples.put(
          "NulNotAllowed", BaseResponse.failure(MemberErrorCode.MEMBER_NAME_NUL_NOT_ALLOWED));
      openApi
          .getComponents()
          .addResponses(
              "MemberCreateBadRequest",
              OpenApiResponses.errorResponses(
                  "이름은 필수이며 최대 50자(UTF-16 코드 단위)입니다. NUL 문자는 허용하지 않습니다.", examples))
          .addResponses(
              "MemberNotFound",
              OpenApiResponses.errorResponse(
                  "존재하지 않거나 삭제된 회원입니다.", MemberErrorCode.MEMBER_NOT_FOUND, List.of()))
          .addResponses(
              "InvalidMemberId",
              OpenApiResponses.errorResponse(
                  "회원 ID는 long 범위의 정수여야 합니다.", GlobalErrorCode.INVALID_REQUEST, List.of()));
    };
  }
}
