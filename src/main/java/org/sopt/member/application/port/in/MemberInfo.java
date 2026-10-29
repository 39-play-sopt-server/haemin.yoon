package org.sopt.member.application.port.in;

/** 회원 도메인이 외부에 공개하는 불변 스냅샷입니다. */
public record MemberInfo(long id, String name) {}
