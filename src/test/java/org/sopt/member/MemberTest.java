package org.sopt.member;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sopt.member.domain.Member;
import org.sopt.member.domain.exception.MemberException;
import org.sopt.post.domain.Category;
import org.sopt.post.domain.Post;
import org.sopt.post.domain.exception.PostException;
import org.sopt.support.MemberUnitSupport;

class MemberTest extends MemberUnitSupport {
  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t", "\n", "　"})
  void rejectsBlankName(String name) {
    assertEquals(
        "INVALID_MEMBER_NAME",
        assertThrows(MemberException.class, () -> member(name)).getErrorCode().getCode());
  }

  @Test
  void validatesNameAndPreservesValidText() {
    assertThrows(MemberException.class, () -> member("가".repeat(51)));
    assertThrows(MemberException.class, () -> member("앞\0뒤"));
    assertThrows(MemberException.class, () -> member("😀".repeat(26)));
    assertEquals("😀".repeat(25), member("😀".repeat(25)).getName());
    String name = " 한글😀\n\t\\u0000 ";
    assertEquals(name, member(name).getName());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "hong", "hong@", "@example.com", "hong@example", "a b@example.com"})
  void rejectsInvalidEmail(String email) {
    assertEquals(
        "INVALID_MEMBER_EMAIL",
        assertThrows(MemberException.class, () -> new Member("홍길동", email, "hash"))
            .getErrorCode()
            .getCode());
  }

  @Test
  void normalizesEmailAndRequiresPasswordHash() {
    assertEquals("hong@example.com", new Member("홍길동", "  Hong@Example.COM ", "hash").getEmail());
    assertThrows(IllegalArgumentException.class, () -> new Member("홍길동", "hong@example.com", " "));
    assertThrows(
        MemberException.class, () -> new Member("홍길동", "a".repeat(243) + "@example.com", "hash"));
  }

  @Test
  void rejectsDuplicateEmailIgnoringCaseIncludingDeletedMembers() {
    var member = memberCommands.registerMember("홍길동", "hong@example.com", "hash");
    memberCommands.deleteMember(member.id());
    assertEquals(
        "MEMBER_EMAIL_DUPLICATED",
        assertThrows(
                MemberException.class,
                () -> memberCommands.registerMember("다른 이름", "HONG@example.com", "hash"))
            .getErrorCode()
            .getCode());
    assertTrue(memberQueries.findActiveCredentialByEmail("hong@example.com").isEmpty());
    assertTrue(memberQueries.findActiveCredentialByEmail("not-an-email").isEmpty());
  }

  private static Member member(String name) {
    return new Member(name, "member@example.com", "hash");
  }

  @Test
  void deletionPreservesIdentityAndNameButPreventsActiveLookupAndNewPosts() {
    var member = memberQueries.getActiveMember(1);
    memberCommands.deleteMember(1);
    assertEquals(1L, member.id());
    assertEquals("작성자", member.name());
    assertThrows(MemberException.class, () -> memberQueries.getActiveMember(1));
    assertThrows(MemberException.class, () -> memberCommands.deleteMember(1));
    var posts = new org.sopt.post.adapter.out.persistence.InMemoryPostRepository();
    assertThrows(
        MemberException.class,
        () ->
            new org.sopt.post.application.service.command.PostCommandService(
                    posts, posts, memberQueries)
                .createPost("제목", "본문", Category.GENERAL, member.id()));
    assertThrows(PostException.class, () -> new Post("제목", "본문", Category.GENERAL, null));
  }
}
