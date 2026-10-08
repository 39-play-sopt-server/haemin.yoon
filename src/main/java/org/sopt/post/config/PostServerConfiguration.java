package org.sopt.post.config;

import org.sopt.global.exception.GlobalExceptionHandler;
import org.sopt.post.adapter.in.api.PostController;
import org.sopt.post.adapter.out.id.SequentialPostIdGenerator;
import org.sopt.post.adapter.out.persistence.InMemoryPostRepository;
import org.sopt.post.application.service.PostService;

/**
 * 서버 객체를 생성하고 입력 포트·출력 포트의 구현을 연결하는 구성 담당 클래스입니다.
 * 서비스가 구체적인 어댑터를 직접 생성하지 않게 해 의존성 연결과 게시글 처리를 분리합니다.
 */
public final class PostServerConfiguration {
  private PostServerConfiguration() {}

  /** 호출마다 저장소와 ID 발급기를 새로 생성하므로 반환된 컨트롤러들은 서로 다른 서버 상태를 갖습니다. */
  public static PostController createController() {
    var repository = new InMemoryPostRepository();
    var idGenerator = new SequentialPostIdGenerator();
    var useCase = new PostService(repository, idGenerator);
    return new PostController(useCase, new GlobalExceptionHandler());
  }
}
