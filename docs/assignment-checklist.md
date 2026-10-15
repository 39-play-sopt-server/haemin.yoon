# 과제 요구사항 체크리스트

Spring Boot HTTP 서버 기준으로 구현 위치와 검증 방법을 정리합니다.
구조는 [아키텍처 문서](architecture.md), 실행과 HTTP 테스트 방법은 [서버 실행 및 검증](architecture.md#서버-실행-및-검증)을 참고하세요.

## 구현과 검증

| 요구사항 | 구현 위치와 동작 | 검증 |
| --- | --- | --- |
| 역할과 책임 분리 | domain·application·adapter와 입력·출력 포트, Command/Query 서비스 분리 | PostCommandQueryServiceTest, PostCommandQueryControllerTest |
| Spring 객체 관리 | Main의 컴포넌트 스캔, 서비스·저장소의 생성자 주입 | PostHttpApiTest의 실제 Spring 컨텍스트 |
| 게시글 작성·목록·단건·수정·삭제 | PostCommandController와 PostQueryController의 `/posts` HTTP 매핑 | PostHttpApiTest |
| 빈 제목·본문 금지 | @Valid 요청 DTO 검증 + Post의 생성·수정 검증 | PostCommandQueryServiceTest, PostCommandQueryControllerTest, PostHttpApiTest |
| 입력 길이 제한 | Post의 제목 200·본문 10,000·작성자 50, 도메인과 DTO가 동일 상한 적용 | PostLengthValidationTest, PostLengthHttpTest, ApiDocumentationTest |
| 필드별 입력 오류 | VALIDATION_FAILED와 정렬된 violations 목록, 입력값·내부 메시지 비노출 | PostHttpApiTest, GlobalExceptionHandlerTest |
| 필수 카테고리·작성자와 작성 시각 | Category와 Post, 요청·응답 DTO 변환 | 서비스·HTTP 테스트 |
| 목록 페이지네이션 | PostListQuery의 범위 검증, DB OFFSET/LIMIT, 기본 page=0·size=20, size 최대 100 | PostListingTest |
| 카테고리별 목록 | 선택적 category 필터링 후 페이지 조회, V3의 category·id 인덱스 | PostListingTest, FlywayMigrationTest |
| 동시 생성·상세 조회 | 기존 DB identity와 조회수 원자적 UPDATE·상세 트랜잭션 유지, 동시성 검증 보강 | PostCreationConcurrencyTest의 HTTP 생성 40건, PostViewCountTest의 상세 조회 40건 및 반환값 1~40 |
| 실제 PostgreSQL 검증 | 임시 컨테이너의 동시 생성·조회, 수정 교차 실행, 행별 잠금·롤백, Flyway 전환 | postgresTest의 PostgreSqlPostIntegrationTest |
| 오래된 편집·동시 수정 충돌 | 필수 예상 버전과 @Version, 충돌 시 409·CONFLICT, 조회수와 편집 버전 분리 | PostVersionHttpTest, PostgreSqlPostIntegrationTest, ErrorHttpIntegrationTest |
| 없는 게시글 예외 처리 | PostException과 POST_NOT_FOUND, HTTP 404 응답 | PostHttpApiTest |
| 잘못된 요청 형식 처리 | GlobalExceptionHandler의 JSON·경로 ID 변환 오류 처리 | PostHttpApiTest, GlobalExceptionHandlerTest |
| 공통 응답과 예외 처리 | BaseResponse의 다섯 필드, ErrorCode의 상태 선언, GlobalExceptionHandler의 MVC 응답 변환 | BaseResponseTest, GlobalExceptionHandlerTest, PostHttpApiTest |
| 서블릿 오류 응답 통일 | ApiErrorController가 필터 예외·sendError도 같은 JSON으로 반환 | ErrorHttpIntegrationTest의 실제 내장 서버 |
| 저장소 접근 분리 | LoadPostPort·SavePostPort와 PostPersistenceAdapter | InMemoryPostRepositoryTest, 서비스 테스트 |
| DB 저장과 자동 ID 생성 | Spring Data JPA의 IDENTITY와 PostgreSQL identity 컬럼, 생성·변경 감지·삭제 | PostDatabaseTest, PostHttpApiTest |
| 외부 클라이언트와 서버 분리 | Postman 등에서 HTTP 요청, 서버는 JSON 응답 | PostHttpApiTest, 아키텍처 문서의 HTTP 요청 순서 |

## 추가로 검증한 동작

- 생성·수정 검증 실패 시 기존 게시글 상태가 유지됩니다.
- 게시글 수정은 관리 엔티티를 변경합니다. 외부에 반환한 DTO 스냅샷은 엔티티와 분리되어 유지됩니다.
- Command와 Query는 Spring에서 같은 저장소 Bean을 주입받습니다.
- 수정·삭제 대상 조회에 실패하면 POST_NOT_FOUND를 반환하며 새 게시글을 삽입하지 않습니다.
- Command 서비스가 조회부터 변경까지 트랜잭션을 관리하고, 수정은 명시적인 save 없이 JPA 변경 감지로 커밋됩니다.
- 삭제 후에도 다른 게시글의 ID는 유지되고 발급된 ID는 재사용하지 않습니다.
- DTO와 도메인 카테고리는 명시적인 switch로 변환합니다.
- 실패 데이터나 성공 여부·코드가 모순되는 응답은 BaseResponse 생성자가 거부합니다.
- 성공 응답의 비어 있지 않은 검증 목록도 거부하고, 검증 목록은 불변 스냅샷으로 보관합니다.
- HTTP 필수값 오류는 VALIDATION_FAILED와 모든 필드 오류를 반환하고, HTTP 외 도메인 검증은 기존 오류 코드를 유지합니다.
- 검증 오류 목록은 중복 제거·정렬하며, 입력 원문·원본 검증 메시지·내부 객체명·제약명은 응답에 포함하지 않습니다.
- 잘못된 JSON·빈 본문·알 수 없는 카테고리는 INVALID_REQUEST와 빈 violations를 반환합니다.
- MVC의 404·405·406·415 응답과 Allow 헤더, 실제 HTTP의 429 응답과 Retry-After 헤더를 확인합니다.
- 예상하지 못한 오류는 서버에 기록하고 클라이언트에는 공통 메시지와 HTTP 500을 반환합니다.
- 빈 저장소의 목록 조회는 HTTP 200과 빈 배열을 반환합니다.
- DB가 저장 시 ID를 생성하고, 삭제 후에도 발급된 ID를 재사용하지 않습니다. 테스트용 메모리 저장소의 동시 저장도 검증합니다.

## 검증 방법

```sh
./gradlew test bootJar
./gradlew bootRun
```

Postman에서 작성 → 목록에서 실제 ID 확인 → 단건 조회 → 수정 → 삭제 → 같은 ID의 404를 확인합니다.
빈 제목·잘못된 JSON·잘못된 경로 ID의 400 응답도 확인합니다.
JSON 본문과 요청 URL은 [서버 실행 및 검증](architecture.md#서버-실행-및-검증)에 정리했습니다.

## 구현 범위의 한계

- PostgreSQL에 게시글과 identity 상태를 저장하므로 서버 재시작 후에도 유지됩니다. 일반 test는 H2, postgresTest는 임시 PostgreSQL 컨테이너를 사용합니다.
- 수정 요청은 읽었던 version을 필수로 전달합니다. 오래된 편집과 실제 동시 수정 충돌을 감지하며 자동 재시도하지 않습니다. DELETE는 클라이언트의 예상 버전을 받지 않으므로 오래된 삭제 의도 자체는 검사하지 않습니다.
- 조회 후 다른 요청이 삭제한 경우를 벌크 쿼리의 행 수로 판별하는 계약은 사용하지 않습니다.
- 저장 실패나 롤백에도 DB가 생성한 ID는 소비될 수 있으며 연속 번호를 보장하지 않습니다.
- 작성 시각은 실행 환경의 로컬 시간을 사용하며 Clock 주입은 적용하지 않았습니다.
- 성공 코드는 SUCCESS 고정이며 SuccessCode Enum, 중첩 error 객체, 외부 ProblemDetail 응답은 도입하지 않았습니다.
- requestId·traceId와 로그 연동, 인증·인가, 요청 횟수 제한 기능은 구현하지 않았습니다.
- offset 페이지네이션은 동시 삭제 시 페이지 사이 결과 이동을 허용하고 총 개수·총 페이지 수를 제공하지 않습니다.
- PostgreSQL의 동시성 정합성·행별 잠금·롤백을 자동 검증하지만, 운영 규모 부하·잠금 대기 측정은 별도 검증이 필요합니다.
- 필드별 검증 목록은 생성·수정 요청 본문에 적용합니다. 길이는 UTF-16 코드 단위이며 기존 DB의 대형 본문은 축약하지 않습니다. 경로 ID의 양수 제약은 추가하지 않았습니다.
