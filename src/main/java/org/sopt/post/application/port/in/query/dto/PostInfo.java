package org.sopt.post.application.port.in.query.dto;

import java.time.LocalDateTime;
import org.sopt.post.domain.Category;

/** 입력 어댑터에 공개하는 게시글의 불변 스냅샷입니다. */
public record PostInfo(
    long id,
    String title,
    String content,
    Category category,
    long authorId,
    String authorName,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    long viewCount,
    long version,
    long likeCount) {}
