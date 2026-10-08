package org.sopt.post.adapter.in.api.dto.request;

import org.sopt.post.adapter.in.api.dto.PostCategory;

/**
 * 클라이언트가 게시글 생성을 요청할 때 전달하는 입력 값입니다.
 * ID와 작성 시각은 서버가 결정하며, 게시글 유효성 검증은 DTO가 아닌 도메인에서 수행합니다.
 * 입력 어댑터 안에 두어 애플리케이션이 외부 요청 형식에 의존하지 않게 합니다.
 */
public record CreatePostRequest(String title, String content, PostCategory category, String author) {}
