package org.sopt.client.console;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sopt.post.adapter.in.api.PostController;
import org.sopt.post.adapter.in.api.dto.PostCategory;
import org.sopt.post.adapter.in.api.dto.request.CreatePostRequest;
import org.sopt.post.config.PostServerConfiguration;
import org.sopt.support.TestServerFixtures;
import org.sopt.global.exception.GlobalExceptionHandler;
import org.sopt.post.adapter.out.persistence.InMemoryPostRepository;
import org.sopt.post.adapter.out.id.SequentialPostIdGenerator;
import org.sopt.post.application.service.PostService;
import org.sopt.post.domain.Post;

import static org.junit.jupiter.api.Assertions.*;

class PostConsoleClientTest {
  private final PostController server = PostServerConfiguration.createController();

  @Test
  void displaysSelectedPostWithOneLookup() {
    AtomicInteger lookups = new AtomicInteger();
    var repository = new InMemoryPostRepository() {
      @Override
      public Optional<Post> findById(long id) {
        lookups.incrementAndGet();
        return super.findById(id);
      }
    };
    var controller = new PostController(
        new PostService(repository, new SequentialPostIdGenerator()),
        new GlobalExceptionHandler());
    controller.createPost(new CreatePostRequest("제목", "본문", PostCategory.GENERAL, "작성자"));

    String output = run("3\n1\n6\n", controller);

    assertTrue(output.contains("제목: 제목"));
    assertTrue(output.contains("내용: 본문"));
    assertEquals(1, lookups.get());
  }

  @Test
  void reportsInvalidTitleAndAllowsNextCreation() {
    String output = run("1\n   \n본문\n1\n작성자\n1\n제목\n본문\n1\n작성자\n6\n");
    assertTrue(output.contains("제목은 비어 있을 수 없습니다."));
    assertEquals(1, output.split("게시글이 작성되었습니다.", -1).length - 1);
    assertTrue(output.contains("프로그램을 종료합니다."));
    assertEquals(1, server.getPosts().data().size());
    assertEquals("제목", server.getPosts().data().get(0).title());
  }

  @Test
  void reportsInvalidContentWithoutSuccessMessage() {
    String output = run("1\n제목\n\n1\n작성자\n2\n6\n");
    assertTrue(output.contains("본문은 비어 있을 수 없습니다."));
    assertFalse(output.contains("게시글이 작성되었습니다."));
    assertTrue(output.contains("게시글이 없습니다."));
    assertTrue(output.contains("프로그램을 종료합니다."));
    assertTrue(server.getPosts().data().isEmpty());
  }

  @Test
  void performsCrudThroughConsole() {
    String output = run("1\n제목\n본문\n1\n작성자\n2\n3\n1\n4\n1\n새 제목\n새 본문\n3\n1\n5\n1\n2\n6\n");
    assertTrue(output.contains("게시글이 작성되었습니다."));
    assertTrue(output.contains("1. [일반] 제목 (작성자: 작성자)"));
    assertTrue(output.contains("제목: 제목"));
    assertTrue(output.contains("내용: 본문"));
    assertTrue(output.contains("게시글이 수정되었습니다."));
    assertTrue(output.contains("제목: 새 제목"));
    assertTrue(output.contains("내용: 새 본문"));
    assertTrue(output.contains("게시글이 삭제되었습니다."));
    assertTrue(output.contains("게시글이 없습니다."));
    assertTrue(output.contains("프로그램을 종료합니다."));
    assertTrue(server.getPosts().data().isEmpty());
  }

  @Test
  void emptyStoreDoesNotAskForPostNumber() {
    String output = run("3\n4\n5\n6\n");
    assertEquals(3, output.split("게시글이 없습니다.", -1).length - 1);
    assertFalse(output.contains("할 게시글 ID:"));
    assertFalse(output.contains("새로운 제목:"));
  }

  @Test
  void invalidNumbersDoNotModifyPostsOrAskForNewContent() {
    server.createPost(new CreatePostRequest("원래 제목", "원래 본문", PostCategory.GENERAL, "작성자"));
    String output = run("3\n0\n4\n2\n5\n-1\n6\n");
    assertEquals(3, output.split("존재하지 않는 게시글입니다.", -1).length - 1);
    assertFalse(output.contains("새로운 제목:"));
    assertEquals(1, server.getPosts().data().size());
    assertEquals("원래 제목", server.getPost(1).data().title());
    assertEquals("원래 본문", server.getPost(1).data().content());
  }

  @Test
  void retriesNonNumericInputAndRejectsUnknownMenuCommand() {
    server.createPost(new CreatePostRequest("제목", "본문", PostCategory.GENERAL, "작성자"));
    String output = run("문자\n0\n3\n문자\n1\n6\n");
    assertEquals(2, output.split("숫자를 입력해주세요.", -1).length - 1);
    assertTrue(output.contains("잘못된 입력입니다."));
    assertTrue(output.contains("내용: 본문"));
    assertTrue(output.contains("프로그램을 종료합니다."));
  }

  @Test
  void categoryRetriesAndMetadataAppearsInListAndDetail() {
    String output = run("1\n질문 제목\n질문 본문\n문자\n0\n4\n2\n홍길동\n2\n3\n1\n6\n");
    assertTrue(output.contains("1. 일반"));
    assertTrue(output.contains("2. 질문"));
    assertTrue(output.contains("3. 정보"));
    assertTrue(output.contains("숫자를 입력해주세요."));
    assertEquals(2, output.split("올바른 카테고리 번호를 선택해주세요.", -1).length - 1);
    assertTrue(output.contains("1. [질문] 질문 제목 (작성자: 홍길동)"));
    assertTrue(output.contains("카테고리: 질문"));
    assertTrue(output.contains("작성자: 홍길동"));
    assertTrue(output.matches("(?s).*작성 시각: \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}.*"));
    assertEquals(PostCategory.QUESTION, server.getPost(1).data().category());
    assertEquals("홍길동", server.getPost(1).data().author());
  }

  @Test
  void blankAuthorIsRejectedAndMenuContinues() {
    String output = run("1\n제목\n본문\n3\n   \n2\n6\n");
    assertTrue(output.contains("작성자는 비어 있을 수 없습니다."));
    assertFalse(output.contains("게시글이 작성되었습니다."));
    assertTrue(output.contains("프로그램을 종료합니다."));
    assertTrue(server.getPosts().data().isEmpty());
  }

  @Test
  void continuesWithValidReadAfterMissingPostException() {
    server.createPost(new CreatePostRequest("제목", "본문", PostCategory.GENERAL, "작성자"));
    String output = run("3\n99\n3\n1\n6\n");
    assertEquals(1, output.split("존재하지 않는 게시글입니다.", -1).length - 1);
    assertTrue(output.contains("내용: 본문"));
    assertTrue(output.contains("프로그램을 종료합니다."));
  }

  @Test
  void selectsStoredIdAfterDeletionInsteadOfListPosition() {
    server.createPost(new CreatePostRequest("첫 글", "첫 본문", PostCategory.GENERAL, "작성자"));
    server.createPost(new CreatePostRequest("둘째 글", "둘째 본문", PostCategory.GENERAL, "작성자"));
    server.createPost(new CreatePostRequest("셋째 글", "셋째 본문", PostCategory.GENERAL, "작성자"));
    String output = run("5\n2\n2\n3\n3\n4\n3\n셋째 수정\n수정 본문\n3\n2\n6\n");
    assertTrue(output.contains("3. [일반] 셋째 글 (작성자: 작성자)"));
    assertTrue(output.contains("조회할 게시글 ID:"));
    assertTrue(output.contains("내용: 셋째 본문"));
    assertTrue(output.contains("존재하지 않는 게시글입니다."));
    assertEquals("셋째 수정", server.getPost(3).data().title());
    assertEquals(3L, server.getPost(3).data().id());
  }

  @Test
  void readsLongIdsAndRetriesOverflowInput() {
    PostController custom = TestServerFixtures.withFixedId(5000000000L);
    custom.createPost(new CreatePostRequest("제목", "본문", PostCategory.GENERAL, "작성자"));
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (PrintStream output = new PrintStream(buffer, true, StandardCharsets.UTF_8);
         Scanner scanner = new Scanner("3\n9223372036854775808\n5000000000\n6\n")) {
      PostView view = new PostView(output);
      new PostConsoleClient(new PostInput(scanner, view), view, custom).run();
    }
    String output = buffer.toString(StandardCharsets.UTF_8);
    assertTrue(output.contains("숫자를 입력해주세요."));
    assertTrue(output.contains("ID: 5000000000"));
    assertTrue(output.contains("내용: 본문"));
  }

  @Test
  void failedExistenceCheckDoesNotReadIdOrTreatFailureAsEmptyStore() {
    String output = run("4\n6\n", TestServerFixtures.withFailingExistenceCheck());
    assertTrue(output.contains("서버 오류가 발생했습니다."));
    assertFalse(output.contains("게시글이 없습니다."));
    assertFalse(output.contains("할 게시글 ID:"));
    assertTrue(output.contains("프로그램을 종료합니다."));
  }

  @Test
  void failedReadBeforeUpdateDoesNotAskForNewContent() {
    PostController failingServer = TestServerFixtures.withFailingRead();
    failingServer.createPost(new CreatePostRequest("제목", "본문", PostCategory.GENERAL, "작성자"));
    String output = run("4\n1\n6\n", failingServer);
    assertTrue(output.contains("서버 오류가 발생했습니다."));
    assertFalse(output.contains("새로운 제목:"));
    assertFalse(output.contains("게시글이 수정되었습니다."));
    assertTrue(output.contains("프로그램을 종료합니다."));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "문자\n", "1\n", "1\n제목\n", "1\n제목\n본문\n",
      "1\n제목\n본문\n1\n", "1\n제목\n본문\n0\n"})
  void endOfInputExitsWithoutSavingIncompleteCreation(String input) {
    String output = run(input);
    assertEquals(1, output.split("프로그램을 종료합니다.", -1).length - 1);
    assertFalse(output.contains("게시글이 작성되었습니다."));
    assertTrue(server.getPosts().data().isEmpty());
  }

  @Test
  void endOfInputDuringUpdatePreservesPost() {
    server.createPost(new CreatePostRequest("기존 제목", "기존 본문", PostCategory.GENERAL, "작성자"));
    String output = run("4\n1\n새 제목\n");
    assertTrue(output.contains("프로그램을 종료합니다."));
    assertFalse(output.contains("게시글이 수정되었습니다."));
    assertEquals("기존 제목", server.getPost(1).data().title());
    assertEquals("기존 본문", server.getPost(1).data().content());
  }

  @Test
  void invalidUpdateShowsErrorAndAllowsNextValidUpdate() {
    server.createPost(new CreatePostRequest("기존 제목", "기존 본문", PostCategory.GENERAL, "작성자"));
    String output = run("4\n1\n새 제목\n   \n3\n1\n4\n1\n정상 제목\n정상 본문\n6\n");
    assertTrue(output.contains("본문은 비어 있을 수 없습니다."));
    assertTrue(output.contains("제목: 기존 제목"));
    assertTrue(output.contains("내용: 기존 본문"));
    assertEquals(1, output.split("게시글이 수정되었습니다.", -1).length - 1);
    assertEquals("정상 제목", server.getPost(1).data().title());
    assertEquals("정상 본문", server.getPost(1).data().content());
  }

  private String run(String input) {
    return run(input, server);
  }

  private String run(String input, PostController controller) {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (PrintStream output = new PrintStream(buffer, true, StandardCharsets.UTF_8);
         Scanner scanner = new Scanner(input)) {
      PostView view = new PostView(output);
      PostInput postInput = new PostInput(scanner, view);
      new PostConsoleClient(postInput, view, controller).run();
    }
    return buffer.toString(StandardCharsets.UTF_8);
  }
}
