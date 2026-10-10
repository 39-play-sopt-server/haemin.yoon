package org.sopt.post.application.service;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import org.sopt.post.domain.Category;
import org.sopt.post.domain.exception.PostException;
import org.sopt.post.domain.exception.PostErrorCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sopt.post.adapter.out.persistence.InMemoryPostRepository;
import org.sopt.post.adapter.out.id.SequentialPostIdGenerator;
import org.sopt.post.application.port.in.command.PostCommandUseCase;
import org.sopt.post.application.port.in.query.PostQueryUseCase;
import org.sopt.post.application.service.command.PostCommandService;
import org.sopt.post.application.service.query.PostQueryService;
import org.sopt.post.application.port.out.SavePostPort;
import org.sopt.post.domain.Post;

import static org.junit.jupiter.api.Assertions.*;

class PostCommandQueryServiceTest {
  private final InMemoryPostRepository repository = new InMemoryPostRepository();
  private final PostCommandUseCase commands = new PostCommandService(repository, repository, new SequentialPostIdGenerator());
  private final PostQueryUseCase queries = new PostQueryService(repository);

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "   ", "\t", "\n", "\u3000"})
  void invalidTitleIsNotStored(String title) {
    commands.createPost("기존 제목", "기존 본문", Category.GENERAL, "작성자");
    PostException exception = assertThrows(PostException.class,
        () -> commands.createPost(title, "본문", Category.GENERAL, "작성자"));
    assertEquals(PostErrorCode.INVALID_TITLE, exception.getErrorCode());
    assertEquals("제목은 비어 있을 수 없습니다.", exception.getMessage());
    assertEquals(1, queries.getPosts().size());
    assertEquals("기존 제목", queries.getPost(1).getTitle());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "   ", "\t", "\n", "\u3000"})
  void invalidContentIsNotStored(String content) {
    PostException exception = assertThrows(PostException.class,
        () -> commands.createPost("제목", content, Category.GENERAL, "작성자"));
    assertEquals(PostErrorCode.INVALID_CONTENT, exception.getErrorCode());
    assertEquals("본문은 비어 있을 수 없습니다.", exception.getMessage());
    assertTrue(queries.getPosts().isEmpty());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t", "\n", "\u3000"})
  void invalidAuthorIsNotStored(String author) {
    PostException exception = assertThrows(PostException.class,
        () -> commands.createPost("제목", "본문", Category.GENERAL, author));
    assertEquals(PostErrorCode.INVALID_AUTHOR, exception.getErrorCode());
    assertEquals("작성자는 비어 있을 수 없습니다.", exception.getMessage());
    assertTrue(queries.getPosts().isEmpty());
  }

  @Test
  void nullCategoryIsNotStored() {
    PostException exception = assertThrows(PostException.class,
        () -> commands.createPost("제목", "본문", null, "작성자"));
    assertEquals(PostErrorCode.INVALID_CATEGORY, exception.getErrorCode());
    assertEquals("카테고리는 필수입니다.", exception.getMessage());
    assertTrue(queries.getPosts().isEmpty());
  }

  @ParameterizedTest
  @EnumSource(Category.class)
  void metadataIsRecordedAndPreservedOnUpdate(Category category) {
    LocalDateTime before = LocalDateTime.now();
    commands.createPost("제목", "본문", category, "홍길동");
    LocalDateTime after = LocalDateTime.now();
    var post = queries.getPost(1);
    assertEquals(category, post.getCategory());
    assertEquals("홍길동", post.getAuthor());
    assertFalse(post.getCreatedAt().isBefore(before));
    assertFalse(post.getCreatedAt().isAfter(after));
    LocalDateTime createdAt = post.getCreatedAt();
    commands.updatePost(1, "새 제목", "새 본문");
    var updated = queries.getPost(1);
    assertEquals(category, updated.getCategory());
    assertEquals("홍길동", updated.getAuthor());
    assertEquals(createdAt, updated.getCreatedAt());
    assertEquals("새 제목", updated.getTitle());
  }

  @Test
  void preservesWhitespaceAroundValidText() {
    commands.createPost(" 제목 ", " 본문 ", Category.GENERAL, "작성자");
    assertEquals(" 제목 ", queries.getPost(1).getTitle());
    assertEquals(" 본문 ", queries.getPost(1).getContent());
  }

  @Test
  void createsAndListsPostsInOrder() {
    commands.createPost("첫 글", "첫 본문", Category.GENERAL, "작성자");
    commands.createPost("둘째 글", "둘째 본문", Category.GENERAL, "작성자");
    assertTrue(queries.hasPosts());
    assertEquals(2, queries.getPosts().size());
    assertEquals("첫 글", queries.getPosts().get(0).getTitle());
    assertEquals("둘째 본문", queries.getPost(2).getContent());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t", "\n", "\u3000"})
  void invalidUpdatedTitlePreservesEntirePost(String title) {
    commands.createPost("기존 제목", "기존 본문", Category.GENERAL, "작성자");
    PostException exception = assertThrows(PostException.class,
        () -> commands.updatePost(1, title, "새 본문"));
    assertEquals(PostErrorCode.INVALID_TITLE, exception.getErrorCode());
    assertEquals("기존 제목", queries.getPost(1).getTitle());
    assertEquals("기존 본문", queries.getPost(1).getContent());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t", "\n", "\u3000"})
  void invalidUpdatedContentDoesNotPartiallyUpdateTitle(String content) {
    commands.createPost("기존 제목", "기존 본문", Category.GENERAL, "작성자");
    PostException exception = assertThrows(PostException.class,
        () -> commands.updatePost(1, "새 제목", content));
    assertEquals(PostErrorCode.INVALID_CONTENT, exception.getErrorCode());
    assertEquals("기존 제목", queries.getPost(1).getTitle());
    assertEquals("기존 본문", queries.getPost(1).getContent());
  }

  @Test
  void updatesBothTitleAndContent() {
    commands.createPost("이전 제목", "이전 본문", Category.GENERAL, "작성자");
    commands.updatePost(1, "새 제목", "새 본문");
    assertEquals("새 제목", queries.getPost(1).getTitle());
    assertEquals("새 본문", queries.getPost(1).getContent());
  }

  @Test
  void updatingRetrievedPostDoesNotBypassRepositoryUpdate() {
    commands.createPost("기존 제목", "기존 본문", Category.GENERAL, "작성자");
    var retrieved = queries.getPost(1);

    retrieved.update("우회 제목", "우회 본문");

    assertEquals("기존 제목", queries.getPost(1).getTitle());
    assertEquals("기존 본문", queries.getPost(1).getContent());
  }

  @Test
  void serviceUpdatePreservesPreviouslyRetrievedPostAndList() {
    commands.createPost("기존 제목", "기존 본문", Category.QUESTION, "작성자");
    var retrieved = queries.getPost(1);
    var list = queries.getPosts();

    commands.updatePost(1, "새 제목", "새 본문");

    assertEquals("기존 제목", retrieved.getTitle());
    assertEquals("기존 본문", list.get(0).getContent());
    var stored = queries.getPost(1);
    assertEquals("새 제목", stored.getTitle());
    assertEquals("새 본문", stored.getContent());
    assertEquals(retrieved.getId(), stored.getId());
    assertEquals(retrieved.getCategory(), stored.getCategory());
    assertEquals(retrieved.getAuthor(), stored.getAuthor());
    assertEquals(retrieved.getCreatedAt(), stored.getCreatedAt());
  }

  @Test
  void deletionPreservesOtherIdsAndNewPostDoesNotReuseDeletedId() {
    commands.createPost("첫 글", "첫 본문", Category.GENERAL, "작성자");
    commands.createPost("둘째 글", "둘째 본문", Category.GENERAL, "작성자");
    commands.createPost("셋째 글", "셋째 본문", Category.GENERAL, "작성자");
    commands.deletePost(2);
    assertEquals(java.util.List.of(1L, 3L), queries.getPosts().stream().map(p -> p.getId()).toList());
    assertEquals("셋째 글", queries.getPost(3).getTitle());
    assertNotFound(() -> queries.getPost(2));
    commands.createPost("새 글", "새 본문", Category.GENERAL, "작성자");
    assertEquals(4L, queries.getPost(4).getId());
    commands.updatePost(3, "셋째 수정", "수정 본문");
    assertEquals(3L, queries.getPost(3).getId());
    assertEquals("첫 글", queries.getPost(1).getTitle());
  }

  @Test
  void creationUsesInjectedIdGenerator() {
    var customRepository = new InMemoryPostRepository();
    PostCommandUseCase custom = new PostCommandService(customRepository, customRepository, () -> 5000000000L);
    PostQueryUseCase customQueries = new PostQueryService(customRepository);
    custom.createPost("제목", "본문", Category.GENERAL, "작성자");
    assertEquals(5000000000L, customQueries.getPost(5000000000L).getId());
  }

  @Test
  void missingIdsDoNotChangePosts() {
    commands.createPost("제목", "본문", Category.GENERAL, "작성자");
    for (long id : new long[]{-1, 0, 2, Long.MAX_VALUE}) {
      assertNotFound(() -> queries.getPost(id));
      assertNotFound(() -> commands.updatePost(id, "변경", "변경"));
      assertNotFound(() -> commands.deletePost(id));
    }
    assertEquals(1, queries.getPosts().size());
    assertEquals("제목", queries.getPost(1).getTitle());
  }

  private void assertNotFound(org.junit.jupiter.api.function.Executable action) {
    PostException exception = assertThrows(PostException.class, action);
    assertEquals(PostErrorCode.POST_NOT_FOUND, exception.getErrorCode());
    assertEquals("존재하지 않는 게시글입니다.", exception.getMessage());
  }

  @Test
  void rejectedReplacementDoesNotChangeStoredPost() {
    commands.createPost("기존 제목", "기존 본문", Category.GENERAL, "작성자");
    SavePostPort rejectedWrites = new SavePostPort() {
      public void save(Post post) { repository.save(post); }
      public boolean update(Post post) { return false; }
      public boolean deleteById(long id) { return repository.deleteById(id); }
    };
    var command = new PostCommandService(repository, rejectedWrites, new SequentialPostIdGenerator());

    assertNotFound(() -> command.updatePost(1, "새 제목", "새 본문"));

    assertEquals("기존 제목", queries.getPost(1).getTitle());
    assertEquals("기존 본문", queries.getPost(1).getContent());
  }

  @Test
  void emptyRepositoryReturnsNoPost() {
    assertFalse(queries.hasPosts());
    assertTrue(queries.getPosts().isEmpty());
    assertNotFound(() -> queries.getPost(1));
    assertNotFound(() -> commands.updatePost(1, "제목", "본문"));
    assertNotFound(() -> commands.deletePost(1));
  }
}
