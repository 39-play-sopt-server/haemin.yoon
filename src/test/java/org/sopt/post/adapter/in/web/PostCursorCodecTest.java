package org.sopt.post.adapter.in.web;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sopt.post.adapter.in.web.dto.PostCursorCodec;
import org.sopt.post.application.port.in.query.PostCursor;
import org.sopt.post.application.port.in.query.PostListQuery;
import org.sopt.post.domain.Category;
import org.sopt.post.domain.exception.PostErrorCode;
import org.sopt.post.domain.exception.PostException;

class PostCursorCodecTest {
  @Test
  void roundTripsPrecisionIdAndCategory() {
    for (Category category :
        new Category[] {null, Category.GENERAL, Category.QUESTION, Category.INFORMATION}) {
      var cursor =
          new PostCursor(
              LocalDateTime.of(2026, 10, 26, 12, 34, 56, 123456000), Long.MAX_VALUE, category);
      assertEquals(cursor, PostCursorCodec.decode(PostCursorCodec.encode(cursor)));
    }
    assertNull(PostCursorCodec.decode(null));
    assertNull(PostCursorCodec.decode(""));
    assertNull(PostCursorCodec.encode(null));
  }

  @ParameterizedTest
  @MethodSource("publishedCategoryValues")
  void preservesPublishedVersionOneCategoryValues(Category category, String publicValue) {
    var cursor = new PostCursor(LocalDateTime.of(2026, 10, 26, 12, 0), 42L, category);
    String published =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                ("1|2026-10-26T12:00|42|" + publicValue).getBytes(StandardCharsets.UTF_8));
    assertEquals(published, PostCursorCodec.encode(cursor));
    assertEquals(cursor, PostCursorCodec.decode(published));
  }

  static Stream<Arguments> publishedCategoryValues() {
    return Stream.of(
        Arguments.of(null, "ALL"),
        Arguments.of(Category.GENERAL, "GENERAL"),
        Arguments.of(Category.QUESTION, "QUESTION"),
        Arguments.of(Category.INFORMATION, "INFORMATION"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "2|2026-10-26T12:00|1|ALL",
        "1|invalid|1|ALL",
        "1|2026-10-26T12:00|0|ALL",
        "1|2026-10-26T12:00|-1|ALL",
        "1|2026-10-26T12:00|9223372036854775808|ALL",
        "1|2026-10-26T12:00|1|UNKNOWN",
        "1|2026-10-26T12:00|1|ALL|extra",
        "1|0000-01-01T00:00|1|ALL",
        "1|+10000-01-01T00:00|1|ALL",
        "1|2026-10-26T12:00|01|ALL"
      })
  void rejectsMalformedUnsupportedAndNonCanonicalTokens(String raw) {
    String token =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    assertEquals(
        PostErrorCode.INVALID_CURSOR,
        assertThrows(PostException.class, () -> PostCursorCodec.decode(token)).getErrorCode());
  }

  @Test
  void queryRejectsInvalidSizeAndCursorCategoryForNonHttpCallers() {
    assertThrows(PostException.class, () -> new PostListQuery(null, 0, null));
    assertThrows(PostException.class, () -> new PostListQuery(null, 101, null));
    var cursor = new PostCursor(LocalDateTime.of(2026, 10, 26, 0, 0), 1L, Category.GENERAL);
    assertEquals(
        PostErrorCode.INVALID_CURSOR,
        assertThrows(PostException.class, () -> new PostListQuery(cursor, 20, null))
            .getErrorCode());
  }
}
