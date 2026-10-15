package org.sopt.post.adapter.in.web.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import org.sopt.post.adapter.in.web.dto.PostCategory;
import org.sopt.post.domain.Post;

/**
 * 클라이언트에 공개하는 게시글 조회 결과로, 내부 도메인 대신 전달하는 불변 스냅샷입니다. 외부용 카테고리와 표시할 메타데이터를 포함하며, 이후 서버 수정·삭제로 이전 응답이
 * 바뀌지 않습니다.
 */
public record PostResponse(
    @Schema(description = "게시글 ID", example = "1") long id,
    @Schema(description = "제목", example = "첫 게시글") String title,
    @Schema(description = "본문", example = "Scalar에서 API를 테스트합니다.") String content,
    @Schema(description = "게시글 분류", example = "QUESTION") PostCategory category,
    @Schema(description = "작성자", example = "홍길동") String author,
    @Schema(description = "서버 로컬 기준 작성 시각. 타임존 정보 없음", example = "2026-10-13T12:00:00")
        LocalDateTime createdAt,
    @Schema(description = "조회수. 상세 조회 성공마다 1 증가하며 목록 조회에서는 증가하지 않습니다.", example = "1")
        long viewCount,
    @Schema(description = "편집 버전. 수정 요청에 이 값을 전달하며 조회수 증가로는 바뀌지 않습니다.", example = "0")
        long version) {
  /**
   * 도메인의 값을 응답으로 복사합니다. 외부용 DTO가 도메인을 참조하므로 의존성은 내부를 향합니다. 변환 규칙을 결과 DTO와 함께 두어 컨트롤러는 유스케이스 호출과 응답
   * 처리 흐름에 집중합니다.
   */
  public static PostResponse from(Post post) {
    return new PostResponse(
        post.getId(),
        post.getTitle(),
        post.getContent(),
        PostCategory.from(post.getCategory()),
        post.getAuthor(),
        post.getCreatedAt(),
        post.getViewCount(),
        post.getVersion());
  }
}
