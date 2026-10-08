package org.sopt;

import java.util.Scanner;
import org.sopt.client.console.PostConsoleClient;
import org.sopt.client.console.PostInput;
import org.sopt.client.console.PostView;
import org.sopt.post.config.PostServerConfiguration;

/**
 * 프로그램의 시작점으로, 서버와 콘솔 클라이언트를 생성하고 연결합니다.
 * 같은 JVM에서 서버 컨트롤러를 직접 호출하는 과제 구조이므로 이곳에서 두 역할을 조립합니다.
 * 게시글 처리나 메뉴 진행은 각각 서비스와 클라이언트에 맡깁니다.
 */
public class Main {
  public static void main(String[] args) {
    var controller = PostServerConfiguration.createController();
    var view = new PostView(System.out);
    var input = new PostInput(new Scanner(System.in), view);
    new PostConsoleClient(input, view, controller).run();
  }
}
