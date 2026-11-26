package org.sopt.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import java.util.Set;
import org.sopt.global.docs.OpenApiResponses;
import org.sopt.global.exception.GlobalErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 공통 오류 응답과 인증 방식을 실제 JSON 계약에 맞춰 문서화합니다. 인증이 필요한 API는 bearerAuth 요구 사항을 선언합니다. */
@Configuration
public class OpenApiConfig {
  @Bean
  public OpenAPI openApi() {
    var violation =
        new ObjectSchema()
            .addProperty(
                "field",
                new Schema<String>()
                    .types(Set.of("string", "null"))
                    .description("오류 필드. 객체 전체 오류는 null"))
            .addProperty("code", new StringSchema())
            .addProperty("message", new StringSchema());
    violation.setRequired(List.of("field", "code", "message"));
    var error = new ObjectSchema();
    error
        .addProperty("success", new BooleanSchema()._enum(List.of(false)))
        .addProperty("code", new StringSchema())
        .addProperty("message", new StringSchema())
        .addProperty("data", new Schema<>().types(Set.of("null")))
        .addProperty("violations", new ArraySchema().items(violation));
    error.setRequired(List.of("success", "code", "message", "data", "violations"));
    var components =
        new Components()
            .addSchemas("ErrorResponse", error)
            .addSecuritySchemes(
                "bearerAuth",
                new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("POST /auth/login으로 발급한 Access Token"))
            .addResponses(
                "Unauthorized",
                OpenApiResponses.errorResponse(
                    "토큰이 없거나 유효하지 않으며, 세션 만료·로그아웃·탈퇴도 포함합니다.",
                    GlobalErrorCode.UNAUTHORIZED,
                    List.of()))
            .addResponses(
                "Forbidden",
                OpenApiResponses.errorResponse(
                    "요청에 필요한 권한이나 재발급 헤더가 없습니다.", GlobalErrorCode.FORBIDDEN, List.of()))
            .addResponses(
                "InternalServerError",
                OpenApiResponses.errorResponse(
                    "예기치 않은 서버 오류", GlobalErrorCode.INTERNAL_SERVER_ERROR, List.of()));
    return new OpenAPI()
        .info(
            new Info()
                .title("PLAY SOPT SERVER Assignment API")
                .version("1.0")
                .description("아이셔버 아이셔"))
        .components(components);
  }
}
