package org.sopt.member.application.port.out;

import org.sopt.member.domain.Member;

public interface SaveMemberPort {
  /** ID가 없는 신규 회원만 저장합니다. 기존 회원의 변경은 JPA 변경 감지로 반영합니다. */
  Member save(Member member);
}
