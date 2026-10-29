package org.sopt.support;

import org.junit.jupiter.api.BeforeEach;
import org.sopt.member.application.port.in.command.MemberCommandUseCase;
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

  protected PostAuthor member(String name) {
    var member = memberCommands.createMember(name);
    return new PostAuthor(member.id(), member.name());
  }

  protected long memberId(String name) {
    return memberCommands.createMember(name).id();
  }
}
