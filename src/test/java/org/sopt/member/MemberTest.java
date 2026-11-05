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
        assertThrows(MemberException.class, () -> new Member(name)).getErrorCode().getCode());
  }

  @Test
  void validatesNameAndPreservesValidText() {
    assertThrows(MemberException.class, () -> new Member("가".repeat(51)));
    assertThrows(MemberException.class, () -> new Member("앞\0뒤"));
    assertThrows(MemberException.class, () -> new Member("😀".repeat(26)));
    assertEquals("😀".repeat(25), new Member("😀".repeat(25)).getName());
    String name = " 한글😀\n\t\\u0000 ";
    assertEquals(name, new Member(name).getName());
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
