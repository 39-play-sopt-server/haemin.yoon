package org.sopt.global.docs;

import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.List;
import java.util.Map;
import org.sopt.global.exception.ErrorCode;
import org.sopt.global.response.BaseResponse;
import org.sopt.global.response.ValidationViolation;

/** 전역과 도메인 문서 설정이 사용하는 공통 오류 응답 생성기입니다. */
public final class OpenApiResponses {
  private OpenApiResponses() {}

  public static ApiResponse errorResponse(
      String description, ErrorCode errorCode, List<ValidationViolation> violations) {
    return response(
        description, new MediaType().example(BaseResponse.failure(errorCode, violations)));
  }

  public static ApiResponse errorResponses(
      String description, Map<String, BaseResponse<Void>> examples) {
    var mediaType = new MediaType();
    examples.forEach((name, example) -> mediaType.addExamples(name, new Example().value(example)));
    return response(description, mediaType);
  }

  private static ApiResponse response(String description, MediaType mediaType) {
    return new ApiResponse()
        .description(description)
        .content(
            new Content()
                .addMediaType(
                    "application/json",
                    mediaType.schema(new Schema<>().$ref("#/components/schemas/ErrorResponse"))));
  }
}
