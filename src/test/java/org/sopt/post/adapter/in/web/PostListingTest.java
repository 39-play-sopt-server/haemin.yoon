package org.sopt.post.adapter.in.web;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.sopt.post.application.port.in.command.PostCommandUseCase;
import org.sopt.post.domain.Category;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PostListingTest {
  @Autowired MockMvc mvc;
  @Autowired PostCommandUseCase commands;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void clearPosts() {
    jdbc.update("DELETE FROM posts");
  }

  @Test
  void defaultsToFirstTwentyPostsInIdOrder() throws Exception {
    createPosts(25);
    mvc.perform(get("/posts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(20))
        .andExpect(jsonPath("$.data[0].title").value("post-0"))
        .andExpect(jsonPath("$.data[19].title").value("post-19"));
  }

  @Test
  void returnsRequestedPageAndPartialLastPageWithoutOverlap() throws Exception {
    createPosts(7);
    mvc.perform(get("/posts").param("page", "0").param("size", "3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[*].title").value(contains("post-0", "post-1", "post-2")));
    mvc.perform(get("/posts").param("page", "1").param("size", "3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[*].title").value(contains("post-3", "post-4", "post-5")));
    mvc.perform(get("/posts").param("page", "2").param("size", "3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[*].title").value(contains("post-6")));
    mvc.perform(get("/posts").param("page", "3").param("size", "3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isEmpty());
  }

  @Test
  void supportsMaximumSizeAndSingleItemPages() throws Exception {
    createPosts(101);
    mvc.perform(get("/posts").param("size", "100"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(100));
    mvc.perform(get("/posts").param("page", "100").param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[*].title").value(contains("post-100")));
  }

  @ParameterizedTest
  @EnumSource(Category.class)
  void filtersBeforePaginationForEveryCategory(Category category) throws Exception {
    createPosts(9);
    int index = category.ordinal();
    mvc.perform(get("/posts").param("category", category.name()).param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.data[*].title").value(contains("post-" + index, "post-" + (index + 3))))
        .andExpect(jsonPath("$.data[*].category").value(everyItem(is(category.name()))));
    mvc.perform(
            get("/posts").param("category", category.name()).param("page", "1").param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[*].title").value(contains("post-" + (index + 6))));
    mvc.perform(
            get("/posts").param("category", category.name()).param("page", "2").param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isEmpty());
    org.junit.jupiter.api.Assertions.assertEquals(
        0L, jdbc.queryForObject("SELECT SUM(view_count) FROM posts", Long.class));
  }

  @Test
  void emptyStorageAndCategoryWithoutMatchesReturnEmptyArray() throws Exception {
    mvc.perform(get("/posts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data").isEmpty());
    commands.createPost("일반 글", "본문", Category.GENERAL, "작성자");
    mvc.perform(get("/posts").param("category", "QUESTION"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isEmpty());
  }

  @Test
  void emptyOptionalParametersUseDefaults() throws Exception {
    createPosts(2);
    mvc.perform(get("/posts").param("page", "").param("size", "").param("category", ""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[*].title").value(contains("post-0", "post-1")));
  }

  @Test
  void largestSupportedOffsetReturnsEmptyPage() throws Exception {
    createPosts(1);
    mvc.perform(get("/posts").param("page", "2147483647").param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isEmpty());
  }

  @Test
  void deletionDoesNotTurnPaginationIntoAnIdRange() throws Exception {
    createPosts(7);
    jdbc.update("DELETE FROM posts WHERE title = ?", "post-1");
    mvc.perform(get("/posts").param("page", "1").param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[*].title").value(contains("post-3", "post-4")));
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
        Arguments.of("page", "-1", "INVALID_PAGINATION"),
        Arguments.of("page", "2147483647", "INVALID_PAGINATION"),
        Arguments.of("size", "0", "INVALID_PAGINATION"),
        Arguments.of("size", "-1", "INVALID_PAGINATION"),
        Arguments.of("size", "101", "INVALID_PAGINATION"),
        Arguments.of("page", "abc", "INVALID_REQUEST"),
        Arguments.of("page", "1.5", "INVALID_REQUEST"),
        Arguments.of("page", "2147483648", "INVALID_REQUEST"),
        Arguments.of("size", "abc", "INVALID_REQUEST"),
        Arguments.of("size", "2147483648", "INVALID_REQUEST"),
        Arguments.of("category", "UNKNOWN", "INVALID_REQUEST"),
        Arguments.of("category", "general", "INVALID_REQUEST"));
  }

  private void createPosts(int count) {
    for (int i = 0; i < count; i++) {
      commands.createPost("post-" + i, "본문", Category.values()[i % 3], "작성자");
    }
  }
}
