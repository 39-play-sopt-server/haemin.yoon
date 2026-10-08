package org.sopt.post.domain;

import java.time.LocalDateTime;
import org.sopt.post.domain.exception.PostErrorCode;
import org.sopt.post.domain.exception.PostException;

/**
 * 게시글의 상태와 유효성 규칙을 함께 관리하는 불변 도메인 객체입니다.
 * 모든 필드를 final로 두고 상속을 막아, 조회한 객체를 통해 저장소 상태를 바꿀 수 없게 합니다.
 * 생성·수정 경로 모두 동일한 검증을 거치므로 유효하지 않은 게시글 상태가 만들어지지 않습니다.
 */
public final class Post {
  private final long id;
  private final String title;
  private final String content;
  private final Category category;
  private final String author;
  private final LocalDateTime createdAt;

  /** 새 게시글의 작성 시각은 실행 환경의 로컬 현재 시각으로 기록하며, ID는 외부 발급 값을 사용합니다. */
  public Post(long id, String title, String content, Category category, String author) {
    this(id, title, content, category, author, LocalDateTime.now());
  }

  /** 생성과 수정이 같은 검증을 사용하고, 수정에서는 원래 작성 시각을 유지하도록 내부 생성자를 둡니다. */
  private Post(long id, String title, String content, Category category, String author,
      LocalDateTime createdAt) {
    validateTitle(title);
    validateContent(content);
    if (category == null) {
      throw new PostException(PostErrorCode.INVALID_CATEGORY);
    }
    if (author == null || author.isBlank()) {
      throw new PostException(PostErrorCode.INVALID_AUTHOR);
    }
    this.id = id;
    this.title = title;
    this.content = content;
    this.category = category;
    this.author = author;
    this.createdAt = createdAt;
  }

  public long getId() {
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

  /**
   * 기존 객체를 유지하고, 변경할 제목과 본문을 검증한 새 게시글을 반환합니다.
   * ID·카테고리·작성자·작성 시각은 유지하며, 반환값을 저장소에 반영해야 실제 수정이 완료됩니다.
   * 어느 검증에서든 실패하면 새 객체를 반환하지 않으므로 제목만 변경되는 부분 수정도 없습니다.
   */
  public Post update(String title, String content) {
    return new Post(id, title, content, category, author, createdAt);
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
