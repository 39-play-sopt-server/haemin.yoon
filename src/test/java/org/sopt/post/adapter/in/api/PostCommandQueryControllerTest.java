package org.sopt.post.adapter.in.api;

import java.time.LocalDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.sopt.global.response.BaseResponse;
import org.sopt.post.adapter.in.api.dto.PostCategory;
import org.sopt.post.adapter.in.api.dto.request.CreatePostRequest;
import org.sopt.post.adapter.in.api.dto.request.UpdatePostRequest;
import org.sopt.post.config.PostServerConfiguration;

import static org.junit.jupiter.api.Assertions.*;

class PostCommandQueryControllerTest {
  private final PostServerConfiguration.Controllers controllers = PostServerConfiguration.createControllers();
  private final PostCommandController commands = controllers.commandController();
  private final PostQueryController queries = controllers.queryController();

  private CreatePostRequest request(PostCategory category) {
    return new CreatePostRequest("제목", "본문", category, "작성자");
  }

  @ParameterizedTest
  @EnumSource(PostCategory.class)
  void mapsRequestAndResponseFields(PostCategory category) {
    LocalDateTime before = LocalDateTime.now();
    var created = commands.createPost(request(category));
    assertTrue(created.success());
    assertEquals("SUCCESS", created.code());
    assertEquals("게시글이 작성되었습니다.", created.message());
    assertNull(created.data());
    var response = queries.getPost(1);
    assertTrue(response.success());
    assertEquals("SUCCESS", response.code());
    var post = response.data();
    assertEquals(1L, post.id());
    assertEquals("제목", post.title());
    assertEquals("본문", post.content());
    assertEquals(category, post.category());
    assertEquals("작성자", post.author());
    assertFalse(post.createdAt().isBefore(before));
    assertFalse(post.createdAt().isAfter(LocalDateTime.now()));
    assertTrue(queries.hasPosts().data());
  }

  static Stream<Arguments> invalidRequests() {
    return Stream.of(
        Arguments.of(null, "INVALID_REQUEST", "요청 정보가 없습니다."),
        Arguments.of(new CreatePostRequest("", "본문", PostCategory.GENERAL, "작성자"), "INVALID_TITLE", "제목은 비어 있을 수 없습니다."),
        Arguments.of(new CreatePostRequest("제목", "", PostCategory.GENERAL, "작성자"), "INVALID_CONTENT", "본문은 비어 있을 수 없습니다."),
        Arguments.of(new CreatePostRequest("제목", "본문", null, "작성자"), "INVALID_CATEGORY", "카테고리는 필수입니다."),
        Arguments.of(new CreatePostRequest("제목", "본문", PostCategory.GENERAL, ""), "INVALID_AUTHOR", "작성자는 비어 있을 수 없습니다."));
  }

  @ParameterizedTest
  @MethodSource("invalidRequests")
  void creationErrorsBecomeConsistentResponses(CreatePostRequest request, String code, String message) {
    var response = commands.createPost(request);
    assertFalse(response.success());
    assertEquals(code, response.code());
    assertEquals(message, response.message());
    assertNull(response.data());
    assertTrue(queries.getPosts().data().isEmpty());
  }

  @Test
  void missingReadUpdateAndDeleteReturnNotFound() {
    assertFailure(queries.getPost(99), "POST_NOT_FOUND");
    assertFailure(commands.updatePost(99, new UpdatePostRequest("제목", "본문")), "POST_NOT_FOUND");
    assertFailure(commands.deletePost(99), "POST_NOT_FOUND");
    assertFalse(queries.hasPosts().data());
  }

  @Test
  void nullUpdateRequestReturnsInvalidRequestAndPreservesPost() {
    commands.createPost(request(PostCategory.GENERAL));
    assertFailure(commands.updatePost(1, null), "INVALID_REQUEST");
    assertEquals("제목", queries.getPost(1).data().title());
  }

  @Test
  void invalidUpdateReturnsFailureAndPreservesBothFields() {
    commands.createPost(request(PostCategory.GENERAL));
    var response = commands.updatePost(1, new UpdatePostRequest("새 제목", " "));
    assertFailure(response, "INVALID_CONTENT");
    assertEquals("본문은 비어 있을 수 없습니다.", response.message());
    assertEquals("제목", queries.getPost(1).data().title());
    assertEquals("본문", queries.getPost(1).data().content());
  }

  @Test
  void responseSnapshotsRemainUnchangedAfterServerMutations() {
    commands.createPost(request(PostCategory.QUESTION));
    var original = queries.getPost(1).data();
    var list = queries.getPosts().data();
    assertThrows(UnsupportedOperationException.class, list::clear);
    var updated = commands.updatePost(1, new UpdatePostRequest("수정 제목", "수정 본문"));
    assertTrue(updated.success());
    assertEquals("게시글이 수정되었습니다.", updated.message());
    assertEquals("제목", original.title());
    assertEquals("본문", list.get(0).content());
    assertEquals("수정 제목", queries.getPost(1).data().title());
    assertEquals(original.createdAt(), queries.getPost(1).data().createdAt());
    var deleted = commands.deletePost(1);
    assertTrue(deleted.success());
    assertEquals("게시글이 삭제되었습니다.", deleted.message());
    assertEquals(1, list.size());
    assertFailure(queries.getPost(1), "POST_NOT_FOUND");
  }

  @Test
  void commandAndQueryControllersShareTheSameStorage() {
    commands.createPost(request(PostCategory.GENERAL));
    assertEquals("제목", queries.getPost(1).data().title());
    commands.updatePost(1, new UpdatePostRequest("변경 제목", "변경 본문"));
    assertEquals("변경 본문", queries.getPost(1).data().content());
    commands.deletePost(1);
    assertFalse(queries.hasPosts().data());
  }

  @Test
  void separateServerConfigurationsKeepIndependentStorageAndIds() {
    var other = PostServerConfiguration.createControllers();
    commands.createPost(request(PostCategory.GENERAL));
    assertFalse(other.queryController().hasPosts().data());
    other.commandController().createPost(request(PostCategory.QUESTION));
    assertEquals(1L, other.queryController().getPosts().data().get(0).id());
    assertEquals(PostCategory.GENERAL, queries.getPost(1).data().category());
    assertEquals(PostCategory.QUESTION, other.queryController().getPost(1).data().category());
  }

  private void assertFailure(BaseResponse<?> response, String code) {
    assertFalse(response.success());
    assertEquals(code, response.code());
    assertNull(response.data());
  }
}
