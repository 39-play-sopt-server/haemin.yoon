package org.sopt.client.console;

import org.sopt.post.adapter.in.api.dto.response.PostResponse;
import org.sopt.post.adapter.in.api.dto.PostCategory;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.io.PrintStream;

/**
 * 메뉴·안내 문구·서버 응답 DTO를 콘솔에 출력하는 화면 담당 객체입니다.
 * 게시글 처리와 저장소 접근은 하지 않으며, 날짜 표시 형식 등 화면 표현만 결정합니다.
 * PrintStream을 주입받아 표준 출력 대신 테스트용 출력에도 사용할 수 있습니다.
 */
public class PostView {
  private static final DateTimeFormatter DATE_TIME_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  private final PrintStream output;

  public PostView(PrintStream output) {
    this.output = output;
  }

  public void showMenu() {
    output.println("\n=== 게시판 ===");
    output.println("1. 게시글 작성");
    output.println("2. 게시글 목록 조회");
    output.println("3. 게시글 단건 조회");
    output.println("4. 게시글 수정");
    output.println("5. 게시글 삭제");
    output.println("6. 종료");
  }

  public void showCategories() {
    PostCategory[] categories = PostCategory.values();
    output.println("=== 카테고리 ===");
    for (int i = 0; i < categories.length; i++) {
      output.println((i + 1) + ". " + categories[i].getDisplayName());
    }
  }

  /** 줄바꿈 없는 입력 안내도 사용자가 바로 볼 수 있도록 출력 버퍼를 비웁니다. */
  public void showPrompt(String prompt) {
    output.print(prompt);
    output.flush();
  }

  /** 목록 순번이 아닌 실제 ID를 보여줘, 삭제 후에도 사용자가 같은 ID로 게시글을 선택하게 합니다. */
  public void showPosts(List<PostResponse> posts) {
    output.println("\n=== 게시글 목록 ===");
    if (posts.isEmpty()) {
      showMessage("게시글이 없습니다.");
      return;
    }
    for (int i = 0; i < posts.size(); i++) {
      PostResponse post = posts.get(i);
      output.println(post.id() + ". [" + post.category().getDisplayName() + "] "
          + post.title() + " (작성자: " + post.author() + ")");
    }
  }

  public void showPost(PostResponse post) {
    output.println("\n=== 게시글 ===");
    output.println("ID: " + post.id());
    output.println("제목: " + post.title());
    output.println("내용: " + post.content());
    output.println("카테고리: " + post.category().getDisplayName());
    output.println("작성자: " + post.author());
    output.println("작성 시각: " + post.createdAt().format(DATE_TIME_FORMAT));
  }

  public void showMessage(String message) {
    output.println(message);
  }

}
