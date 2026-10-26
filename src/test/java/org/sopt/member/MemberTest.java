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
    assertFalse(member.isDeleted());
    memberCommands.deleteMember(1);
    assertTrue(member.isDeleted());
    assertEquals(1L, member.getId());
    assertEquals("작성자", member.getName());
    assertThrows(MemberException.class, () -> memberQueries.getActiveMember(1));
    assertThrows(MemberException.class, () -> memberCommands.deleteMember(1));
    assertThrows(PostException.class, () -> new Post("제목", "본문", Category.GENERAL, member));
    assertThrows(PostException.class, () -> new Post("제목", "본문", Category.GENERAL, null));
  }
}
