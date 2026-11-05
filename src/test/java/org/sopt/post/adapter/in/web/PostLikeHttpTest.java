package org.sopt.post.adapter.in.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sopt.post.application.facade.PostLikeFacade;
import org.sopt.post.application.port.in.command.PostCommandUseCase;
import org.sopt.post.domain.Category;
import org.sopt.support.MemberDatabaseSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PostLikeHttpTest extends MemberDatabaseSupport {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired PostCommandUseCase posts;
  @Autowired PostLikeFacade likes;
  long postId;

  @BeforeEach
  void createPost() {
    posts.createPost("좋아요 대상", "본문", Category.GENERAL, 1L);
    postId = jdbc.queryForObject("SELECT id FROM posts", Long.class);
  }

  @Test
  void repeatedPutAndDeleteAreIdempotentAndReturnStateAndCount() throws Exception {
    for (int i = 0; i < 2; i++) {
      mvc.perform(put("/posts/{postId}/likes/{memberId}", postId, 1L))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.liked").value(true))
          .andExpect(jsonPath("$.data.likeCount").value(1));
    }
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM post_likes", Integer.class));
    for (int i = 0; i < 2; i++) {
      mvc.perform(delete("/posts/{postId}/likes/{memberId}", postId, 1L))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.liked").value(false))
          .andExpect(jsonPath("$.data.likeCount").value(0));
    }
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM post_likes", Integer.class));
    // 취소 후 다시 좋아요할 수 있습니다.
    assertEquals(1, likes.like(postId, 1L).likeCount());
  }

  @Test
  void modelsManyToManyAndCancelOnlyAffectsTheRequestingMember() {
    long another = memberId("다른 회원");
    posts.createPost("두 번째 글", "본문", Category.GENERAL, another);
    long second = jdbc.queryForObject("SELECT id FROM posts WHERE title = '두 번째 글'", Long.class);
    likes.like(postId, 1L);
    likes.like(postId, another);
    likes.like(second, 1L);
    assertEquals(1, likes.unlike(postId, 1L).likeCount());
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM post_likes WHERE post_id = ? AND member_id = ?",
            Integer.class,
            postId,
            another));
    assertEquals(
        1, jdbc.queryForObject("SELECT like_count FROM posts WHERE id = ?", Long.class, second));
  }

  @Test
  void displaysCountInDetailHeadAndFilteredListingWithoutChangingEditMetadata() throws Exception {
    var original = jdbc.queryForMap("SELECT updated_at, version FROM posts WHERE id = ?", postId);
    likes.like(postId, 1L);
    mvc.perform(get("/posts/{id}", postId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.likeCount").value(1));
    mvc.perform(head("/posts/{id}", postId)).andExpect(status().isOk());
    mvc.perform(get("/posts").param("authorId", "1").param("keyword", "좋아요"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].likeCount").value(1));
    assertEquals(
        original, jdbc.queryForMap("SELECT updated_at, version FROM posts WHERE id = ?", postId));
    assertEquals(
        1, jdbc.queryForObject("SELECT view_count FROM posts WHERE id = ?", Long.class, postId));
    posts.updatePost(postId, "수정", "수정 내용", 0);
    assertEquals(
        1, jdbc.queryForObject("SELECT like_count FROM posts WHERE id = ?", Long.class, postId));
  }

  @Test
  void deletedMemberCannotChangeLikesAndExistingLikesRemain() throws Exception {
    likes.like(postId, 1L);
    memberCommands.deleteMember(1L);
    mvc.perform(put("/posts/{postId}/likes/{memberId}", postId, 1L))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    mvc.perform(delete("/posts/{postId}/likes/{memberId}", postId, 1L))
        .andExpect(status().isNotFound());
    mvc.perform(get("/posts/{id}", postId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.likeCount").value(1));
  }

  @Test
  void unknownMemberAndPostDoNotCreateOrphanLikes() throws Exception {
    mvc.perform(put("/posts/{postId}/likes/{memberId}", postId, Long.MAX_VALUE))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    mvc.perform(put("/posts/{postId}/likes/{memberId}", Long.MAX_VALUE, 1L))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    mvc.perform(delete("/posts/{postId}/likes/{memberId}", Long.MAX_VALUE, 1L))
        .andExpect(status().isNotFound());
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM post_likes", Integer.class));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1"})
  void rejectsNonPositiveIds(String id) throws Exception {
    mvc.perform(put("/posts/{postId}/likes/{memberId}", postId, id))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_LIKE_ID"));
    mvc.perform(delete("/posts/{postId}/likes/{memberId}", id, 1L))
        .andExpect(status().isBadRequest());
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc", "1.5", "9223372036854775808"})
  void rejectsMalformedIds(String id) throws Exception {
    mvc.perform(put("/posts/{postId}/likes/{memberId}", postId, id))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  void deletingPostCascadesLikesWithoutDeletingMembers() {
    likes.like(postId, 1L);
    posts.deletePost(postId);
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM post_likes", Integer.class));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM members", Integer.class));
  }

  @Test
  void documentsLikeOperationsAndCount() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.paths['/posts/{postId}/likes/{memberId}'].put.responses['200']").exists())
        .andExpect(
            jsonPath("$.paths['/posts/{postId}/likes/{memberId}'].delete.responses['503'].$ref")
                .value("#/components/responses/PostLikeBusy"))
        .andExpect(
            jsonPath("$.components.schemas.PostResponse.properties.likeCount.type")
                .value("integer"));
  }
}
