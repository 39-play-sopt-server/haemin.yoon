package org.sopt.post.adapter.in.web;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sopt.post.application.port.in.command.PostCommandUseCase;
import org.sopt.post.application.port.out.LoadPostPort;
import org.sopt.post.application.port.out.dto.PostListCriteria;
import org.sopt.post.domain.Category;
import org.sopt.support.MemberDatabaseSupport;
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
class PostHttpApiTest extends MemberDatabaseSupport {
  @Autowired MockMvc mvc;
  @Autowired LoadPostPort repository;
  @Autowired PostCommandUseCase commands;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void clearPosts() {
    jdbc.update("DELETE FROM posts");
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "1", "2", "-1", "3", "1.0", "1e0", "\"0\"", "\"1\"", "\"2\""})
  void rejectsNumericCategoryWithoutSaving(String category) throws Exception {
    mvc.perform(
            post("/posts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title":"제목","content":"본문","category":%s,"memberId":1}
                    """
                        .formatted(category)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.data").value(nullValue()))
        .andExpect(jsonPath("$.violations", empty()));
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM posts", Integer.class));
  }

  @ParameterizedTest
  @ValueSource(strings = {"GENERAL", "QUESTION", "INFORMATION"})
  void acceptsNamedCategoryAndPreservesItsPublicValue(String category) throws Exception {
    mvc.perform(
            post("/posts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title":"제목","content":"본문","category":"%s","memberId":1}
                    """
                        .formatted(category)))
        .andExpect(status().isCreated());
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM posts", Integer.class));
    assertEquals(category, jdbc.queryForObject("SELECT category FROM posts", String.class));
    mvc.perform(get("/posts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].category").value(category));
  }

  @Test
  void performsCrudThroughHttpWithSharedStorage() throws Exception {
    mvc.perform(
            post("/posts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
        {"title":"제목","content":"본문","category":"QUESTION","memberId":1}
        """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"));
    long id = repository.findAll(criteria()).getFirst().getId();
    mvc.perform(get("/posts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items.length()").value(1))
        .andExpect(jsonPath("$.data.items[0].id").value(id))
        .andExpect(jsonPath("$.data.items[0].category").value("QUESTION"));
    mvc.perform(get("/posts/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.title").value("제목"))
        .andExpect(jsonPath("$.data.author").value("작성자"))
        .andExpect(jsonPath("$.data.createdAt").isString());
    mvc.perform(
            put("/posts/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
            {"title":"새 제목","content":"새 본문","version":0}
            """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("게시글이 수정되었습니다."));
    mvc.perform(get("/posts/{id}", id))
        .andExpect(jsonPath("$.data.title").value("새 제목"))
        .andExpect(jsonPath("$.data.category").value("QUESTION"));
    mvc.perform(delete("/posts/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("게시글이 삭제되었습니다."));
    mvc.perform(get("/posts/{id}", id))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    mvc.perform(get("/posts")).andExpect(jsonPath("$.data.items").isEmpty());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "null",
        "{",
        "{}",
        "{\"title\":\"제목\",\"content\":\"본문\",\"category\":\"UNKNOWN\",\"memberId\":1}"
      })
  void rejectsInvalidBodiesWithCommonResponse(String body) throws Exception {
    mvc.perform(post("/posts").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.data").isEmpty());
    mvc.perform(get("/posts")).andExpect(jsonPath("$.data.items").isEmpty());
  }

  @Test
  void rejectsBlankTitleWithFieldViolation() throws Exception {
    mvc.perform(
            post("/posts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
        {"title":" ","content":"본문","category":"GENERAL","memberId":1}
        """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.violations.length()").value(1))
        .andExpect(jsonPath("$.violations[0].field").value("title"))
        .andExpect(jsonPath("$.violations[0].code").value("REQUIRED"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"title\":null,\"content\":null,\"category\":null,\"memberId\":null}",
        "{\"title\":\" \",\"content\":\"\\t\\n\",\"category\":null,\"memberId\":null}"
      })
  void reportsAllInvalidCreateFieldsWithoutWritingPost(String body) throws Exception {
    mvc.perform(post("/posts").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.data").value(nullValue()))
        .andExpect(jsonPath("$.violations.length()").value(4))
        .andExpect(
            jsonPath("$.violations[*].field")
                .value(contains("category", "content", "memberId", "title")))
        .andExpect(
            jsonPath("$.violations[*].code")
                .value(contains("REQUIRED", "REQUIRED", "REQUIRED", "REQUIRED")))
        .andExpect(jsonPath("$.violations[*].rejectedValue").doesNotExist());
    mvc.perform(get("/posts")).andExpect(jsonPath("$.data.items").isEmpty());
  }

  @Test
  void invalidUpdateReportsBothFieldsAndPreservesStoredPost() throws Exception {
    mvc.perform(
            post("/posts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
        {"title":"기존 제목","content":"기존 본문","category":"GENERAL","memberId":1}
        """))
        .andExpect(status().isCreated());
    long id = repository.findAll(criteria()).getFirst().getId();
    mvc.perform(
            put("/posts/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\" \",\"content\":null,\"version\":0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.violations[*].field").value(contains("content", "title")));
    mvc.perform(get("/posts/{id}", id))
        .andExpect(jsonPath("$.data.title").value("기존 제목"))
        .andExpect(jsonPath("$.data.content").value("기존 본문"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc", "9223372036854775808"})
  void rejectsInvalidPathIds(String id) throws Exception {
    mvc.perform(get("/posts/{id}", id))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  void missingUpdateAndDeleteReturn404() throws Exception {
    mvc.perform(
            put("/posts/99999")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"제목\",\"content\":\"본문\",\"version\":0}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    mvc.perform(delete("/posts/99999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
  }

  @Test
  void unknownRouteUsesCommon404Response() throws Exception {
    mvc.perform(get("/unknown-api"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
        .andExpect(jsonPath("$.data").value(nullValue()))
        .andExpect(jsonPath("$.stackTrace").doesNotExist());
  }

  @Test
  void unsupportedMethodPreservesAllowHeader() throws Exception {
    mvc.perform(patch("/posts"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(header().string("Allow", containsString("GET")))
        .andExpect(header().string("Allow", containsString("POST")))
        .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
  }

  @Test
  void unsupportedContentTypeUsesCommon415Response() throws Exception {
    mvc.perform(post("/posts").contentType(MediaType.TEXT_PLAIN).content("secret input"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
        .andExpect(content().string(not(containsString("secret input"))));
  }

  @Test
  void unsupportedAcceptUsesJson406Response() throws Exception {
    mvc.perform(get("/posts").accept(MediaType.APPLICATION_XML))
        .andExpect(status().isNotAcceptable())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("NOT_ACCEPTABLE"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"application/xml", "text/html"})
  void unsupportedAcceptDoesNotCreatePost(String accept) throws Exception {
    mvc.perform(
            post("/posts")
                .accept(accept)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title":"제목","content":"본문","category":"GENERAL","memberId":1}
                    """))
        .andExpect(status().isNotAcceptable())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("NOT_ACCEPTABLE"));

    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM posts", Integer.class));
  }

  @ParameterizedTest
  @ValueSource(strings = {"application/xml", "text/html"})
  void unsupportedAcceptDoesNotUpdatePost(String accept) throws Exception {
    long id = createStoredPost();

    mvc.perform(
            put("/posts/{id}", id)
                .accept(accept)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"수정 제목\",\"content\":\"수정 본문\",\"version\":0}"))
        .andExpect(status().isNotAcceptable())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("NOT_ACCEPTABLE"));

    assertEquals(
        "기존 제목", jdbc.queryForObject("SELECT title FROM posts WHERE id = ?", String.class, id));
    assertEquals(
        "기존 본문", jdbc.queryForObject("SELECT content FROM posts WHERE id = ?", String.class, id));
  }

  @ParameterizedTest
  @ValueSource(strings = {"application/xml", "text/html"})
  void unsupportedAcceptDoesNotDeletePost(String accept) throws Exception {
    long id = createStoredPost();

    mvc.perform(delete("/posts/{id}", id).accept(accept))
        .andExpect(status().isNotAcceptable())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("NOT_ACCEPTABLE"));

    assertEquals(
        1, jdbc.queryForObject("SELECT COUNT(*) FROM posts WHERE id = ?", Integer.class, id));
  }

  @ParameterizedTest
  @ValueSource(strings = {"application/xml", "text/html"})
  void unsupportedAcceptDoesNotIncrementViewCount(String accept) throws Exception {
    long id = createStoredPost();

    mvc.perform(get("/posts/{id}", id).accept(accept))
        .andExpect(status().isNotAcceptable())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("NOT_ACCEPTABLE"));

    assertEquals(
        0L, jdbc.queryForObject("SELECT view_count FROM posts WHERE id = ?", Long.class, id));
  }

  @ParameterizedTest
  @ValueSource(strings = {"application/json", "application/*", "*/*"})
  void compatibleAcceptReturnsJsonDetailAndIncrementsViewCount(String accept) throws Exception {
    long id = createStoredPost();

    mvc.perform(get("/posts/{id}", id).accept(accept))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.data.viewCount").value(1));

    assertEquals(
        1L, jdbc.queryForObject("SELECT view_count FROM posts WHERE id = ?", Long.class, id));
  }

  private long createStoredPost() {
    commands.createPost("기존 제목", "기존 본문", Category.GENERAL, memberId("작성자"));
    return jdbc.queryForObject("SELECT id FROM posts", Long.class);
  }

  @Test
  void servletErrorDispatchUsesCommonResponseEvenForHtmlAccept() throws Exception {
    mvc.perform(
            get("/error")
                .accept(MediaType.TEXT_HTML)
                .with(
                    request -> {
                      request.setDispatcherType(DispatcherType.ERROR);
                      return request;
                    })
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 403)
                .requestAttr(RequestDispatcher.ERROR_MESSAGE, "internal secret"))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("FORBIDDEN"))
        .andExpect(content().string(not(containsString("internal secret"))));
  }

  private static PostListCriteria criteria() {
    return new PostListCriteria(null, null, 100, null, null, null);
  }
}
