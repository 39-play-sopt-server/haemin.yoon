package org.sopt.post.application.port.in.query;

import java.util.List;
import org.sopt.post.domain.Post;

/**
 * 게시글을 변경하지 않고 조회하는 기능만 공개하는 Query 입력 포트입니다.
 * 목록·단건·존재 여부 조회를 묶고, 작성·수정·삭제는 Command 포트에 둡니다.
 * in은 외부에서 애플리케이션 기능을 호출하는 방향을 뜻합니다.
 */
public interface PostQueryUseCase {
  /** ID 오름차순의 독립된 게시글 스냅샷 목록을 반환합니다. */
  List<Post> getPosts();

  /** 전체 목록의 복사·정렬 없이 게시글 존재 여부를 확인합니다. */
  boolean hasPosts();

  /** 불변 게시글을 조회하며, 없으면 POST_NOT_FOUND 예외를 발생시킵니다. */
  Post getPost(long id);
}
