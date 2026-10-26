package org.sopt.post.application.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sopt.post.adapter.out.persistence.InMemoryPostRepository;
import org.sopt.post.application.port.in.command.PostCommandUseCase;
import org.sopt.post.application.port.in.query.PostListQuery;
import org.sopt.post.application.port.in.query.PostQueryUseCase;
import org.sopt.post.application.service.command.PostCommandService;
import org.sopt.post.application.service.query.PostQueryService;
import org.sopt.post.domain.Category;
import org.sopt.post.domain.exception.PostErrorCode;
import org.sopt.post.domain.exception.PostException;
import org.sopt.support.MemberUnitSupport;

class PostCommandQueryServiceTest extends MemberUnitSupport {
  private final InMemoryPostRepository repository = new InMemoryPostRepository();
  private final PostCommandUseCase commands =
      new PostCommandService(repository, repository, memberQueries);
  private final PostQueryUseCase queries = new PostQueryService(repository);

  @Test
  void cursorUsesLastReturnedPostAndLookaheadOnlyDeterminesHasNext() {
    for (int index = 0; index < 3; index++)
      commands.createPost("글" + index, "본문", Category.GENERAL, 1L);
    var first = queries.getPosts(new PostListQuery(null, 2, null));
    assertEquals(
        java.util.List.of("글2", "글1"),
        first.posts().stream().map(org.sopt.post.domain.Post::getTitle).toList());
    assertTrue(first.hasNext());
    assertEquals(first.posts().getLast().getId(), first.nextCursor().id());
    var last = queries.getPosts(new PostListQuery(first.nextCursor(), 2, null));
    assertEquals(
        java.util.List.of("글0"),
        last.posts().stream().map(org.sopt.post.domain.Post::getTitle).toList());
    assertFalse(last.hasNext());
    assertNull(last.nextCursor());
    assertThrows(UnsupportedOperationException.class, first.posts()::clear);
  }

  @Test
  void negativeExpectedVersionIsRejectedForNonHttpCaller() {
    commands.createPost("제목", "본문", Category.GENERAL, memberId("작성자"));
    var exception =
        assertThrows(PostException.class, () -> commands.updatePost(1, "수정", "수정 본문", -1));
    assertEquals(PostErrorCode.INVALID_VERSION, exception.getErrorCode());
    assertEquals("제목", queries.getPost(1).getTitle());
  }

  @Test
  void versionMismatchIsRejectedForNonHttpCaller() {
    commands.createPost("제목", "본문", Category.GENERAL, memberId("작성자"));
    var exception =
        assertThrows(
            org.sopt.global.exception.BaseException.class,
            () -> commands.updatePost(1, "수정", "수정 본문", 1));
    assertEquals(org.sopt.global.exception.GlobalErrorCode.CONFLICT, exception.getErrorCode());
    assertEquals("제목", queries.getPost(1).getTitle());
  }

  @Test
  void queryingPostNeverChangesViewCount() {
    commands.createPost("제목", "본문", Category.GENERAL, memberId("작성자"));
    queries.getPost(1);
    queries.getPost(1);
    assertEquals(
        0, queries.getPosts(new PostListQuery(null, 100, null)).posts().getFirst().getViewCount());
  }

  @Test
  void commandIncrementsViewsAndQueryOnlyReadsTheResult() {
    commands.createPost("제목", "본문", Category.GENERAL, memberId("작성자"));
    commands.increaseViewCount(1);
    assertEquals(1, queries.getPost(1).getViewCount());
    assertEquals(1, queries.getPost(1).getViewCount());
    assertNotFound(() -> commands.increaseViewCount(2));
    assertEquals(1, queries.getPost(1).getViewCount());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "   ", "\t", "\n", "\u3000"})
  void invalidTitleIsNotStored(String title) {
    commands.createPost("기존 제목", "기존 본문", Category.GENERAL, memberId("작성자"));
    PostException exception =
        assertThrows(
            PostException.class,
            () -> commands.createPost(title, "본문", Category.GENERAL, memberId("작성자")));
    assertEquals(PostErrorCode.INVALID_TITLE, exception.getErrorCode());
    assertEquals("제목은 비어 있을 수 없습니다.", exception.getMessage());
    assertEquals(1, queries.getPosts(new PostListQuery(null, 100, null)).posts().size());
    assertEquals("기존 제목", queries.getPost(1).getTitle());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "   ", "\t", "\n", "\u3000"})
  void invalidContentIsNotStored(String content) {
    PostException exception =
        assertThrows(
            PostException.class,
            () -> commands.createPost("제목", content, Category.GENERAL, memberId("작성자")));
    assertEquals(PostErrorCode.INVALID_CONTENT, exception.getErrorCode());
    assertEquals("본문은 비어 있을 수 없습니다.", exception.getMessage());
    assertTrue(queries.getPosts(new PostListQuery(null, 100, null)).posts().isEmpty());
  }

  @Test
  void missingMemberIsNotStored() {
    var exception =
        assertThrows(
            org.sopt.member.domain.exception.MemberException.class,
            () -> commands.createPost("제목", "본문", Category.GENERAL, 999L));
    assertEquals(
        org.sopt.member.domain.exception.MemberErrorCode.MEMBER_NOT_FOUND,
        exception.getErrorCode());
    assertTrue(queries.getPosts(new PostListQuery(null, 100, null)).posts().isEmpty());
  }

  @Test
  void nullCategoryIsNotStored() {
    PostException exception =
        assertThrows(
            PostException.class, () -> commands.createPost("제목", "본문", null, memberId("작성자")));
    assertEquals(PostErrorCode.INVALID_CATEGORY, exception.getErrorCode());
    assertEquals("카테고리는 필수입니다.", exception.getMessage());
    assertTrue(queries.getPosts(new PostListQuery(null, 100, null)).posts().isEmpty());
  }

  @ParameterizedTest
  @EnumSource(Category.class)
  void metadataIsRecordedAndPreservedOnUpdate(Category category) {
    LocalDateTime before = LocalDateTime.now();
    commands.createPost("제목", "본문", category, memberId("홍길동"));
    LocalDateTime after = LocalDateTime.now();
    var post = queries.getPost(1);
    assertEquals(category, post.getCategory());
    assertEquals("홍길동", post.getMember().getName());
    assertFalse(post.getCreatedAt().isBefore(before));
    assertFalse(post.getCreatedAt().isAfter(after));
    LocalDateTime createdAt = post.getCreatedAt();
    commands.updatePost(1, "새 제목", "새 본문", 0L);
    var updated = queries.getPost(1);
    assertEquals(category, updated.getCategory());
    assertEquals("홍길동", updated.getMember().getName());
    assertEquals(createdAt, updated.getCreatedAt());
    assertEquals("새 제목", updated.getTitle());
  }

  @Test
  void preservesWhitespaceAroundValidText() {
    commands.createPost(" 제목 ", " 본문 ", Category.GENERAL, memberId("작성자"));
    assertEquals(" 제목 ", queries.getPost(1).getTitle());
    assertEquals(" 본문 ", queries.getPost(1).getContent());
  }

  @Test
  void createsAndListsPostsLatestFirst() {
    commands.createPost("첫 글", "첫 본문", Category.GENERAL, memberId("작성자"));
    commands.createPost("둘째 글", "둘째 본문", Category.GENERAL, memberId("작성자"));
    assertFalse(queries.getPosts(new PostListQuery(null, 100, null)).posts().isEmpty());
    assertEquals(2, queries.getPosts(new PostListQuery(null, 100, null)).posts().size());
    assertEquals(
        "둘째 글", queries.getPosts(new PostListQuery(null, 100, null)).posts().get(0).getTitle());
    assertEquals("둘째 본문", queries.getPost(2).getContent());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t", "\n", "\u3000"})
  void invalidUpdatedTitlePreservesEntirePost(String title) {
    commands.createPost("기존 제목", "기존 본문", Category.GENERAL, memberId("작성자"));
    PostException exception =
        assertThrows(PostException.class, () -> commands.updatePost(1, title, "새 본문", 0L));
    assertEquals(PostErrorCode.INVALID_TITLE, exception.getErrorCode());
    assertEquals("기존 제목", queries.getPost(1).getTitle());
    assertEquals("기존 본문", queries.getPost(1).getContent());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t", "\n", "\u3000"})
  void invalidUpdatedContentDoesNotPartiallyUpdateTitle(String content) {
    commands.createPost("기존 제목", "기존 본문", Category.GENERAL, memberId("작성자"));
    PostException exception =
        assertThrows(PostException.class, () -> commands.updatePost(1, "새 제목", content, 0L));
    assertEquals(PostErrorCode.INVALID_CONTENT, exception.getErrorCode());
    assertEquals("기존 제목", queries.getPost(1).getTitle());
    assertEquals("기존 본문", queries.getPost(1).getContent());
  }

  @Test
  void updatesBothTitleAndContent() {
    commands.createPost("이전 제목", "이전 본문", Category.GENERAL, memberId("작성자"));
    commands.updatePost(1, "새 제목", "새 본문", 0L);
    assertEquals("새 제목", queries.getPost(1).getTitle());
    assertEquals("새 본문", queries.getPost(1).getContent());
  }

  @Test
  void serviceUpdateChangesTheLoadedEntityInPlace() {
    commands.createPost("기존 제목", "기존 본문", Category.QUESTION, memberId("작성자"));
    var loaded = queries.getPost(1);
    var createdAt = loaded.getCreatedAt();

    commands.updatePost(1, "새 제목", "새 본문", 0L);

    assertSame(loaded, queries.getPost(1));
    assertEquals("새 제목", loaded.getTitle());
    assertEquals("새 본문", loaded.getContent());
    assertEquals(Category.QUESTION, loaded.getCategory());
    assertEquals("작성자", loaded.getMember().getName());
    assertEquals(createdAt, loaded.getCreatedAt());
  }

  @Test
  void deletionPreservesOtherIdsAndNewPostDoesNotReuseDeletedId() {
    commands.createPost("첫 글", "첫 본문", Category.GENERAL, memberId("작성자"));
    commands.createPost("둘째 글", "둘째 본문", Category.GENERAL, memberId("작성자"));
    commands.createPost("셋째 글", "셋째 본문", Category.GENERAL, memberId("작성자"));
    commands.deletePost(2);
    assertEquals(
        java.util.List.of(3L, 1L),
        queries.getPosts(new PostListQuery(null, 100, null)).posts().stream()
            .map(p -> p.getId())
            .toList());
    assertEquals("셋째 글", queries.getPost(3).getTitle());
    assertNotFound(() -> queries.getPost(2));
    commands.createPost("새 글", "새 본문", Category.GENERAL, memberId("작성자"));
    assertEquals(4L, queries.getPost(4).getId());
    commands.updatePost(3, "셋째 수정", "수정 본문", 0L);
    assertEquals(3L, queries.getPost(3).getId());
    assertEquals("첫 글", queries.getPost(1).getTitle());
  }

  @Test
  void missingIdsDoNotChangePosts() {
    commands.createPost("제목", "본문", Category.GENERAL, memberId("작성자"));
    for (long id : new long[] {-1, 0, 2, Long.MAX_VALUE}) {
      assertNotFound(() -> queries.getPost(id));
      assertNotFound(() -> commands.updatePost(id, "변경", "변경", 0L));
      assertNotFound(() -> commands.deletePost(id));
    }
    assertEquals(1, queries.getPosts(new PostListQuery(null, 100, null)).posts().size());
    assertEquals("제목", queries.getPost(1).getTitle());
  }

  private void assertNotFound(org.junit.jupiter.api.function.Executable action) {
    PostException exception = assertThrows(PostException.class, action);
    assertEquals(PostErrorCode.POST_NOT_FOUND, exception.getErrorCode());
    assertEquals("존재하지 않는 게시글입니다.", exception.getMessage());
  }

  @Test
  void emptyRepositoryReturnsNoPost() {
    assertTrue(queries.getPosts(new PostListQuery(null, 100, null)).posts().isEmpty());
    assertNotFound(() -> queries.getPost(1));
    assertNotFound(() -> commands.updatePost(1, "제목", "본문", 0L));
    assertNotFound(() -> commands.deletePost(1));
  }
}
