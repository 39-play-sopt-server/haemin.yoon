package org.sopt.post.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.sopt.post.domain.exception.PostException;

class PostTextValidationTest {
  @ParameterizedTest
  @CsvSource({
    "title,0", "title,1", "title,3",
    "content,0", "content,1", "content,3"
  })
  void rejectsNulAnywhereInCreationText(String field, int position) {
    String invalid = new StringBuilder("텍스트").insert(position, '\0').toString();
    var exception =
        assertThrows(
            PostException.class,
            () ->
                new Post(
                    field.equals("title") ? invalid : "제목",
                    field.equals("content") ? invalid : "본문",
                    Category.GENERAL,
                    member("작성자")));
    assertEquals("NUL_CHARACTER_NOT_ALLOWED", exception.getErrorCode().getCode());
  }

  @ParameterizedTest
  @CsvSource({"title,0", "title,1", "title,3", "content,0", "content,1", "content,3"})
  void rejectedNulUpdatePreservesBothFields(String field, int position) {
    var post = new Post("기존 제목", "기존 본문", Category.GENERAL, member("작성자"));
    String invalid = new StringBuilder("텍스트").insert(position, '\0').toString();
    var exception =
        assertThrows(
            PostException.class,
            () ->
                post.update(
                    field.equals("title") ? invalid : "변경 제목",
                    field.equals("content") ? invalid : "변경 본문"));
    assertEquals("NUL_CHARACTER_NOT_ALLOWED", exception.getErrorCode().getCode());
    assertEquals("기존 제목", post.getTitle());
    assertEquals("기존 본문", post.getContent());
  }

  @Test
  void preservesUnicodeWhitespaceAndLiteralEscapeText() {
    String text = " 한글😀\n\t\\u0000 ";
    var post = new Post(text, text, Category.GENERAL, member(text));
    assertEquals(text, post.getTitle());
    assertEquals(text, post.getContent());
    assertEquals(text, post.getAuthorName());
    post.update(text + "수정", text + "수정");
    assertEquals(text + "수정", post.getTitle());
    assertEquals(text + "수정", post.getContent());
  }

  private static PostAuthor member(String name) {
    return new PostAuthor(1L, name);
  }
}
