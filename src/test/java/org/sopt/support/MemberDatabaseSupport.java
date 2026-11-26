package org.sopt.support;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.sopt.member.application.port.in.command.MemberCommandUseCase;
import org.sopt.member.application.port.in.dto.MemberInfo;
import org.sopt.post.domain.PostAuthor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

public abstract class MemberDatabaseSupport {
  @Autowired protected MemberCommandUseCase memberCommands;
  @Autowired private JdbcTemplate memberJdbc;

  @BeforeEach
  void prepareMembers() {
    memberJdbc.update("DELETE FROM posts");
    memberJdbc.update("DELETE FROM members");
    memberJdbc.execute("ALTER TABLE members ALTER COLUMN id RESTART WITH 2");
    memberJdbc.update(
        "INSERT INTO members (id, name, deleted, created_at, updated_at) OVERRIDING SYSTEM VALUE VALUES (1, '작성자', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
  }

  /** 회원가입 API를 거치지 않고 고유 이메일과 임의 비밀번호 해시로 회원을 등록합니다. */
  protected MemberInfo createMember(String name) {
    return memberCommands.registerMember(
        name, "member-" + UUID.randomUUID() + "@example.com", "{noop}test-password");
  }

  protected PostAuthor member(String name) {
    var member = createMember(name);
    return new PostAuthor(member.id(), member.name());
  }

  protected long memberId(String name) {
    return createMember(name).id();
  }
}
