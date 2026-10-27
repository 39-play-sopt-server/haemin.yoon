package org.sopt.post.adapter.out.persistence;

import java.util.List;
import org.sopt.post.application.port.in.query.PostListQuery;
import org.sopt.post.domain.Post;

/** Spring Data 저장소에 게시글 목록 전용 조건 조합을 연결하는 프래그먼트입니다. */
public interface PostListingRepository {
  List<Post> findPosts(PostListQuery query);
}
