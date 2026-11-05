package org.sopt.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** 엔티티의 공통 생성·수정 시각과 영속성 매핑을 관리합니다. */
@MappedSuperclass
public abstract class BaseEntity {
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt = LocalDateTime.now();

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt = createdAt;

  protected BaseEntity() {}

  /** DB의 마이크로초 정밀도에 맞춰 저장 전후 생성 시각을 일치시킵니다. */
  @PrePersist
  private void normalizeCreatedAt() {
    createdAt = createdAt.truncatedTo(ChronoUnit.MICROS);
    updatedAt = createdAt;
  }

  /** JPA가 변경된 엔티티를 DB에 반영할 때 수정 시각을 갱신합니다. 벌크 UPDATE에는 적용되지 않습니다. */
  @PreUpdate
  private void updateTimestamp() {
    updatedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }
}
