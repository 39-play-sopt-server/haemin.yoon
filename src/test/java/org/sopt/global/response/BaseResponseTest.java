package org.sopt.global.response;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sopt.global.exception.GlobalErrorCode;

import static org.junit.jupiter.api.Assertions.*;

class BaseResponseTest {
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
    assertEquals("요청 정보가 없습니다.", response.message());
    assertNull(response.data());
  }
}
