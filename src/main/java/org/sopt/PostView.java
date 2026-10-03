package org.sopt;

import java.util.List;
import java.util.Scanner;

public class PostView {
  private final Scanner scanner;

  public PostView(Scanner scanner) {
    this.scanner = scanner;
  }

  public int readCommand() {
    System.out.println("\n=== 게시판 ===");
    System.out.println("1. 게시글 작성");
    System.out.println("2. 게시글 목록 조회");
    System.out.println("3. 게시글 단건 조회");
    System.out.println("4. 게시글 수정");
    System.out.println("5. 게시글 삭제");
    System.out.println("6. 종료");
    return readNumber("선택: ");
  }

  public String readTitle() {
    return readText("제목: ");
  }

  public String readContent() {
    return readText("내용: ");
  }

  public String readNewTitle() {
    return readText("새로운 제목: ");
  }

  public String readNewContent() {
    return readText("새로운 내용: ");
  }

  public int readPostNumber(String action) {
    return readNumber(action + "할 게시글 번호: ");
  }

  public void showPosts(List<Post> posts) {
    System.out.println("\n=== 게시글 목록 ===");
    if (posts.isEmpty()) {
      showMessage("게시글이 없습니다.");
      return;
    }
    for (int i = 0; i < posts.size(); i++) {
      System.out.println((i + 1) + ". " + posts.get(i).getTitle());
    }
  }

  // 게시글 단건 조회 View 함수
  public void showPost(Post post) {
    System.out.println("\n=== 게시글 ===");
    System.out.println("제목: " + post.getTitle());
    System.out.println("내용: " + post.getContent());
  }

  public void showMessage(String message) {
    System.out.println(message);
  }

  private String readText(String prompt) {
    System.out.print(prompt);
    return scanner.nextLine();
  }

  private int readNumber(String prompt) {
    while (true) {
      try {
        return Integer.parseInt(readText(prompt));
      } catch (NumberFormatException e) {
        showMessage("숫자를 입력해주세요.");
      }
    }
  }
}
