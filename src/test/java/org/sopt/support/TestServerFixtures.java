package org.sopt.support;

import org.sopt.global.exception.GlobalExceptionHandler;
import org.sopt.global.exception.BaseException;
import org.sopt.global.exception.GlobalErrorCode;
import org.sopt.post.adapter.out.id.SequentialPostIdGenerator;
import org.sopt.post.domain.Post;
import org.sopt.post.adapter.in.api.PostController;
import org.sopt.post.adapter.out.persistence.InMemoryPostRepository;
import org.sopt.post.application.service.PostService;

public final class TestServerFixtures {
  private TestServerFixtures() {}

  public static PostController withFailingExistenceCheck() {
    var service = new PostService(new InMemoryPostRepository(), new SequentialPostIdGenerator()) {
      @Override
      public boolean hasPosts() {
        throw new BaseException(GlobalErrorCode.INTERNAL_SERVER_ERROR);
      }
    };
    return new PostController(service, new GlobalExceptionHandler());
  }

  public static PostController withFailingRead() {
    var service = new PostService(new InMemoryPostRepository(), new SequentialPostIdGenerator()) {
      @Override
      public Post getPost(long id) {
        throw new BaseException(GlobalErrorCode.INTERNAL_SERVER_ERROR);
      }
    };
    return new PostController(service, new GlobalExceptionHandler());
  }

  public static PostController withFixedId(long id) {
    return new PostController(new PostService(new InMemoryPostRepository(), () -> id),
        new GlobalExceptionHandler());
  }
}
