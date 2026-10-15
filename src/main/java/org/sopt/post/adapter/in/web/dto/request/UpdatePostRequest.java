package org.sopt.post.adapter.in.web.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.sopt.post.domain.Post;

/**
 * 수정 가능한 제목·본문만 전달하는 요청 값입니다. 대상 ID는 컨트롤러의 별도 인자로 전달합니다. 생성 요청과 분리해 현재 수정 기능에서 허용하지 않는 카테고리·작성자 변경을
 * 요청에 포함하지 않습니다.
 */
public record UpdatePostRequest(
    @NotBlank @Size(max = Post.MAX_TITLE_LENGTH) @Schema(description = "변경할 제목", example = "수정한 제목")
        String title,
    @NotBlank
        @Size(max = Post.MAX_CONTENT_LENGTH)
        @Schema(description = "변경할 본문", example = "수정한 본문")
        String content) {}
