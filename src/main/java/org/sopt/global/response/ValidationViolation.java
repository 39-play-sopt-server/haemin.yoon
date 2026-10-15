package org.sopt.global.response;

import org.jspecify.annotations.Nullable;

/** 공개 입력 필드와 검증 사유만 전달합니다. 객체 전체의 오류는 field가 null입니다. */
public record ValidationViolation(@Nullable String field, String code, String message) {}
