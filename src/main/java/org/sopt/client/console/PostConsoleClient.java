package org.sopt.client.console;

import java.util.Optional;
import java.util.function.Consumer;
import org.sopt.global.response.BaseResponse;
import org.sopt.post.adapter.in.api.PostCommandController;
import org.sopt.post.adapter.in.api.PostQueryController;
import org.sopt.post.adapter.in.api.dto.PostCategory;
import org.sopt.post.adapter.in.api.dto.request.CreatePostRequest;
import org.sopt.post.adapter.in.api.dto.request.UpdatePostRequest;
import org.sopt.post.adapter.in.api.dto.response.PostResponse;

/**
 * 콘솔의 메뉴 흐름을 진행하고 변경 요청과 조회 요청을 각각의 서버 컨트롤러로 보냅니다.
 * 입력과 출력은 PostInput·PostView에 맡기며, 게시글 규칙이나 저장소에는 직접 접근하지 않습니다.
 * 서버 예외 대신 BaseResponse를 처리해 클라이언트와 서버의 책임을 나눕니다.
 */
public class PostConsoleClient {
  private final PostInput input;
  private final PostView view;
  private final PostCommandController commandController;
  private final PostQueryController queryController;

  public PostConsoleClient(PostInput input, PostView view,
      PostCommandController commandController, PostQueryController queryController) {
    this.input = input;
    this.view = view;
    this.commandController = commandController;
    this.queryController = queryController;
  }

  /** 메뉴를 반복 실행하며, 종료 명령 또는 입력 종료 시 진행 중인 입력을 마칩니다. */
  public void run() {
    while (true) {
      try {
        int command = input.readCommand();
        switch (command) {
          case 1:
            createPost();
            break;
          case 2:
            showResult(queryController.getPosts(), view::showPosts);
            break;
          case 3:
            readPost();
            break;
          case 4:
            updatePost();
            break;
          case 5:
            deletePost();
            break;
          case 6:
            view.showMessage("프로그램을 종료합니다.");
            return;
          default:
            view.showMessage("잘못된 입력입니다.");
        }
      } catch (ConsoleInputClosedException e) {
        view.showMessage("프로그램을 종료합니다.");
        return;
      }
    }
  }

  /** 모든 입력을 받은 후 요청하므로, 입력 도중 종료되면 미완성 게시글을 저장하지 않습니다. */
  private void createPost() {
    String title = input.readTitle();
    String content = input.readContent();
    PostCategory category = input.readCategory();
    String author = input.readAuthor();
    BaseResponse<Void> response = commandController.createPost(
        new CreatePostRequest(title, content, category, author));
    view.showMessage(response.message());
  }

  /** 대상 선택에서 받은 응답을 재사용해 같은 게시글을 다시 조회하지 않습니다. */
  private void readPost() {
    selectPost("조회").ifPresent(view::showPost);
  }

  /** 대상을 먼저 확인해 존재하지 않는 게시글의 새 제목·본문을 불필요하게 입력받지 않습니다. */
  private void updatePost() {
    Optional<PostResponse> selected = selectPost("수정");
    if (selected.isEmpty()) {
      return;
    }
    String title = input.readNewTitle();
    String content = input.readNewContent();
    view.showMessage(commandController.updatePost(selected.get().id(),
        new UpdatePostRequest(title, content)).message());
  }

  private void deletePost() {
    selectPost("삭제").ifPresent(post ->
        view.showMessage(commandController.deletePost(post.id()).message()));
  }

  /**
   * 빈 저장소는 ID 입력 전에 안내하고, 서버 실패는 빈 저장소와 구분해 표시합니다.
   * 성공하면 조회한 게시글을 반환하며, 진행할 수 없으면 Optional.empty()로 호출자에게 알립니다.
   */
  private Optional<PostResponse> selectPost(String action) {
    BaseResponse<Boolean> exists = queryController.hasPosts();
    if (!exists.success()) {
      view.showMessage(exists.message());
      return Optional.empty();
    }
    if (!exists.data()) {
      view.showMessage("게시글이 없습니다.");
      return Optional.empty();
    }
    long id = input.readPostId(action);
    BaseResponse<PostResponse> selected = queryController.getPost(id);
    if (!selected.success()) {
      view.showMessage(selected.message());
      return Optional.empty();
    }
    return Optional.of(selected.data());
  }

  /** 성공일 때만 데이터를 전달해 실패 응답의 null 데이터를 화면에서 사용하지 않게 합니다. */
  private <T> void showResult(BaseResponse<T> response, Consumer<T> onSuccess) {
    if (response.success()) {
      onSuccess.accept(response.data());
    } else {
      view.showMessage(response.message());
    }
  }
}
