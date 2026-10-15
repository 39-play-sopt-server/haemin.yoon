package org.sopt.post.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.sopt.post.application.port.in.command.PostCommandUseCase;
import org.sopt.post.domain.Category;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PostLengthHttpTest {
  @Autowired MockMvc mvc;
  @Autowired PostCommandUseCase commands;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void clearPosts() {
    jdbc.update("DELETE FROM posts");
  }

  @Test
  void supplementaryCharactersUseSameLimitInHttpValidation() throws Exception {
    mvc.perform(
            post("/posts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
        {"title":"%s","content":"본문","category":"GENERAL","author":"작성자"}
        """
                        .formatted("😀".repeat(100))))
        .andExpect(status().isCreated());
    long id = jdbc.queryForObject("SELECT id FROM posts", Long.class);
    mvc.perform(
            put("/posts/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
        {"title":"%s","content":"변경 본문"}
        """
                        .formatted("😀".repeat(101))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.violations[0].code").value("INVALID_LENGTH"));
    assertEquals(
        "😀".repeat(100),
        jdbc.queryForObject("SELECT title FROM posts WHERE id = ?", String.class, id));
    assertEquals(
        "본문", jdbc.queryForObject("SELECT content FROM posts WHERE id = ?", String.class, id));
  }

  @ParameterizedTest
  @CsvSource({"title,201", "content,10001", "author,51"})
  void oversizedCreateReturnsFieldViolationWithoutSaving(String field, int length)
      throws Exception {
    String title = field.equals("title") ? "가".repeat(length) : "제목";
    String content = field.equals("content") ? "가".repeat(length) : "본문";
    String author = field.equals("author") ? "가".repeat(length) : "작성자";
    mvc.perform(
            post("/posts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
        {"title":"%s","content":"%s","category":"GENERAL","author":"%s"}
        """
                        .formatted(title, content, author)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.violations.length()").value(1))
        .andExpect(jsonPath("$.violations[0].field").value(field))
        .andExpect(jsonPath("$.violations[0].code").value("INVALID_LENGTH"));
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM posts", Integer.class));
  }

  @ParameterizedTest
  @CsvSource({"title,201", "content,10001"})
  void oversizedUpdatePreservesStoredPost(String field, int length) throws Exception {
    commands.createPost("기존 제목", "기존 본문", Category.GENERAL, "작성자");
    long id = jdbc.queryForObject("SELECT id FROM posts", Long.class);
    String title = field.equals("title") ? "가".repeat(length) : "변경 제목";
    String content = field.equals("content") ? "가".repeat(length) : "변경 본문";
    mvc.perform(
            put("/posts/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
        {"title":"%s","content":"%s"}
        """
                        .formatted(title, content)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.violations[0].field").value(field))
        .andExpect(jsonPath("$.violations[0].code").value("INVALID_LENGTH"));
    assertEquals(
        "기존 제목", jdbc.queryForObject("SELECT title FROM posts WHERE id = ?", String.class, id));
    assertEquals(
        "기존 본문", jdbc.queryForObject("SELECT content FROM posts WHERE id = ?", String.class, id));
  }

  @Test
  void maximumLengthsAreAcceptedAndListingKeepsFullContent() throws Exception {
    String title = "제".repeat(200);
    String content = "본".repeat(10000);
    String author = "작".repeat(50);
    mvc.perform(
            post("/posts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
        {"title":"%s","content":"%s","category":"GENERAL","author":"%s"}
        """
                        .formatted(title, content, author)))
        .andExpect(status().isCreated());
    mvc.perform(get("/posts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].title").value(title))
        .andExpect(jsonPath("$.data[0].content").value(content))
        .andExpect(jsonPath("$.data[0].author").value(author));
    long id = jdbc.queryForObject("SELECT id FROM posts", Long.class);
    mvc.perform(
            put("/posts/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
        {"title":"%s","content":"%s"}
        """
                        .formatted("수".repeat(200), "정".repeat(10000))))
        .andExpect(status().isOk());
    assertEquals(
        "수".repeat(200),
        jdbc.queryForObject("SELECT title FROM posts WHERE id = ?", String.class, id));
    assertEquals(
        "정".repeat(10000),
        jdbc.queryForObject("SELECT content FROM posts WHERE id = ?", String.class, id));
  }
}
