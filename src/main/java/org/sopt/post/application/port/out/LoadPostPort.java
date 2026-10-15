package org.sopt.post.application.port.out;

import java.util.List;
import java.util.Optional;
import org.sopt.post.application.port.in.query.PostListQuery;
import org.sopt.post.domain.Post;

/**
 * 애플리케이션이 저장소에 요구하는 조회 전용 출력 포트입니다. Query 서비스에 이 계약만 주입하면 저장·수정·삭제 기능을 호출할 수 없습니다. Command 서비스도 수정
 * 대상을 읽기 위해 사용할 수 있으며, out은 애플리케이션의 호출 방향입니다.
 */
public interface LoadPostPort {
  /**
   * 카테고리 조건을 적용한 뒤 한 페이지를 ID 오름차순으로 반환합니다. 없으면 빈 목록입니다. 엔티티는 진행 중인 트랜잭션에서 관리될 수 있으므로 외부 응답은 별도 DTO로
   * 변환합니다.
   */
  List<Post> findAll(PostListQuery query);

  /** 해당 ID의 게시글을 반환하며, 없으면 Optional.empty()를 반환합니다. */
  Optional<Post> findById(long id);
}
