package org.sopt.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.stream.Stream;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sopt.global.exception.GlobalErrorCode;

import static org.junit.jupiter.api.Assertions.*;

class BaseResponseTest {
  @Test
  void validationResponseOwnsAnImmutableSnapshotOfViolations() {
    var violations = new ArrayList<>(List.of(
        new ValidationViolation("title", "REQUIRED", "필수 입력값입니다.")));
    var response = BaseResponse.failure(GlobalErrorCode.VALIDATION_FAILED, violations);
    violations.clear();
    assertEquals(1, response.violations().size());
    assertEquals("title", response.violations().getFirst().field());
    assertThrows(UnsupportedOperationException.class, () -> response.violations().clear());
  }

  @Test
  void rejectsSuccessContainingValidationErrors() {
    assertThrows(IllegalArgumentException.class, () -> new BaseResponse<>(true, "SUCCESS", "성공", null,
        List.of(new ValidationViolation("title", "REQUIRED", "필수 입력값입니다."))));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void rejectsMissingMessage(String message) {
    assertThrows(IllegalArgumentException.class,
        () -> BaseResponse.success(null, message));
  }

  @Test
  void retainsNullDataEvenWhenGlobalJacksonConfigOmitsNulls() {
    var mapper = JsonMapper.builder()
        .changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(
            JsonInclude.Include.NON_NULL))
        .build();
    var json = mapper.readTree(mapper.writeValueAsString(
        BaseResponse.failure(GlobalErrorCode.INVALID_REQUEST)));
    assertTrue(json.has("data"));
    assertTrue(json.get("data").isNull());
    assertEquals(5, json.size());
    assertTrue(json.get("violations").isArray());
    assertEquals(0, json.get("violations").size());
  }

  static Stream<Arguments> invalidResponses() {
    return Stream.of(
        Arguments.of(true, "INVALID_REQUEST", null),
        Arguments.of(false, "SUCCESS", null),
        Arguments.of(false, "INVALID_REQUEST", "실패 응답 데이터"),
        Arguments.of(true, null, null),
        Arguments.of(false, null, null),
        Arguments.of(false, "", null),
        Arguments.of(false, " ", null));
  }

  @ParameterizedTest
  @MethodSource("invalidResponses")
  void rejectsInconsistentResponses(boolean success, String code, String data) {
    assertThrows(IllegalArgumentException.class,
        () -> new BaseResponse<>(success, code, "메시지", data));
  }

  @Test
  void permitsSuccessWithAndWithoutData() {
    var withData = BaseResponse.success("게시글", "조회 성공");
    assertTrue(withData.success());
    assertEquals("SUCCESS", withData.code());
    assertEquals("게시글", withData.data());

    BaseResponse<Void> withoutData = BaseResponse.success(null, "작성 성공");
    assertTrue(withoutData.success());
    assertNull(withoutData.data());
  }

  @Test
  void permitsFailureWithoutData() {
    var response = BaseResponse.failure(GlobalErrorCode.INVALID_REQUEST);
    assertFalse(response.success());
    assertEquals("INVALID_REQUEST", response.code());
    assertEquals("요청 형식이나 값이 올바르지 않습니다.", response.message());
    assertNull(response.data());
  }
}
