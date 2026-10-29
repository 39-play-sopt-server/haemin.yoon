package org.sopt.post.adapter.in.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sopt.post.application.port.in.command.PostCommandUseCase;
import org.sopt.post.domain.Category;
import org.sopt.support.MemberDatabaseSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PostDynamicSearchTest extends MemberDatabaseSupport {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired PostCommandUseCase commands;
  @Autowired JsonMapper json;

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7})
  void combinesEverySubsetOfTitleCategoryAndAuthor(int mask) throws Exception {
    long another = memberCommands.createMember("작성자").id();
    create("Spring A 일반", Category.GENERAL, 1L);
    create("다른 A", Category.QUESTION, 1L);
    create("Spring A 질문", Category.QUESTION, 1L);
    create("Spring B 일반", Category.GENERAL, another);
    create("다른 B", Category.GENERAL, another);
    create("Spring B 질문", Category.QUESTION, another);
    var request = get("/posts").param("size", "100");
    if ((mask & 1) != 0) request.param("keyword", "spring");
    if ((mask & 2) != 0) request.param("category", "GENERAL");
    if ((mask & 4) != 0) request.param("authorId", "1");
    var data =
        json.readTree(
                mvc.perform(request)
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("data");
    var expected =
        List.of(
                List.of("Spring B 질문", "다른 B", "Spring B 일반", "Spring A 질문", "다른 A", "Spring A 일반"),
                List.of("Spring B 질문", "Spring B 일반", "Spring A 질문", "Spring A 일반"),
                List.of("다른 B", "Spring B 일반", "Spring A 일반"),
                List.of("Spring B 일반", "Spring A 일반"),
                List.of("Spring A 질문", "다른 A", "Spring A 일반"),
                List.of("Spring A 질문", "Spring A 일반"),
                List.of("Spring A 일반"),
                List.of("Spring A 일반"))
            .get(mask);
    assertEquals(expected.size(), data.get("items").size());
    for (int i = 0; i < expected.size(); i++)
      assertEquals(expected.get(i), data.get("items").get(i).get("title").stringValue());
    assertFalse(data.get("hasNext").booleanValue());
    assertTrue(data.get("nextCursor").isNull());
    assertEquals(0L, jdbc.queryForObject("SELECT SUM(view_count) FROM posts", Long.class));
  }

  @Test
  void scrollsCombinedSearchAndRejectsCursorReuseForAnotherAuthor() throws Exception {
    long another = memberCommands.createMember("다른 작성자").id();
    for (int i = 0; i < 3; i++) {
      create("검색 A" + i, Category.GENERAL, 1L);
      create("검색 B" + i, Category.GENERAL, another);
    }
    String cursor = null;
    for (int i = 2; i >= 0; i--) {
      var request =
          get("/posts")
              .param("size", "1")
              .param("authorId", "1")
              .param("category", "GENERAL")
              .param("keyword", "검색");
      if (cursor != null) request.param("cursor", cursor);
      var data =
          json.readTree(
                  mvc.perform(request)
                      .andExpect(status().isOk())
                      .andReturn()
                      .getResponse()
                      .getContentAsString())
              .get("data");
      assertEquals(1, data.get("items").size());
      assertEquals("검색 A" + i, data.get("items").get(0).get("title").stringValue());
      if (i > 0) {
        cursor = data.get("nextCursor").stringValue();
        mvc.perform(
                get("/posts")
                    .param("cursor", cursor)
                    .param("authorId", Long.toString(another))
                    .param("category", "GENERAL")
                    .param("keyword", "검색"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        mvc.perform(
                get("/posts")
                    .param("cursor", cursor)
                    .param("category", "GENERAL")
                    .param("keyword", "검색"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
      } else {
        assertFalse(data.get("hasNext").booleanValue());
        assertTrue(data.get("nextCursor").isNull());
      }
    }
  }

  @Test
  void deletedAuthorsRemainSearchableAndUnknownAuthorsReturnEmptyItems() throws Exception {
    create("남겨진 게시글", Category.GENERAL, 1L);
    memberCommands.deleteMember(1L);
    mvc.perform(get("/posts").param("authorId", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].author").value("작성자"));
    mvc.perform(get("/posts").param("authorId", Long.toString(Long.MAX_VALUE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items").isEmpty())
        .andExpect(jsonPath("$.data.hasNext").value(false));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1"})
  void rejectsNonPositiveAuthorId(String id) throws Exception {
    mvc.perform(get("/posts").param("authorId", id))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_AUTHOR_ID"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc", "1.5", "9223372036854775808"})
  void rejectsInvalidAuthorIdFormat(String id) throws Exception {
    mvc.perform(get("/posts").param("authorId", id))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  private void create(String title, Category category, long memberId) {
    commands.createPost(title, "본문", category, memberId);
    jdbc.update(
        "UPDATE posts SET created_at = TIMESTAMP '2026-01-01 00:00:00.123456' WHERE title = ?",
        title);
  }
}
