package org.sopt.post.application.port.out;

import java.util.List;
import java.util.Optional;
import org.sopt.post.domain.Post;

/**
 * 애플리케이션이 외부 저장소에 요구하는 접근 계약인 출력 포트입니다.
 * 서비스를 HashMap 같은 저장 방식에서 분리하며, 구현을 교체해도 아래 반환 규칙을 유지해야 합니다.
 * out은 애플리케이션에서 저장소 기능을 호출하는 방향을 뜻합니다.
 */
public interface PostRepositoryPort {
  /** 새 ID의 게시글을 저장하며, 중복 ID는 기존 글을 덮어쓰지 않고 거부합니다. */
  void save(Post post);

  /**
   * 조회 시점의 게시글을 ID 오름차순으로 반환하며, 게시글이 없으면 빈 목록을 반환합니다.
   * 반환된 목록은 저장소와 독립적이고, 이후 저장소의 추가·수정·삭제로 바뀌지 않습니다.
   * 목록의 수정 가능 여부는 보장하지 않지만, 목록 변경이 저장소에 반영되지는 않습니다.
   * 요소인 Post는 불변 객체이며, 수정 결과를 저장하려면 update를 호출해야 합니다.
   */
  List<Post> findAll();

  /** 저장된 게시글이 없으면 true를 반환합니다. */
  boolean isEmpty();

  /**
   * 해당 ID의 불변 게시글을 반환하며, 없으면 Optional.empty()를 반환합니다.
   * 반환된 게시글은 이후 저장소 수정의 영향을 받지 않습니다.
   */
  Optional<Post> findById(long id);

  /** 같은 ID의 게시글을 교체하고 true를 반환하며, 대상이 없으면 저장하지 않고 false를 반환합니다. */
  boolean update(Post post);

  /** 해당 ID의 게시글을 삭제하고 true를 반환하며, 대상이 없으면 false를 반환합니다. */
  boolean deleteById(long id);
}
