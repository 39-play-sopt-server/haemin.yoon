package org.sopt.post.application.port.out.dto;

import java.time.LocalDateTime;
import org.sopt.post.domain.Category;

/** 저장소가 목록을 검색하는 데 필요한 조건만 담은 출력 포트 계약입니다. */
public record PostListCriteria(
    LocalDateTime cursorCreatedAt,
    Long cursorId,
    int size,
    Category category,
    String keyword,
    Long authorId) {}
