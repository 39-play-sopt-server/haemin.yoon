package org.sopt.auth.adapter.in.web.docs;

import java.util.LinkedHashMap;
import java.util.List;
import org.sopt.auth.domain.exception.AuthErrorCode;
import org.sopt.global.docs.OpenApiResponses;
import org.sopt.global.exception.GlobalErrorCode;
import org.sopt.global.response.BaseResponse;
import org.sopt.global.response.ValidationViolation;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 인증 전용 응답은 인증 웹 어댑터에서 등록합니다. 공통 401 응답(Unauthorized)은 전역 문서 설정이 관리합니다. */
@Configuration
public class AuthOpenApiConfig {
  @Bean
  public OpenApiCustomizer authOpenApiCustomizer() {
    return openApi -> {
      var signupExamples = new LinkedHashMap<String, BaseResponse<Void>>();
      signupExamples.put(
          "ValidationFailed",
          BaseResponse.failure(
              GlobalErrorCode.VALIDATION_FAILED,
              List.of(new ValidationViolation("email", "INVALID_VALUE", "입력값이 올바르지 않습니다."))));
      signupExamples.put("InvalidPassword", BaseResponse.failure(AuthErrorCode.INVALID_PASSWORD));
      signupExamples.put("InvalidRequest", BaseResponse.failure(GlobalErrorCode.INVALID_REQUEST));
      openApi
          .getComponents()
          .addResponses(
              "SignupBadRequest",
              OpenApiResponses.errorResponses(
                  "이름은 50자 이하, 이메일은 올바른 형식이며 비밀번호는 8자 이상·UTF-8 기준 72바이트 이하입니다.", signupExamples))
          .addResponses(
              "LoginBadRequest",
              OpenApiResponses.errorResponse(
                  "이메일과 비밀번호는 필수입니다.",
                  GlobalErrorCode.VALIDATION_FAILED,
                  List.of(new ValidationViolation("email", "REQUIRED", "필수 입력값입니다."))))
          .addResponses(
              "InvalidCredentials",
              OpenApiResponses.errorResponse(
                  "이메일이 없거나 비밀번호가 틀린 경우 모두 같은 응답입니다.",
                  AuthErrorCode.INVALID_CREDENTIALS,
                  List.of()));
    };
  }
}
