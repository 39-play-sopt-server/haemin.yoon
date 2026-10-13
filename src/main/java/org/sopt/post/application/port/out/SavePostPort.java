package org.sopt.post.application.port.out;

import org.sopt.post.domain.Post;

/** 게시글 생성·삭제를 위한 출력 포트입니다. 수정은 서비스 트랜잭션의 변경 감지로 반영합니다. */
public interface SavePostPort {
  /** ID가 없는 새 게시글을 저장하며, ID는 DB에서 생성합니다. 이미 ID가 있는 게시글은 거부합니다. */
  void save(Post post);

  /** 서비스가 조회한 게시글을 삭제합니다. 대상 존재 여부는 서비스에서 검증합니다. */
  void delete(Post post);
}
