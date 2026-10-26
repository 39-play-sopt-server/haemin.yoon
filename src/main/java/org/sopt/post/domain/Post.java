package org.sopt.post.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.sopt.global.entity.BaseEntity;
import org.sopt.post.domain.exception.PostErrorCode;
import org.sopt.post.domain.exception.PostException;

/** 게시글의 JPA 매핑과 생성·수정 시 유효성 규칙을 관리합니다. */
@Entity
@Table(name = "posts")
public class Post extends BaseEntity {
  /** 입력 길이 상한입니다. DTO의 Size와 동일하게 UTF-16 코드 단위로 계산합니다. */
  public static final int MAX_TITLE_LENGTH = 200;

  public static final int MAX_CONTENT_LENGTH = 10_000;
  public static final int MAX_AUTHOR_LENGTH = 50;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, columnDefinition = "text")
  private String title;

  @Column(nullable = false, columnDefinition = "text")
  private String content;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private Category category;

  @Column(nullable = false, columnDefinition = "text")
  private String author;

  /** 조회수는 전용 출력 포트의 원자적 UPDATE로 변경하며 일반 엔티티 수정에서 제외합니다. */
  @Column(name = "view_count", nullable = false, updatable = false)
  private long viewCount;

  /** 편집 충돌을 감지하는 버전입니다. 값은 JPA가 관리하며 조회수 UPDATE에서는 변경하지 않습니다. */
  @Version
  @Column(nullable = false)
  private long version;

  /** JPA가 DB의 행을 엔티티로 복원할 때 사용합니다. */
  protected Post() {}

  /** 새 게시글은 ID 없이 생성하고, 저장 시 DB가 ID를 부여합니다. */
  public Post(String title, String content, Category category, String author) {
    validateTitle(title);
    validateContent(content);
    if (category == null) {
      throw new PostException(PostErrorCode.INVALID_CATEGORY);
    }
    if (author == null || author.isBlank()) {
      throw new PostException(PostErrorCode.INVALID_AUTHOR);
    }
    validateNoNul(author);
    if (author.length() > MAX_AUTHOR_LENGTH) {
      throw new PostException(PostErrorCode.AUTHOR_TOO_LONG);
    }
    this.title = title;
    this.content = content;
    this.category = category;
    this.author = author;
  }

  public Long getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public String getContent() {
    return content;
  }

  public Category getCategory() {
    return category;
  }

  public String getAuthor() {
    return author;
  }

  public long getViewCount() {
    return viewCount;
  }

  public long getVersion() {
    return version;
  }

  /** 모든 값을 검증한 뒤 관리 중인 엔티티를 변경합니다. 메타데이터는 유지합니다. */
  public void update(String title, String content) {
    validateTitle(title);
    validateContent(content);
    this.title = title;
    this.content = content;
  }

  /** null과 공백뿐인 제목을 거부하며, 유효한 텍스트의 앞뒤 공백은 변경하지 않습니다. */
  private static void validateTitle(String title) {
    if (title == null || title.isBlank()) {
      throw new PostException(PostErrorCode.INVALID_TITLE);
    }
    validateNoNul(title);
    if (title.length() > MAX_TITLE_LENGTH) {
      throw new PostException(PostErrorCode.TITLE_TOO_LONG);
    }
  }

  /** 생성과 수정에 동일한 본문 규칙을 적용해 수정으로 생성 검증을 우회하지 못하게 합니다. */
  private static void validateContent(String content) {
    if (content == null || content.isBlank()) {
      throw new PostException(PostErrorCode.INVALID_CONTENT);
    }
    validateNoNul(content);
    if (content.length() > MAX_CONTENT_LENGTH) {
      throw new PostException(PostErrorCode.CONTENT_TOO_LONG);
    }
  }

  /** PostgreSQL 텍스트에 저장할 수 없는 NUL을 거부하며 다른 문자나 공백은 변경하지 않습니다. */
  private static void validateNoNul(String text) {
    if (text.indexOf('\0') >= 0) {
      throw new PostException(PostErrorCode.NUL_CHARACTER_NOT_ALLOWED);
    }
  }
}
