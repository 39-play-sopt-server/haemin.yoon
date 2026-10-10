package org.sopt.post.application.port.out;

import org.sopt.post.domain.Post;

/**
 * 게시글의 저장·교체·삭제를 담당하는 변경 전용 출력 포트입니다.
 * 조회 포트와 별도 계약으로 두되, 구현 어댑터와 실제 저장소는 함께 사용할 수 있습니다.
 * 서비스를 구체적인 HashMap 관리 방식에서 분리합니다.
 */
public interface SavePostPort {
  /** 새 ID의 게시글을 저장하며, 중복 ID는 기존 글을 덮어쓰지 않고 거부합니다. */
  void save(Post post);

  /** 같은 ID의 게시글을 교체하고 true를 반환하며, 대상이 없으면 저장하지 않고 false를 반환합니다. */
  boolean update(Post post);

  /** 해당 ID의 게시글을 삭제하고 true를 반환하며, 대상이 없으면 false를 반환합니다. */
  boolean deleteById(long id);
}
