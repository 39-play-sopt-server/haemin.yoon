package org.sopt.post.application.port.out;

import org.sopt.post.domain.Post;

/** 게시글 생성·삭제·조회수 증가를 위한 쓰기 출력 포트입니다. 본문 수정은 서비스 트랜잭션의 변경 감지로 반영합니다. */
public interface SavePostPort {
  /** ID가 없는 새 게시글을 저장하며, ID는 DB에서 생성합니다. 이미 ID가 있는 게시글은 거부합니다. */
  void save(Post post);

  /** 서비스가 조회한 게시글을 삭제합니다. 대상 존재 여부는 서비스에서 검증합니다. */
  void delete(Post post);

  /** 해당 게시글의 조회수를 원자적으로 1 증가시키며, 게시글이 없으면 false를 반환합니다. */
  boolean incrementViewCount(long id);
}
