package org.sopt.post.application.port.in.command;

/** 트랜잭션에서 확정한 해당 회원의 상태와 게시글의 좋아요 수입니다. */
public record PostLikeResult(boolean liked, long likeCount) {}
