package org.sopt;

import java.util.Scanner;

public class Main {

  public static void main(String[] args) {
    PostView view = new PostView(new Scanner(System.in));
    PostController controller = new PostController(view);
    controller.run();
  }
}
