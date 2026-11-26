package org.sopt.member.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.sopt.member.application.port.out.SaveMemberPort;
import org.sopt.member.domain.Member;
import org.sopt.member.domain.exception.MemberException;
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
    var original = createMember("기존 회원");
    var duplicate = new Member("덮어쓸 이름", "assigned@example.com", "hash");
    ReflectionTestUtils.setField(duplicate, "id", original.id());

    assertThrows(IllegalArgumentException.class, () -> writer.save(duplicate));

    assertEquals(
        "기존 회원",
        jdbc.queryForObject("SELECT name FROM members WHERE id = ?", String.class, original.id()));
    assertFalse(
        jdbc.queryForObject(
            "SELECT deleted FROM members WHERE id = ?", Boolean.class, original.id()));
    assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM members", Integer.class));
  }

  @Test
  void savesNormalizedCredentialsAndRejectsConcurrentDuplicateEmailByConstraint() {
    var saved = memberCommands.registerMember("홍길동", " Hong@Example.com ", "hash");
    assertEquals(
        "hong@example.com",
        jdbc.queryForObject("SELECT email FROM members WHERE id = ?", String.class, saved.id()));
    assertEquals(
        "hash",
        jdbc.queryForObject(
            "SELECT password_hash FROM members WHERE id = ?", String.class, saved.id()));

    // 사전 중복 확인을 통과한 동시 가입도 DB 유일 제약으로 같은 오류가 됩니다.
    var exception =
        assertThrows(
            MemberException.class,
            () -> writer.save(new Member("동시 가입", "hong@example.com", "hash")));
    assertEquals("MEMBER_EMAIL_DUPLICATED", exception.getErrorCode().getCode());
  }

  @Test
  void saveWithUnknownAssignedIdDoesNotInsertMember() {
    var assigned = new Member("임의 ID 회원", "assigned@example.com", "hash");
    ReflectionTestUtils.setField(assigned, "id", Long.MAX_VALUE);

    assertThrows(IllegalArgumentException.class, () -> writer.save(assigned));

    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM members", Integer.class));
  }
}
