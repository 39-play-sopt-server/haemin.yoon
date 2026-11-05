package org.sopt.member.adapter.out.persistence;

import java.util.Optional;
import org.sopt.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {
  Optional<Member> findByIdAndDeletedFalse(long id);
}
