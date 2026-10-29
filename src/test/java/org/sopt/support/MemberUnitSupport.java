package org.sopt.support;

import java.util.HashMap;
import java.util.Optional;
import org.sopt.member.application.port.in.command.MemberCommandUseCase;
import org.sopt.member.application.port.in.query.MemberQueryUseCase;
import org.sopt.member.application.port.out.LoadMemberPort;
import org.sopt.member.application.port.out.SaveMemberPort;
import org.sopt.member.application.service.command.MemberCommandService;
import org.sopt.member.application.service.query.MemberQueryService;
import org.sopt.member.domain.Member;
import org.springframework.test.util.ReflectionTestUtils;

public abstract class MemberUnitSupport {
  private final Members repository = new Members();
  protected final MemberCommandUseCase memberCommands =
      new MemberCommandService(repository, repository);
  protected final MemberQueryUseCase memberQueries = new MemberQueryService(repository);

  protected MemberUnitSupport() {
    memberCommands.createMember("작성자");
  }

  protected long memberId(String name) {
    return memberCommands.createMember(name).id();
  }

  private static class Members implements LoadMemberPort, SaveMemberPort {
    private final HashMap<Long, Member> members = new HashMap<>();

    @Override
    public Optional<Member> findActiveById(long id) {
      return Optional.ofNullable(members.get(id)).filter(member -> !member.isDeleted());
    }

    @Override
    public Member save(Member member) {
      ReflectionTestUtils.setField(member, "id", (long) members.size() + 1);
      members.put(member.getId(), member);
      return member;
    }
  }
}
