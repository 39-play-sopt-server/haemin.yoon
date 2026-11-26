package org.sopt.member.application.port.in.dto;

/** 로그인 검증에만 사용하는 활성 회원의 자격 증명입니다. 응답이나 로그에 비밀번호 해시를 노출하지 않습니다. */
public record MemberCredential(long id, String passwordHash) {}
