package org.sopt.member.application.port.out;

import java.util.Optional;
import org.sopt.member.domain.Member;

public interface LoadMemberPort {
  /** 삭제된 회원은 조회 결과에서 제외합니다. */
  Optional<Member> findActiveById(long id);
}
