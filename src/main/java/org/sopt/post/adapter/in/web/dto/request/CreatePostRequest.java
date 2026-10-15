package org.sopt.post.adapter.in.web.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.sopt.post.adapter.in.web.dto.PostCategory;
import org.sopt.post.domain.Post;

/**
 * 클라이언트가 게시글 생성을 요청할 때 전달하는 입력 값입니다. ID와 작성 시각은 서버가 결정합니다. HTTP 입력의 필수값을 검증하고 도메인도 불변식을 검증합니다. 입력
 * 어댑터 안에 두어 애플리케이션이 외부 요청 형식에 의존하지 않게 합니다.
 */
public record CreatePostRequest(
    @NotBlank @Size(max = Post.MAX_TITLE_LENGTH) @Schema(description = "제목", example = "첫 게시글")
        String title,
    @NotBlank
        @Size(max = Post.MAX_CONTENT_LENGTH)
        @Schema(description = "본문", example = "Scalar에서 API를 테스트합니다.")
        String content,
    @NotNull @Schema(description = "게시글 분류", example = "QUESTION") PostCategory category,
    @NotBlank @Size(max = Post.MAX_AUTHOR_LENGTH) @Schema(description = "작성자", example = "홍길동")
        String author) {}
