package org.sopt.post.application.port.in.command;

import org.sopt.post.domain.Category;

/**
 * 게시글 상태를 변경하는 기능만 공개하는 Command 입력 포트입니다.
 * 조회 계약과 분리해 변경 작업의 호출 지점과 책임을 명확하게 합니다.
 * 외부 요청 DTO를 받지 않으므로 콘솔이나 HTTP의 요청 형식에 의존하지 않습니다.
 */
public interface PostCommandUseCase {
  /** 유효한 게시글을 서버에서 발급한 ID로 생성합니다. */
  void createPost(String title, String content, Category category, String author);

  /** 제목·본문을 검증한 후 함께 교체하며, 대상이 없거나 검증에 실패하면 예외를 발생시킵니다. */
  void updatePost(long id, String title, String content);

  /** 해당 게시글을 삭제하며, 없으면 POST_NOT_FOUND 예외를 발생시킵니다. */
  void deletePost(long id);
}
