package org.sopt.post.adapter.in.web.docs;

import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.sopt.global.docs.OpenApiResponses;
import org.sopt.global.exception.GlobalErrorCode;
import org.sopt.global.response.BaseResponse;
import org.sopt.global.response.ValidationViolation;
import org.sopt.post.domain.exception.PostErrorCode;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 게시글 전용 응답을 공통 OpenAPI 명세에 추가합니다. */
@Configuration
public class PostOpenApiConfig {
  @Bean
  public OpenApiCustomizer postOpenApiCustomizer() {
    return openApi ->
        openApi
            .getComponents()
            .addResponses("PostListBadRequest", listRequestErrors())
            .addResponses(
                "InvalidPostId",
                OpenApiResponses.errorResponse(
                    "게시글 ID는 long 범위의 정수여야 합니다. 숫자가 아니거나 범위를 벗어나면 INVALID_REQUEST를 반환합니다.",
                    GlobalErrorCode.INVALID_REQUEST,
                    List.of()))
            .addResponses(
                "PostCreateBadRequest",
                requestErrors(
                    "제목·본문·작성자는 공백일 수 없고 카테고리는 필수입니다(VALIDATION_FAILED). "
                        + "요청 스키마의 maxLength 초과 시에도 VALIDATION_FAILED를 반환하며 필드 오류 코드는 INVALID_LENGTH입니다. "
                        + "본문 누락, 잘못된 JSON 또는 허용되지 않은 카테고리는 INVALID_REQUEST를 반환합니다.",
                    List.of("title", "content", "category", "author")))
            .addResponses(
                "PostUpdateBadRequest",
                requestErrors(
                    "수정할 제목·본문은 공백일 수 없습니다(VALIDATION_FAILED). "
                        + "요청 스키마의 maxLength 초과 시에도 VALIDATION_FAILED를 반환하며 필드 오류 코드는 INVALID_LENGTH입니다. "
                        + "게시글 ID 형식 오류, 본문 누락 또는 잘못된 JSON은 INVALID_REQUEST를 반환합니다.",
                    List.of("title", "content")))
            .addResponses(
                "PostNotFound",
                OpenApiResponses.errorResponse(
                    "해당 ID의 게시글이 없습니다.", PostErrorCode.POST_NOT_FOUND, List.of()));
  }

  private ApiResponse listRequestErrors() {
    Map<String, BaseResponse<Void>> examples = new LinkedHashMap<>();
    examples.put("InvalidPagination", BaseResponse.failure(PostErrorCode.INVALID_PAGINATION));
    examples.put("InvalidRequest", BaseResponse.failure(GlobalErrorCode.INVALID_REQUEST));
    return OpenApiResponses.errorResponses(
        "page는 0 이상, size는 1~100이어야 하고 page * size는 2147483647 이하여야 합니다(INVALID_PAGINATION). "
            + "정수가 아닌 page·size, int 범위를 벗어나는 숫자 또는 허용되지 않은 category는 INVALID_REQUEST를 반환합니다.",
        examples);
  }

  private ApiResponse requestErrors(String description, List<String> fields) {
    Map<String, BaseResponse<Void>> examples = new LinkedHashMap<>();
    var violations =
        fields.stream()
            .sorted()
            .map(field -> new ValidationViolation(field, "REQUIRED", "필수 입력값입니다."))
            .toList();
    examples.put(
        "ValidationFailed", BaseResponse.failure(GlobalErrorCode.VALIDATION_FAILED, violations));
    examples.put("InvalidRequest", BaseResponse.failure(GlobalErrorCode.INVALID_REQUEST));
    return OpenApiResponses.errorResponses(description, examples);
  }
}
