# 과제 요구사항 체크리스트

현재 코드의 필수·심화 요구사항과 구현·검증 위치를 정리합니다.
구조와 설계 이유는 [아키텍처 문서](architecture.md)를 참고하세요. 실행·검증 방법은 아래에 정리했습니다.

## 필수 과제

| 요구사항 | 상태 | 구현 위치와 동작 | 검증 |
| --- | --- | --- | --- |
| 역할과 책임을 분리하는 아키텍처 적용 | 완료 | domain·application·adapter를 구분하는 헥사고날 아키텍처. [PostUseCase](../src/main/java/org/sopt/post/application/port/in/PostUseCase.java)와 출력 포트로 외부 형식·저장 방식 분리 | [PostServiceTest](../src/test/java/org/sopt/post/application/service/PostServiceTest.java), [PostControllerTest](../src/test/java/org/sopt/post/adapter/in/api/PostControllerTest.java) 및 소스 의존성 검토 |
| 빈 제목·본문의 게시글 작성 금지 | 완료 | [Post](../src/main/java/org/sopt/post/domain/Post.java)에서 null과 isBlank()를 검증. 수정에도 같은 규칙 적용 | PostServiceTest의 생성·수정 검증, PostControllerTest의 오류 응답 |
| 필수 카테고리 Enum과 추가 필드 | 완료 | [Category](../src/main/java/org/sopt/post/domain/Category.java), Post의 author·createdAt·id. 카테고리와 작성자는 필수 | PostServiceTest의 카테고리·작성자 검증과 수정 시 메타데이터 유지, PostControllerTest의 결과 변환 |
| 존재하지 않는 게시글 등을 Exception으로 처리 | 완료 | [PostException](../src/main/java/org/sopt/post/domain/exception/PostException.java)과 [PostErrorCode](../src/main/java/org/sopt/post/domain/exception/PostErrorCode.java). 서비스가 예외를 던지고 서버 경계에서 실패 응답 생성 | PostServiceTest의 없는 ID 처리, PostControllerTest의 POST_NOT_FOUND 응답 |

## 심화 과제

| 요구사항 | 상태 | 구현 위치와 동작 | 검증 |
| --- | --- | --- | --- |
| 입출력·예외 처리·저장소 관리와 접근의 분리 | 완료 | [PostInput](../src/main/java/org/sopt/client/console/PostInput.java)과 [PostView](../src/main/java/org/sopt/client/console/PostView.java), [GlobalExceptionHandler](../src/main/java/org/sopt/global/exception/GlobalExceptionHandler.java), [PostRepositoryPort](../src/main/java/org/sopt/post/application/port/out/PostRepositoryPort.java)와 저장소 구현 | [PostConsoleClientTest](../src/test/java/org/sopt/client/console/PostConsoleClientTest.java), [GlobalExceptionHandlerTest](../src/test/java/org/sopt/global/exception/GlobalExceptionHandlerTest.java), [InMemoryPostRepositoryTest](../src/test/java/org/sopt/post/adapter/out/persistence/InMemoryPostRepositoryTest.java) |
| HashMap 저장소와 별도 ID 생성 로직 | 완료 | [InMemoryPostRepository](../src/main/java/org/sopt/post/adapter/out/persistence/InMemoryPostRepository.java)의 HashMap, [PostIdGeneratorPort](../src/main/java/org/sopt/post/application/port/out/PostIdGeneratorPort.java)와 [SequentialPostIdGenerator](../src/main/java/org/sopt/post/adapter/out/id/SequentialPostIdGenerator.java). 삭제 후에도 기존 ID 유지 | InMemoryPostRepositoryTest의 ID 정렬·중복 저장·없는 대상 교체, [SequentialPostIdGeneratorTest](../src/test/java/org/sopt/post/adapter/out/id/SequentialPostIdGeneratorTest.java)와 PostServiceTest의 ID 비재사용 |
| Main·View를 클라이언트로, 나머지 게시글 처리를 서버로 분리 | 완료 | [Main](../src/main/java/org/sopt/Main.java)과 client/console이 클라이언트. [PostController](../src/main/java/org/sopt/post/adapter/in/api/PostController.java)·application·domain·저장소가 서버. 같은 JVM에서 직접 호출 | PostConsoleClientTest의 실제 서버 컨트롤러를 통한 CRUD, 소스 의존성 검토 |
| 클라이언트가 처리할 공통 응답 객체 | 완료 | [BaseResponse](../src/main/java/org/sopt/global/response/BaseResponse.java)의 success·code·message·data. 모든 서버 컨트롤러 메서드가 반환 | [BaseResponseTest](../src/test/java/org/sopt/global/response/BaseResponseTest.java), GlobalExceptionHandlerTest, PostControllerTest, PostConsoleClientTest |

클라이언트·서버 분리는 과제에서 가정한 역할 분리로 구현했습니다. 실제 HTTP 서버는 포함하지 않습니다.
Main은 서버 구성 팩토리를 호출해 두 역할을 연결하지만 게시글 처리나 저장소에 직접 접근하지 않습니다.

## 추가로 검증한 동작

- 잘못된 숫자와 카테고리 번호는 재입력받습니다.
- 빈 저장소에서는 ID를 묻지 않으며, 없는 수정 대상에는 새 제목·본문을 묻지 않습니다.
- 생성·수정 입력 도중 EOF가 발생하면 미완성 요청 없이 정상 종료합니다.
- 단건 콘솔 조회는 같은 게시글을 한 번만 요청합니다.
- 불변 Post의 수정 결과는 서비스가 저장소에 반영하며 이전 조회 객체는 유지됩니다.
- DTO와 도메인 카테고리는 명시적인 switch로 변환합니다.
- 실패 응답에 데이터가 있거나 성공 여부와 코드가 모순되면 BaseResponse 생성자가 거부합니다.
- 예상하지 못한 서버 오류는 원인을 로그에 남기고 클라이언트에는 일반 오류 메시지를 전달합니다.

이 동작들은 위 테스트 클래스에서 확인할 수 있습니다. 포트와 서버·클라이언트의 의존성 방향은
소스 검토로 확인하며, 자동 아키텍처 검증 테스트는 따로 두지 않았습니다.

## 검증 방법

JDK 21 이상이 필요하며, 컴파일 대상은 Java 21이고 소스 인코딩은 UTF-8입니다.
프로젝트 루트에서 전체 빌드와 테스트를 실행합니다.

```sh
./gradlew clean build
```

수동으로 콘솔 흐름을 확인하려면 다음과 같이 실행합니다.

```sh
java -cp build/classes/java/main org.sopt.Main
```

1. 작성 메뉴에서 제목·본문·카테고리·작성자를 입력합니다.
2. 목록과 단건 조회에서 ID 및 메타데이터를 확인합니다.
3. 수정 메뉴에서 정상 값을 입력하고, 같은 ID의 제목·본문 변경을 확인합니다.
4. 빈 제목이나 본문으로 수정해 실패 안내와 기존 값 유지를 확인합니다.
5. 게시글을 삭제한 뒤 같은 ID 조회가 실패하는지 확인합니다.
6. 메뉴에서 숫자가 아닌 값을 입력해 재입력을 확인하고 6번으로 종료합니다.

## 구현 범위의 한계

- 영속 저장과 동시 요청 처리는 구현하지 않았습니다.
- 생성 검증에 실패해도 먼저 발급된 ID는 소비될 수 있으며 연속 번호를 보장하지 않습니다.
- 작성 시각은 실행 환경의 로컬 시간을 사용하며 Clock 주입은 적용하지 않았습니다.
- HTTP 라우팅·직렬화·상태 코드·프레임워크 전역 예외 처리는 후속 구현 범위입니다.
