package org.sopt.post.adapter.in.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PostTitleSearchTest extends MemberDatabaseSupport {
  @Autowired MockMvc mvc;
  @Autowired PostCommandUseCase commands;
  @Autowired JdbcTemplate jdbc;
  @Autowired JsonMapper json;

  @Test
  void searchesOnlyTitlesWithCaseInsensitivePartialMatching() throws Exception {
    create("처음 배우는 Spring BOOT", "본문", Category.GENERAL);
    create("다른 제목", "spring", Category.GENERAL);
    create("스프링 시작", "본문", Category.GENERAL);
    assertTitles(search("spring", null, null), "처음 배우는 Spring BOOT");
    assertTitles(search("스프링", null, null), "스프링 시작");
    assertTitles(search("작성자", null, null));
    assertEquals(0L, jdbc.queryForObject("SELECT SUM(view_count) FROM posts", Long.class));
  }

  @ParameterizedTest
  @ValueSource(strings = {"%", "_", "!", "\\", "'", "|", "100%_!|", "x' OR 1=1 --"})
  void treatsSqlAndCursorSpecialCharactersAsLiteralText(String keyword) throws Exception {
    create("앞" + keyword + "뒤", "본문", Category.GENERAL);
    create("일반 제목", "본문", Category.GENERAL);
    assertTitles(search(keyword, null, null), "앞" + keyword + "뒤");
  }

  @Test
  void combinesTitleCategoryAndCursorBeforeLimiting() throws Exception {
    create("검색1", "본문", Category.GENERAL);
    create("다른 글", "본문", Category.GENERAL);
    create("검색2", "본문", Category.QUESTION);
    create("검색3", "본문", Category.GENERAL);
    create("검색4", "본문", Category.GENERAL);
    var first = search("검색", "GENERAL", null);
    assertTitles(first, "검색4", "검색3");
    assertTrue(first.get("hasNext").booleanValue());
    var last = search(" 검색 ", "GENERAL", first.get("nextCursor").stringValue());
    assertTitles(last, "검색1");
    assertFalse(last.get("hasNext").booleanValue());
    assertTrue(last.get("nextCursor").isNull());
    var allFirst = search("검색", null, null);
    assertTitles(search("검색", null, allFirst.get("nextCursor").stringValue()), "검색2", "검색1");
  }

  @Test
  void noMatchesReturnEmptyItemsAndNoNextCursor() throws Exception {
    create("다른 제목", "본문", Category.GENERAL);
    var result = search("없는 검색어", null, null);
    assertTitles(result);
    assertFalse(result.get("hasNext").booleanValue());
    assertTrue(result.get("nextCursor").isNull());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\t\n　"})
  void blankKeywordBehavesLikeNoSearch(String keyword) throws Exception {
    create("첫 글", "본문", Category.GENERAL);
    create("둘째 글", "본문", Category.GENERAL);
    assertEquals(search(null, null, null), search(keyword, null, null));
  }

  @Test
  void searchCursorCannotBeReusedAfterChangingOrRemovingKeyword() throws Exception {
    for (int i = 0; i < 3; i++) create("Spring 제목" + i, "본문", Category.GENERAL);
    String cursor = search("Spring", null, null).get("nextCursor").stringValue();
    mvc.perform(get("/posts").param("cursor", cursor).param("keyword", "다른 검색"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
    mvc.perform(get("/posts").param("cursor", cursor))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
    String noSearchCursor = search(null, null, null).get("nextCursor").stringValue();
    mvc.perform(get("/posts").param("cursor", noSearchCursor).param("keyword", "Spring"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
  }

  @Test
  void validatesKeywordLengthAndNulAndAcceptsMaximumUtf16Length() throws Exception {
    for (String invalid : new String[] {"가".repeat(201), "앞\0뒤"}) {
      mvc.perform(get("/posts").param("keyword", invalid))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("INVALID_SEARCH_KEYWORD"));
    }
    String keyword = "😀".repeat(100);
    create(keyword, "본문", Category.GENERAL);
    assertTitles(search(keyword, null, null), keyword);
    mvc.perform(get("/posts").param("keyword", "😀".repeat(101)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_SEARCH_KEYWORD"));
  }

  @Test
  void cursorSurvivesDeletionOfSearchBoundaryAndIgnoresNewerMatches() throws Exception {
    for (int i = 0; i < 4; i++) create("검색" + i, "본문", Category.GENERAL);
    var first = search("검색", null, null);
    assertTitles(first, "검색3", "검색2");
    jdbc.update("DELETE FROM posts WHERE title = '검색2'");
    commands.createPost("검색 최신 글", "본문", Category.GENERAL, 1L);
    assertTitles(search("검색", null, first.get("nextCursor").stringValue()), "검색1", "검색0");
  }

  private void create(String title, String content, Category category) {
    commands.createPost(title, content, category, 1L);
    jdbc.update(
        "UPDATE posts SET created_at = ? WHERE title = ?",
        LocalDateTime.of(2026, 1, 1, 0, 0, 0, 123456000),
        title);
  }

  private JsonNode search(String keyword, String category, String cursor) throws Exception {
    var request = get("/posts").param("size", "2");
    if (keyword != null) request.param("keyword", keyword);
    if (category != null) request.param("category", category);
    if (cursor != null) request.param("cursor", cursor);
    var response = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse();
    return json.readTree(response.getContentAsString()).get("data");
  }

  private void assertTitles(JsonNode data, String... titles) {
    assertEquals(titles.length, data.get("items").size());
    for (int i = 0; i < titles.length; i++)
      assertEquals(titles[i], data.get("items").get(i).get("title").stringValue());
  }
}
