package org.sopt.post.adapter.in.api.dto.response;

import java.time.LocalDateTime;
import org.sopt.post.adapter.in.api.dto.PostCategory;
import org.sopt.post.domain.Post;

/**
 * 클라이언트에 공개하는 게시글 조회 결과로, 내부 도메인 대신 전달하는 불변 스냅샷입니다.
 * 외부용 카테고리와 표시할 메타데이터를 포함하며, 이후 서버 수정·삭제로 이전 응답이 바뀌지 않습니다.
 */
public record PostResponse(long id, String title, String content, PostCategory category,
                           String author, LocalDateTime createdAt) {
  /**
   * 도메인의 값을 응답으로 복사합니다. 외부용 DTO가 도메인을 참조하므로 의존성은 내부를 향합니다.
   * 변환 규칙을 결과 DTO와 함께 두어 컨트롤러는 유스케이스 호출과 응답 처리 흐름에 집중합니다.
   */
  public static PostResponse from(Post post) {
    return new PostResponse(post.getId(), post.getTitle(), post.getContent(),
        PostCategory.from(post.getCategory()), post.getAuthor(), post.getCreatedAt());
  }
}
