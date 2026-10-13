package org.sopt.post.adapter.in.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.sopt.post.adapter.in.web.dto.PostCategory;

/**
 * 클라이언트가 게시글 생성을 요청할 때 전달하는 입력 값입니다. ID와 작성 시각은 서버가 결정합니다. HTTP 입력의 필수값을 검증하고 도메인도 불변식을 검증합니다. 입력
 * 어댑터 안에 두어 애플리케이션이 외부 요청 형식에 의존하지 않게 합니다.
 */
public record CreatePostRequest(
    @NotBlank String title,
    @NotBlank String content,
    @NotNull PostCategory category,
    @NotBlank String author) {}
