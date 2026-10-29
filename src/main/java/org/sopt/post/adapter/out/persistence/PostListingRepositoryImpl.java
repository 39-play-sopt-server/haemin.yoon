package org.sopt.post.adapter.out.persistence;

import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.sopt.post.application.port.out.PostListCriteria;
import org.sopt.post.domain.Post;

/** 사용자 입력은 파라미터로 바인딩하고 실제로 전달된 검색 조건만 쿼리에 추가합니다. */
@RequiredArgsConstructor
public class PostListingRepositoryImpl implements PostListingRepository {
  private final EntityManager entityManager;

  @Override
  public List<Post> findPosts(PostListCriteria query) {
    var predicates = new ArrayList<String>();
    if (query.category() != null) predicates.add("p.category = :category");
    if (query.authorId() != null) predicates.add("p.authorMemberId = :authorId");
    if (query.keyword() != null) predicates.add("p.title ILIKE :titlePattern ESCAPE '!'");
    if (query.cursorId() != null) predicates.add("(p.createdAt, p.id) < (:createdAt, :id)");

    String hql =
        "SELECT p FROM Post p"
            + (predicates.isEmpty() ? "" : " WHERE " + String.join(" AND ", predicates))
            + " ORDER BY p.createdAt DESC, p.id DESC";
    var listing = entityManager.createQuery(hql, Post.class).setMaxResults(query.size() + 1);
    if (query.category() != null) listing.setParameter("category", query.category());
    if (query.authorId() != null) listing.setParameter("authorId", query.authorId());
    if (query.keyword() != null)
      listing.setParameter("titlePattern", containsPattern(query.keyword()));
    if (query.cursorId() != null) {
      listing.setParameter("createdAt", query.cursorCreatedAt());
      listing.setParameter("id", query.cursorId());
    }
    return listing.getResultList();
  }

  /** LIKE의 특수 문자는 사용자가 입력한 문자 그대로 검색합니다. */
  private String containsPattern(String keyword) {
    return "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
  }
}
