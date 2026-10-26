package org.sopt.member.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.sopt.member.application.port.out.SaveMemberPort;
import org.sopt.member.domain.Member;
import org.sopt.support.MemberDatabaseSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
@ActiveProfiles("test")
class MemberDatabaseTest extends MemberDatabaseSupport {
  @Autowired SaveMemberPort writer;
  @Autowired JdbcTemplate jdbc;

  @Test
  void saveWithExistingIdDoesNotOverwriteExistingMember() {
    var original = memberCommands.createMember("기존 회원");
    var duplicate = new Member("덮어쓸 이름");
    ReflectionTestUtils.setField(duplicate, "id", original.getId());

    assertThrows(IllegalArgumentException.class, () -> writer.save(duplicate));

    assertEquals(
        "기존 회원",
        jdbc.queryForObject(
            "SELECT name FROM members WHERE id = ?", String.class, original.getId()));
    assertFalse(
        jdbc.queryForObject(
            "SELECT deleted FROM members WHERE id = ?", Boolean.class, original.getId()));
    assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM members", Integer.class));
  }

  @Test
  void saveWithUnknownAssignedIdDoesNotInsertMember() {
    var assigned = new Member("임의 ID 회원");
    ReflectionTestUtils.setField(assigned, "id", Long.MAX_VALUE);

    assertThrows(IllegalArgumentException.class, () -> writer.save(assigned));

    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM members", Integer.class));
  }
}
