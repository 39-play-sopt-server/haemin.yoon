package org.sopt.post.config;

import org.sopt.global.exception.GlobalExceptionHandler;
import org.sopt.post.adapter.in.api.PostCommandController;
import org.sopt.post.adapter.in.api.PostQueryController;
import org.sopt.post.adapter.out.id.SequentialPostIdGenerator;
import org.sopt.post.adapter.out.persistence.InMemoryPostRepository;
import org.sopt.post.application.service.command.PostCommandService;
import org.sopt.post.application.service.query.PostQueryService;

/**
 * 서버 객체를 생성하고 각 입력·출력 포트의 구현을 연결하는 구성 담당 클래스입니다.
 * Command와 Query를 구분하되 한 저장소를 주입해, 변경 결과를 조회에서 즉시 볼 수 있게 합니다.
 * 서비스가 구체 어댑터를 생성하지 않으므로 객체 조립과 게시글 처리의 책임이 분리됩니다.
 */
public final class PostServerConfiguration {
  private PostServerConfiguration() {}

  /**
   * 같은 저장소를 공유하는 컨트롤러 쌍을 반환합니다.
   * 호출마다 저장소·ID 발급기를 새로 생성하므로 다른 호출로 만든 서버 상태와는 독립적입니다.
   */
  public static Controllers createControllers() {
    var repository = new InMemoryPostRepository();
    var idGenerator = new SequentialPostIdGenerator();
    var handler = new GlobalExceptionHandler();
    var commands = new PostCommandService(repository, repository, idGenerator);
    var queries = new PostQueryService(repository);
    return new Controllers(new PostCommandController(commands, handler),
        new PostQueryController(queries, handler));
  }

  /**
   * Main이 서로 연결된 두 컨트롤러를 함께 받기 위한 구성 결과입니다.
   * 기능을 제공하는 API나 저장소가 아니며, 콘솔에는 각 컨트롤러를 개별 주입합니다.
   */
  public record Controllers(PostCommandController commandController, PostQueryController queryController) {}
}
