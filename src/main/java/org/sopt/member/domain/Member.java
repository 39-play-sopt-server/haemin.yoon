package org.sopt.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.regex.Pattern;
import org.sopt.global.entity.BaseEntity;
import org.sopt.member.domain.exception.MemberErrorCode;
import org.sopt.member.domain.exception.MemberException;

/**
 * 회원의 이름, 로그인 이메일, 비밀번호 해시와 탈퇴 상태를 관리합니다. 탈퇴 후에도 게시글의 작성자 참조를 유지합니다.
 *
 * <p>이메일 도입 전에 생성된 회원은 이메일과 비밀번호 해시가 없어 로그인할 수 없습니다. 새 회원은 생성자에서 두 값을 필수로 검증합니다.
 */
@Entity
@Table(name = "members")
public class Member extends BaseEntity {
  public static final int MAX_NAME_LENGTH = 50;
  public static final int MAX_EMAIL_LENGTH = 254;
  private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, columnDefinition = "text")
  private String name;

  @Column(unique = true, columnDefinition = "text")
  private String email;

  @Column(name = "password_hash", columnDefinition = "text")
  private String passwordHash;

  @Column(nullable = false)
  private boolean deleted;

  protected Member() {}

  public Member(String name, String email, String passwordHash) {
    validateName(name);
    if (passwordHash == null || passwordHash.isBlank()) {
      throw new IllegalArgumentException("비밀번호 해시는 비어 있을 수 없습니다.");
    }
    this.name = name;
    this.email = normalizeEmail(email);
    this.passwordHash = passwordHash;
  }

  /** 대소문자만 다른 이메일을 같은 계정으로 취급하도록 저장·조회 전에 같은 형태로 정규화합니다. */
  public static String normalizeEmail(String email) {
    if (email == null) {
      throw new MemberException(MemberErrorCode.INVALID_MEMBER_EMAIL);
    }
    String normalized = email.strip().toLowerCase(Locale.ROOT);
    if (normalized.length() > MAX_EMAIL_LENGTH || !EMAIL.matcher(normalized).matches()) {
      throw new MemberException(MemberErrorCode.INVALID_MEMBER_EMAIL);
    }
    return normalized;
  }

  private static void validateName(String name) {
    if (name == null || name.isBlank()) {
      throw new MemberException(MemberErrorCode.INVALID_MEMBER_NAME);
    }
    if (name.indexOf('\0') >= 0) {
      throw new MemberException(MemberErrorCode.MEMBER_NAME_NUL_NOT_ALLOWED);
    }
    if (name.length() > MAX_NAME_LENGTH) {
      throw new MemberException(MemberErrorCode.MEMBER_NAME_TOO_LONG);
    }
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  /** 이메일 도입 전에 생성된 회원은 null입니다. */
  public String getEmail() {
    return email;
  }

  /** 이메일 도입 전에 생성된 회원은 null입니다. */
  public String getPasswordHash() {
    return passwordHash;
  }

  public boolean isDeleted() {
    return deleted;
  }

  public void delete() {
    deleted = true;
  }
}
