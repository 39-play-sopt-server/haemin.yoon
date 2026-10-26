package org.sopt.post.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.sopt.post.domain.exception.PostException;

class PostLengthValidationTest {
  @Test
  void supplementaryCharactersCountAsTwoUtf16Units() {
    var post = new Post("😀".repeat(100), "본문", Category.GENERAL, member("작성자"));
    assertEquals("😀".repeat(100), post.getTitle());
    var exception =
        assertThrows(
            PostException.class,
            () -> new Post("😀".repeat(101), "본문", Category.GENERAL, member("작성자")));
    assertEquals("TITLE_TOO_LONG", exception.getErrorCode().getCode());
  }

  @Test
  void acceptsMaximumLengthsWithoutTruncatingText() {
    var post =
        new Post("제".repeat(200), "본".repeat(10000), Category.GENERAL, member("작".repeat(50)));
    assertEquals("제".repeat(200), post.getTitle());
    assertEquals("본".repeat(10000), post.getContent());
    assertEquals("작".repeat(50), post.getMember().getName());
    post.update("수".repeat(200), "정".repeat(10000));
    assertEquals("수".repeat(200), post.getTitle());
    assertEquals("정".repeat(10000), post.getContent());
  }

  @ParameterizedTest
  @CsvSource({"title,201,TITLE_TOO_LONG", "content,10001,CONTENT_TOO_LONG"})
  void rejectsOversizedFieldsOnCreation(String field, int length, String code) {
    var exception =
        assertThrows(
            PostException.class,
            () ->
                new Post(
                    field.equals("title") ? "가".repeat(length) : "제목",
                    field.equals("content") ? "가".repeat(length) : "본문",
                    Category.GENERAL,
                    member("작성자")));
    assertEquals(code, exception.getErrorCode().getCode());
  }

  @ParameterizedTest
  @CsvSource({"title,201,TITLE_TOO_LONG", "content,10001,CONTENT_TOO_LONG"})
  void oversizedUpdatePreservesBothFields(String field, int length, String code) {
    var post = new Post("기존 제목", "기존 본문", Category.GENERAL, member("작성자"));
    var exception =
        assertThrows(
            PostException.class,
            () ->
                post.update(
                    field.equals("title") ? "가".repeat(length) : "변경 제목",
                    field.equals("content") ? "가".repeat(length) : "변경 본문"));
    assertEquals(code, exception.getErrorCode().getCode());
    assertEquals("기존 제목", post.getTitle());
    assertEquals("기존 본문", post.getContent());
  }

  private static org.sopt.member.domain.Member member(String name) {
    return new org.sopt.member.domain.Member(name);
  }
}
