package org.sopt.post.adapter.in.api.dto;

import org.sopt.post.domain.Category;

/**
 * 외부 요청·응답에서 사용하는 카테고리와 콘솔 표시명을 정의합니다.
 * 도메인 Category와 분리해 내부 분류를 외부에 직접 노출하지 않고 명시적인 변환 메서드로 매핑합니다.
 */
public enum PostCategory {
  GENERAL("일반"),
  QUESTION("질문"),
  INFORMATION("정보");

  private final String displayName;

  PostCategory(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }

  /**
   * 요청 분류를 도메인 분류로 변환합니다. 두 Enum의 이름 일치에 의존하지 않고 대응 관계를 명시합니다.
   * default 없는 switch이므로 분류 추가 시 빠진 매핑을 컴파일 단계에서 확인할 수 있습니다.
   */
  public Category toDomain() {
    return switch (this) {
      case GENERAL -> Category.GENERAL;
      case QUESTION -> Category.QUESTION;
      case INFORMATION -> Category.INFORMATION;
    };
  }

  /** 도메인 분류를 외부 응답 분류로 변환하며, 두 Enum의 변경 지점을 분리합니다. */
  public static PostCategory from(Category category) {
    return switch (category) {
      case GENERAL -> GENERAL;
      case QUESTION -> QUESTION;
      case INFORMATION -> INFORMATION;
    };
  }
}
