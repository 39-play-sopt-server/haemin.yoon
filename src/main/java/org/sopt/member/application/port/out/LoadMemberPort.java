package org.sopt.member.application.port.out;

import java.util.List;
import java.util.Optional;
import org.sopt.member.domain.Member;

public interface LoadMemberPort {
  /** 삭제된 회원은 조회 결과에서 제외합니다. */
  Optional<Member> findActiveById(long id);

  /** 정규화된 이메일로 조회하며 삭제된 회원은 제외합니다. */
  Optional<Member> findActiveByEmail(String normalizedEmail);

  /** 탈퇴 회원의 이메일도 재사용하지 않도록 삭제 여부와 관계없이 확인합니다. */
  boolean existsByEmail(String normalizedEmail);

  List<Member> listActive();
}
