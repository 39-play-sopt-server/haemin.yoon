package org.sopt.post.adapter.in.web.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.sopt.post.domain.Post;

/** 수정할 제목·본문과 읽었던 버전을 전달합니다. version은 충돌 검사용이며 클라이언트가 저장 값을 지정하지 않습니다. */
public record UpdatePostRequest(
    @NotBlank @Size(max = Post.MAX_TITLE_LENGTH) @Schema(description = "변경할 제목", example = "수정한 제목")
        String title,
    @NotBlank
        @Size(max = Post.MAX_CONTENT_LENGTH)
        @Schema(description = "변경할 본문", example = "수정한 본문")
        String content,
    @NotNull @PositiveOrZero @Schema(description = "조회 응답에서 읽은 버전. 수정 충돌 검사에 사용", example = "0")
        Long version) {}
