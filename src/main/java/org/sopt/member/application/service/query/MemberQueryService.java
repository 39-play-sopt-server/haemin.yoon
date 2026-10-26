package org.sopt.member.application.service.query;

import lombok.RequiredArgsConstructor;
import org.sopt.member.application.port.in.query.MemberQueryUseCase;
import org.sopt.member.application.port.out.LoadMemberPort;
import org.sopt.member.domain.Member;
import org.sopt.member.domain.exception.MemberErrorCode;
import org.sopt.member.domain.exception.MemberException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 활성 회원 조회를 읽기 전용 트랜잭션으로 처리합니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberQueryService implements MemberQueryUseCase {
  private final LoadMemberPort loadMemberPort;

  @Override
  public Member getActiveMember(long id) {
    return loadMemberPort
        .findActiveById(id)
        .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
  }
}
