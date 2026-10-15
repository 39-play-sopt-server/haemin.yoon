package org.sopt.post.adapter.in.web;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sopt.post.application.port.in.query.PostListQuery;
import org.sopt.post.application.port.out.LoadPostPort;
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
class PostHttpApiTest {
  @Autowired MockMvc mvc;
  @Autowired LoadPostPort repository;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void clearPosts() {
    jdbc.update("DELETE FROM posts");
  }

  @Test
  void performsCrudThroughHttpWithSharedStorage() throws Exception {
    mvc.perform(
            post("/posts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
        {"title":"제목","content":"본문","category":"QUESTION","author":"작성자"}
        """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"));
    long id = repository.findAll(new PostListQuery(0, 100, null)).getFirst().getId();
    mvc.perform(get("/posts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id").value(id))
        .andExpect(jsonPath("$.data[0].category").value("QUESTION"));
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
            {"title":"새 제목","content":"새 본문"}
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
    mvc.perform(get("/posts")).andExpect(jsonPath("$.data").isEmpty());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "null",
        "{",
        "{}",
        "{\"title\":\"제목\",\"content\":\"본문\",\"category\":\"UNKNOWN\",\"author\":\"작성자\"}"
      })
  void rejectsInvalidBodiesWithCommonResponse(String body) throws Exception {
    mvc.perform(post("/posts").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.data").isEmpty());
    mvc.perform(get("/posts")).andExpect(jsonPath("$.data").isEmpty());
  }

  @Test
  void rejectsBlankTitleWithFieldViolation() throws Exception {
    mvc.perform(
            post("/posts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
        {"title":" ","content":"본문","category":"GENERAL","author":"작성자"}
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
        "{\"title\":null,\"content\":null,\"category\":null,\"author\":null}",
        "{\"title\":\" \",\"content\":\"\\t\\n\",\"category\":null,\"author\":\"　\"}"
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
                .value(contains("author", "category", "content", "title")))
        .andExpect(
            jsonPath("$.violations[*].code")
                .value(contains("REQUIRED", "REQUIRED", "REQUIRED", "REQUIRED")))
        .andExpect(jsonPath("$.violations[*].rejectedValue").doesNotExist());
    mvc.perform(get("/posts")).andExpect(jsonPath("$.data").isEmpty());
  }

  @Test
  void invalidUpdateReportsBothFieldsAndPreservesStoredPost() throws Exception {
    mvc.perform(
            post("/posts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
        {"title":"기존 제목","content":"기존 본문","category":"GENERAL","author":"작성자"}
        """))
        .andExpect(status().isCreated());
    long id = repository.findAll(new PostListQuery(0, 100, null)).getFirst().getId();
    mvc.perform(
            put("/posts/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\" \",\"content\":null}"))
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
                .content("{\"title\":\"제목\",\"content\":\"본문\"}"))
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
}
