package org.sopt.post.domain;

/**
 * 도메인에서 허용하는 게시글 분류를 정의해 임의 문자열이나 잘못된 분류가 사용되지 않게 합니다.
 * 외부용 PostCategory와는 별도 타입이며, 변환 책임은 입력 어댑터에 있습니다.
 */
public enum Category {
  GENERAL,
  QUESTION,
  INFORMATION;

}
