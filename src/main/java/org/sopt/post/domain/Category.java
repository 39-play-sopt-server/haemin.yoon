package org.sopt.post.domain;

/**
 * 도메인에서 허용하는 게시글 분류를 정의해 임의 문자열이나 잘못된 분류가 사용되지 않게 합니다.
 * 한글 표시명은 분류의 의미를 설명하는 명칭으로 유지합니다.
 * 외부용 PostCategory와는 별도 타입이며, 변환 책임은 입력 어댑터에 있습니다.
 */
public enum Category {
  GENERAL("일반"),
  QUESTION("질문"),
  INFORMATION("정보");

  private final String displayName;

  Category(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }
}
