package org.sopt.post.adapter.in.api.dto.request;

/**
 * 수정 가능한 제목·본문만 전달하는 요청 값입니다. 대상 ID는 컨트롤러의 별도 인자로 전달합니다.
 * 생성 요청과 분리해 현재 수정 기능에서 허용하지 않는 카테고리·작성자 변경을 요청에 포함하지 않습니다.
 */
public record UpdatePostRequest(String title, String content) {}
