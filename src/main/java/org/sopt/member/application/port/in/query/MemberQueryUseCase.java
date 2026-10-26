package org.sopt.member.application.port.in.query;

import org.sopt.member.domain.Member;

/** 다른 도메인도 활성 회원 확인에 사용할 수 있는 입력 포트입니다. */
public interface MemberQueryUseCase {
  Member getActiveMember(long id);
}
