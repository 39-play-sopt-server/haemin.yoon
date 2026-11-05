package org.sopt.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.sopt.global.entity.BaseEntity;
import org.sopt.member.domain.exception.MemberErrorCode;
import org.sopt.member.domain.exception.MemberException;

/** 회원의 이름과 탈퇴 상태를 관리합니다. 탈퇴 후에도 게시글의 작성자 참조를 유지합니다. */
@Entity
@Table(name = "members")
public class Member extends BaseEntity {
  public static final int MAX_NAME_LENGTH = 50;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, columnDefinition = "text")
  private String name;

  @Column(nullable = false)
  private boolean deleted;

  protected Member() {}

  public Member(String name) {
    if (name == null || name.isBlank()) {
      throw new MemberException(MemberErrorCode.INVALID_MEMBER_NAME);
    }
    if (name.indexOf('\0') >= 0) {
      throw new MemberException(MemberErrorCode.MEMBER_NAME_NUL_NOT_ALLOWED);
    }
    if (name.length() > MAX_NAME_LENGTH) {
      throw new MemberException(MemberErrorCode.MEMBER_NAME_TOO_LONG);
    }
    this.name = name;
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public boolean isDeleted() {
    return deleted;
  }

  public void delete() {
    deleted = true;
  }
}
