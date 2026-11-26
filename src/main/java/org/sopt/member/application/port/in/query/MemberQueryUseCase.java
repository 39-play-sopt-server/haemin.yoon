package org.sopt.member.application.port.in.query;

import java.util.List;
import java.util.Optional;
import org.sopt.member.application.port.in.dto.MemberCredential;
import org.sopt.member.application.port.in.dto.MemberInfo;

/** 다른 도메인도 활성 회원 확인에 사용할 수 있는 입력 포트입니다. */
public interface MemberQueryUseCase {
  MemberInfo getActiveMember(long id);

  /** 활성 회원의 자격 증명을 조회합니다. 형식이 잘못된 이메일도 가입 여부를 드러내지 않도록 빈 결과로 처리합니다. */
  Optional<MemberCredential> findActiveCredentialByEmail(String email);

  List<MemberInfo> listActive();
}
