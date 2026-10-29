package org.sopt.post.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import org.sopt.global.entity.BaseEntity;
import org.sopt.member.domain.Member;

/** 회원과 게시글의 다대다 관계를 표현합니다. 중복 여부는 DB의 유일 제약으로도 보장합니다. */
@Entity
@Table(
    name = "post_likes",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_post_likes_post_member",
            columnNames = {"post_id", "member_id"}))
public class PostLike extends BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "post_id", nullable = false, updatable = false)
  private Post post;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "member_id", nullable = false, updatable = false)
  private Member member;

  protected PostLike() {}

  public PostLike(Post post, Member member) {
    this.post = Objects.requireNonNull(post);
    this.member = Objects.requireNonNull(member);
  }
}
