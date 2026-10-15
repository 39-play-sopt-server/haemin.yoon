package org.sopt.post.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
class PostVersionHttpTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired PostCommandUseCase commands;
  long id;

  @BeforeEach
  void createPost() {
    jdbc.update("DELETE FROM posts");
    commands.createPost("기존 제목", "기존 본문", Category.GENERAL, "작성자");
    id = jdbc.queryForObject("SELECT id FROM posts", Long.class);
  }

  @Test
  void detailAndListExposeVersionWithoutViewIncrementsChangingIt() throws Exception {
    mvc.perform(get("/posts")).andExpect(jsonPath("$.data[0].version").value(0));
    for (int count = 1; count <= 2; count++) {
      mvc.perform(get("/posts/{id}", id))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.version").value(0))
          .andExpect(jsonPath("$.data.viewCount").value(count));
    }
    mvc.perform(
            put("/posts/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"수정 제목\",\"content\":\"수정 본문\",\"version\":0}"))
        .andExpect(status().isOk());
    assertEquals(1L, storedVersion());
    assertEquals(
        2L, jdbc.queryForObject("SELECT view_count FROM posts WHERE id = ?", Long.class, id));
  }

  @Test
  void staleEditorCannotOverwriteSavedEditAndCanRetryWithFreshVersion() throws Exception {
    mvc.perform(
            put("/posts/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"첫 수정\",\"content\":\"첫 본문\",\"version\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isEmpty());
    mvc.perform(
            put("/posts/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"오래된 수정\",\"content\":\"오래된 본문\",\"version\":0}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));
    assertEquals(
        "첫 수정", jdbc.queryForObject("SELECT title FROM posts WHERE id = ?", String.class, id));
    assertEquals(
        "첫 본문", jdbc.queryForObject("SELECT content FROM posts WHERE id = ?", String.class, id));
    mvc.perform(get("/posts/{id}", id)).andExpect(jsonPath("$.data.version").value(1));
    mvc.perform(
            put("/posts/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"재검토한 수정\",\"content\":\"재검토한 본문\",\"version\":1}"))
        .andExpect(status().isOk());
    assertEquals(2L, storedVersion());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"title\":\"수정\",\"content\":\"본문\"}",
        "{\"title\":\"수정\",\"content\":\"본문\",\"version\":null}",
        "{\"title\":\"수정\",\"content\":\"본문\",\"version\":-1}"
      })
  void missingOrNegativeVersionIsRejectedWithoutChanges(String body) throws Exception {
    mvc.perform(put("/posts/{id}", id).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.violations[0].field").value("version"));
    assertEquals(
        "기존 제목", jdbc.queryForObject("SELECT title FROM posts WHERE id = ?", String.class, id));
  }

  @Test
  void futureVersionCannotBeAssignedByClient() throws Exception {
    mvc.perform(
            put("/posts/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"수정\",\"content\":\"본문\",\"version\":99}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));
    assertEquals(0L, storedVersion());
  }

  @ParameterizedTest
  @ValueSource(strings = {"-0.5", "0.9", "1.9"})
  void fractionalVersionIsNotSilentlyTruncated(String version) throws Exception {
    mvc.perform(
            put("/posts/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"수정\",\"content\":\"본문\",\"version\":" + version + "}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    assertEquals(
        "기존 제목", jdbc.queryForObject("SELECT title FROM posts WHERE id = ?", String.class, id));
  }

  @Test
  void savingIdenticalContentDoesNotCreateANewRevision() throws Exception {
    mvc.perform(
            put("/posts/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"기존 제목\",\"content\":\"기존 본문\",\"version\":0}"))
        .andExpect(status().isOk());
    assertEquals(0L, storedVersion());
  }

  @Test
  void missingPostStillReturns404ForValidVersionedRequest() throws Exception {
    mvc.perform(
            put("/posts/{id}", Long.MAX_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"수정\",\"content\":\"본문\",\"version\":0}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
  }

  private long storedVersion() {
    return jdbc.queryForObject("SELECT version FROM posts WHERE id = ?", Long.class, id);
  }
}
