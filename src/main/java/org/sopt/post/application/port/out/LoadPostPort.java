package org.sopt.post.application.port.out;

import java.util.List;
import java.util.Optional;
import org.sopt.post.domain.Post;

/**
 * 애플리케이션이 저장소에 요구하는 조회 전용 출력 포트입니다.
 * Query 서비스에 이 계약만 주입하면 저장·수정·삭제 기능을 호출할 수 없습니다.
 * Command 서비스도 수정 대상을 읽기 위해 사용할 수 있으며, out은 애플리케이션의 호출 방향입니다.
 */
public interface LoadPostPort {
  /**
   * 조회 시점의 게시글을 ID 오름차순으로 반환하며, 없으면 빈 목록을 반환합니다.
   * 반환 목록과 불변 Post는 이후 저장소 변경으로 바뀌지 않습니다.
   * 목록의 수정 가능 여부는 보장하지 않지만, 목록 변경은 저장소에 반영되지 않습니다.
   */
  List<Post> findAll();

  /** 저장된 게시글이 없으면 true를 반환합니다. */
  boolean isEmpty();

  /** 해당 ID의 불변 게시글을 반환하며, 없으면 Optional.empty()를 반환합니다. */
  Optional<Post> findById(long id);
}
