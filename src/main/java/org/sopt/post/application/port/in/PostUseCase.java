package org.sopt.post.application.port.in;

import java.util.List;
import org.sopt.post.domain.Category;
import org.sopt.post.domain.Post;

/**
 * 외부 입력 어댑터가 호출할 수 있는 게시글 기능을 정의하는 입력 포트입니다.
 * in은 애플리케이션을 호출하는 방향을 뜻하며, 요청·응답 데이터의 이동 방향을 뜻하지 않습니다.
 * 콘솔·HTTP 같은 외부 형식 대신 도메인 값과 일반 인자를 사용해 전송 DTO에 의존하지 않습니다.
 */
public interface PostUseCase {
  /** 도메인 규칙을 만족하는 게시글을 서버에서 발급한 ID로 생성합니다. */
  void createPost(String title, String content, Category category, String author);

  /** ID 오름차순의 게시글 스냅샷을 조회합니다. */
  List<Post> getPosts();

  /** 전체 목록을 조회하지 않고 저장된 게시글의 존재 여부를 확인합니다. */
  boolean hasPosts();

  /** 해당 게시글을 조회하며, 없으면 POST_NOT_FOUND 예외를 발생시킵니다. */
  Post getPost(long id);

  /** 제목·본문을 검증한 후 함께 교체하며, 대상이 없거나 검증에 실패하면 예외를 발생시킵니다. */
  void updatePost(long id, String title, String content);

  /** 해당 게시글을 삭제하며, 없으면 POST_NOT_FOUND 예외를 발생시킵니다. */
  void deletePost(long id);
}
