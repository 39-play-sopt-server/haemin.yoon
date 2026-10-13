package org.sopt.post.domain;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.sopt.post.domain.exception.PostErrorCode;
import org.sopt.post.domain.exception.PostException;

/** 게시글의 JPA 매핑과 생성·수정 시 유효성 규칙을 관리합니다. */
@Entity
@Table(name = "posts")
public class Post {
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

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  /** JPA가 DB의 행을 엔티티로 복원할 때 사용합니다. */
  protected Post() {}

  @PrePersist
  private void normalizeCreatedAt() {
    createdAt = createdAt.truncatedTo(ChronoUnit.MICROS);
  }

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
    this.title = title;
    this.content = content;
    this.category = category;
    this.author = author;
    this.createdAt = LocalDateTime.now();
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

  public LocalDateTime getCreatedAt() {
    return createdAt;
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
  }

  /** 생성과 수정에 동일한 본문 규칙을 적용해 수정으로 생성 검증을 우회하지 못하게 합니다. */
  private static void validateContent(String content) {
    if (content == null || content.isBlank()) {
      throw new PostException(PostErrorCode.INVALID_CONTENT);
    }
  }
}
