package org.sopt.post.adapter.in.web;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
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
class PostListingTest extends MemberDatabaseSupport {
  @Autowired MockMvc mvc;
  @Autowired PostCommandUseCase commands;
  @Autowired JdbcTemplate jdbc;
  @Autowired JsonMapper json;

  @Test
  void defaultsToFirstTwentyPostsInLatestOrder() throws Exception {
    createPosts(25);
    mvc.perform(get("/posts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items.length()").value(20))
        .andExpect(jsonPath("$.data.items[0].title").value("post-24"))
        .andExpect(jsonPath("$.data.items[19].title").value("post-5"))
        .andExpect(jsonPath("$.data.hasNext").value(true))
        .andExpect(jsonPath("$.data.nextCursor").isString());
  }

  @Test
  void followsCursorsWithoutOverlapAndStopsAtPartialLastBatch() throws Exception {
    createPosts(7);
    var first = list(null, 3, null);
    assertTitles(first, "post-6", "post-5", "post-4");
    assertTrue(first.get("hasNext").booleanValue());
    var second = list(first.get("nextCursor").stringValue(), 3, null);
    assertTitles(second, "post-3", "post-2", "post-1");
    assertTrue(second.get("hasNext").booleanValue());
    var last = list(second.get("nextCursor").stringValue(), 3, null);
    assertTitles(last, "post-0");
    assertFalse(last.get("hasNext").booleanValue());
    assertTrue(last.get("nextCursor").isNull());
    assertEquals(0L, jdbc.queryForObject("SELECT SUM(view_count) FROM posts", Long.class));
  }

  @Test
  void maximumSizeAndExactFullLastBatchDoNotExposeLookaheadRow() throws Exception {
    createPosts(101);
    var first = list(null, 100, null);
    assertEquals(100, first.get("items").size());
    var last = list(first.get("nextCursor").stringValue(), 1, null);
    assertTitles(last, "post-0");
    assertFalse(last.get("hasNext").booleanValue());
    assertTrue(last.get("nextCursor").isNull());
  }

  @ParameterizedTest
  @EnumSource(Category.class)
  void filtersBeforeApplyingCursorAndLimit(Category category) throws Exception {
    createPosts(9);
    int index = category.ordinal();
    var first = list(null, 2, category.name());
    assertTitles(first, "post-" + (index + 6), "post-" + (index + 3));
    var last = list(first.get("nextCursor").stringValue(), 2, category.name());
    assertTitles(last, "post-" + index);
    assertFalse(last.get("hasNext").booleanValue());
    for (var post : first.get("items"))
      assertEquals(category.name(), post.get("category").stringValue());
  }

  @Test
  void emptyStorageAndCategoryWithoutMatchesReturnEmptyItemsAndNoCursor() throws Exception {
    var empty = list(null, 20, null);
    assertTitles(empty);
    assertFalse(empty.get("hasNext").booleanValue());
    assertTrue(empty.get("nextCursor").isNull());
    commands.createPost("일반 글", "본문", Category.GENERAL, 1L);
    assertTitles(list(null, 20, "QUESTION"));
  }

  @Test
  void emptyOptionalParametersUseDefaults() throws Exception {
    createPosts(2);
    mvc.perform(get("/posts").param("cursor", "").param("size", "").param("category", ""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[*].title").value(contains("post-1", "post-0")))
        .andExpect(jsonPath("$.data.hasNext").value(false))
        .andExpect(jsonPath("$.data.nextCursor").value(nullValue()));
  }

  @Test
  void insertionAndDeletionIncludingBoundaryDoNotShiftTheNextBatch() throws Exception {
    createPosts(6);
    var first = list(null, 2, null);
    assertTitles(first, "post-5", "post-4");
    String cursor = first.get("nextCursor").stringValue();
    jdbc.update("DELETE FROM posts WHERE title IN ('post-5', 'post-4', 'post-3')");
    commands.createPost("새 글", "본문", Category.GENERAL, 1L);
    var second = list(cursor, 2, null);
    assertTitles(second, "post-2", "post-1");
    assertTitles(list(second.get("nextCursor").stringValue(), 2, null), "post-0");
    assertEquals("새 글", list(null, 2, null).get("items").get(0).get("title").stringValue());
  }

  @Test
  void sortsByCreationTimeBeforeIdAndBreaksTiesAcrossBatches() throws Exception {
    createPosts(4);
    var oldest = LocalDateTime.of(2026, 1, 1, 0, 0);
    jdbc.update("UPDATE posts SET created_at = ? WHERE title = 'post-0'", oldest.plusDays(2));
    jdbc.update(
        "UPDATE posts SET created_at = ? WHERE title IN ('post-2', 'post-3')", oldest.plusDays(1));
    var first = list(null, 2, null);
    assertTitles(first, "post-0", "post-3");
    assertTitles(list(first.get("nextCursor").stringValue(), 2, null), "post-2", "post-1");
  }

  @Test
  void categoryBatchesUseDescendingIdWhenCreationTimesMatch() throws Exception {
    createPosts(7);
    jdbc.update("UPDATE posts SET created_at = TIMESTAMP '2026-01-01 00:00:00'");
    var first = list(null, 2, "GENERAL");
    assertTitles(first, "post-6", "post-3");
    assertTitles(list(first.get("nextCursor").stringValue(), 2, "GENERAL"), "post-0");
  }

  @Test
  void editingAnOlderPostDoesNotMoveItAheadOfNewerPosts() throws Exception {
    createPosts(3);
    long id = jdbc.queryForObject("SELECT id FROM posts WHERE title = 'post-0'", Long.class);
    commands.updatePost(id, "편집한 예전 글", "수정 본문", 0L);
    assertTitles(list(null, 20, null), "post-2", "post-1", "편집한 예전 글");
  }

  @Test
  void aCursorCannotBeReusedForDifferentCategoryFilters() throws Exception {
    createPosts(9);
    String allCursor = list(null, 1, null).get("nextCursor").stringValue();
    String categoryCursor = list(null, 1, "GENERAL").get("nextCursor").stringValue();
    mvc.perform(get("/posts").param("cursor", allCursor).param("category", "GENERAL"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
    mvc.perform(get("/posts").param("cursor", categoryCursor))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
    mvc.perform(get("/posts").param("cursor", categoryCursor).param("category", "QUESTION"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
  }

  @Test
  void aValidBoundaryWithNoRemainingPostsReturnsAnEmptyBatch() throws Exception {
    createPosts(3);
    String cursor = list(null, 1, null).get("nextCursor").stringValue();
    jdbc.update("DELETE FROM posts");
    var empty = list(cursor, 20, null);
    assertTitles(empty);
    assertFalse(empty.get("hasNext").booleanValue());
    assertTrue(empty.get("nextCursor").isNull());
  }

  @ParameterizedTest
  @MethodSource("invalidQueries")
  void rejectsInvalidQueriesWithCommonError(String parameter, String value, String code)
      throws Exception {
    mvc.perform(get("/posts").param(parameter, value))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value(code))
        .andExpect(jsonPath("$.data").isEmpty());
  }

  static Stream<Arguments> invalidQueries() {
    return Stream.of(
        Arguments.of("size", "0", "INVALID_PAGINATION"),
        Arguments.of("size", "-1", "INVALID_PAGINATION"),
        Arguments.of("size", "101", "INVALID_PAGINATION"),
        Arguments.of("size", "abc", "INVALID_REQUEST"),
        Arguments.of("size", "1.5", "INVALID_REQUEST"),
        Arguments.of("size", "2147483648", "INVALID_REQUEST"),
        Arguments.of("cursor", "%%%", "INVALID_CURSOR"),
        Arguments.of("cursor", " ", "INVALID_CURSOR"),
        Arguments.of("cursor", "a".repeat(2049), "INVALID_CURSOR"),
        Arguments.of("category", "UNKNOWN", "INVALID_REQUEST"),
        Arguments.of("category", "general", "INVALID_REQUEST"));
  }

  private JsonNode list(String cursor, int size, String category) throws Exception {
    var request = get("/posts").param("size", Integer.toString(size));
    if (cursor != null) request.param("cursor", cursor);
    if (category != null) request.param("category", category);
    var response = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse();
    return json.readTree(response.getContentAsString()).get("data");
  }

  private void assertTitles(JsonNode data, String... titles) {
    assertEquals(titles.length, data.get("items").size());
    for (int index = 0; index < titles.length; index++) {
      assertEquals(titles[index], data.get("items").get(index).get("title").stringValue());
    }
  }

  private void createPosts(int count) {
    for (int i = 0; i < count; i++) {
      commands.createPost("post-" + i, "본문", Category.values()[i % 3], 1L);
      jdbc.update(
          "UPDATE posts SET created_at = ? WHERE title = ?",
          LocalDateTime.of(2026, 1, 1, 0, 0).plusSeconds(i),
          "post-" + i);
    }
  }
}
