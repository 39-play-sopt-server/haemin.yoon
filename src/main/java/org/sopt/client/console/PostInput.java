package org.sopt.client.console;

import java.util.Scanner;
import org.sopt.post.adapter.in.api.dto.PostCategory;

/**
 * 콘솔 문자열을 읽어 메뉴 번호·게시글 ID·요청 값으로 변환하는 입력 담당 객체입니다.
 * 숫자 형식과 선택 범위는 여기서 확인하지만, 제목·본문의 유효성은 서버 도메인이 판단합니다.
 * Scanner와 안내용 View를 주입받아 실제 콘솔 없이도 입력 흐름을 검증할 수 있습니다.
 */
public class PostInput {
  private final Scanner scanner;
  private final PostView view;

  public PostInput(Scanner scanner, PostView view) {
    this.scanner = scanner;
    this.view = view;
  }

  public int readCommand() {
    view.showMenu();
    return readNumber("선택: ");
  }

  public String readTitle() {
    return readText("제목: ");
  }

  public String readContent() {
    return readText("내용: ");
  }

  /** View와 같은 Enum 순서를 사용해 화면의 1부터 시작하는 선택 번호를 카테고리로 변환합니다. */
  public PostCategory readCategory() {
    view.showCategories();
    PostCategory[] categories = PostCategory.values();
    while (true) {
      int number = readNumber("카테고리 선택: ");
      if (number >= 1 && number <= categories.length) {
        return categories[number - 1];
      }
      view.showMessage("올바른 카테고리 번호를 선택해주세요.");
    }
  }

  public String readAuthor() {
    return readText("작성자: ");
  }

  public String readNewTitle() {
    return readText("새로운 제목: ");
  }

  public String readNewContent() {
    return readText("새로운 내용: ");
  }

  /** 게시글 ID는 메뉴 번호와 달리 long 범위를 사용하며, 숫자 형식 오류는 재입력받습니다. */
  public long readPostId(String action) {
    while (true) {
      try {
        return Long.parseLong(readText(action + "할 게시글 ID: "));
      } catch (NumberFormatException e) {
        view.showMessage("숫자를 입력해주세요.");
      }
    }
  }

  /** EOF를 정상 종료 신호로 전달하며, 공백은 서버 검증에 맡기므로 입력을 trim하지 않습니다. */
  private String readText(String prompt) {
    view.showPrompt(prompt);
    if (!scanner.hasNextLine()) {
      throw new ConsoleInputClosedException();
    }
    return scanner.nextLine();
  }

  private int readNumber(String prompt) {
    while (true) {
      try {
        return Integer.parseInt(readText(prompt));
      } catch (NumberFormatException e) {
        view.showMessage("숫자를 입력해주세요.");
      }
    }
  }
}
