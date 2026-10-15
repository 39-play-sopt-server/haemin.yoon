# Scalar API 문서

## 구성과 선택 이유

springdoc-openapi가 컨트롤러와 요청·응답 DTO에서 OpenAPI 3.1 명세를 생성하고,
Scalar가 이를 읽어 API 설명과 요청 실행 화면을 제공한다.
Swagger UI 대신 Scalar를 사용해도 표준 OpenAPI 문서를 그대로 활용할 수 있다.

Spring Boot 4.0.8 프로젝트에
`org.springdoc:springdoc-openapi-starter-webmvc-scalar:3.1.1`을 적용했다.
Swagger UI 의존성을 별도로 추가하지 않는다.

## 실행과 접속

기존 DB는 [Flyway 전환 안내](flyway.md)의 baseline 등록을 먼저 완료한다.

```sh
./gradlew bootRun
```

- Scalar 화면: <http://localhost:8080/scalar>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>
- OpenAPI YAML: <http://localhost:8080/v3/api-docs.yaml>

포트를 변경했다면 해당 포트로 접속한다. Scalar JS는 Java 라이브러리에 포함되어
앱의 `/scalar/scalar.js`에서 제공한다. 기본 폰트는 외부 CDN을 사용할 수 있다.

## 화면 기본 설정

공유한 Scalar Configuration을 기준으로 `application.yml`의 `scalar`에서
Modern 레이아웃, Deep Space 테마, Operation ID 표시를 관리한다.
첫 태그·전체 태그·모델·응답·중첩 필드는 기본적으로 접힌 상태로 시작한다.
설정 파일에서 테마 enum은 `DEEP_SPACE`이며 브라우저에는 `deepSpace`로 전달된다.
JSON의 `orderSchemaPropertiesBy`는 Java 설정의 `schema-property-order`에 대응한다.
다크 모드는 기본으로 켠다(`dark-mode: true`). 전환 버튼은 기본 동작대로 표시한다.
라이브러리 기본값과 같은 옵션은 생략하고, 접속 경로와 의도적으로 변경한 설정만 명시한다.

현재 Java 통합 0.6.74가 직접 지원하지 않는 `showToolbar`, `expandAllParameters`,
`schemaKeyboardNav`, `externalUrls`는 YAML에 추가하지 않고 JS 기본 동작을 사용한다.
`default`, `isEditable`, `slug`, `title`은 Configure 화면의 문서 소스 관련 값으로
별도로 강제하지 않는다. API 제목은 OpenAPI 명세의 `info.title`에서 관리한다.
지원하지 않는 옵션을 이후 기본값과 다르게 바꿔야 한다면 Java 통합 지원 여부를
먼저 확인하고, 필요할 때 별도 HTML 진입점에서 런타임 설정을 제공한다.

기본값 변경은 앱 재시작 후 반영된다. 브라우저에서 별도로 바꾼 테마·다크 모드 등의
사용자 선택은 초기 설정과 다르게 보일 수 있다.

## 문서화 범위

| 메서드 | 경로 | 동작 | 성공 상태 |
| --- | --- | --- | --- |
| POST | `/posts` | 게시글 생성 | 201 |
| GET | `/posts` | page·size 페이지네이션과 선택적 category 필터, ID 오름차순 | 200 |
| GET | `/posts/{id}` | 단건 조회 | 200 |
| PUT | `/posts/{id}` | 제목·본문 수정 | 200 |
| DELETE | `/posts/{id}` | 게시글 삭제 | 200 |

`@Operation`, `@Tag`, `@Parameter`, `@Schema`로 API 설명과 필드 예시를 제공한다.
문서 대상은 프로젝트 루트 패키지 `org.sopt`의 컨트롤러다.
내부 오류 처리용 `ApiErrorController`는 `@Hidden`으로 명시적으로 제외한다.

- 생성 요청은 title, content, category, author가 필수다. 문자열은 공백만 입력할 수 없다.
- category는 GENERAL, QUESTION, INFORMATION 중 하나다.
- 목록 조회는 page=0, size=20이 기본값이며 size는 1~100이다. category를 생략하거나 비우면 전체 분류를 조회한다.
  카테고리 필터링 후 페이지를 나누며 마지막 페이지를 넘으면 빈 배열을 반환한다.
- 수정은 title과 content를 함께 전달하며 작성자·카테고리·작성 시각은 유지한다.
- 응답은 success, code, message, data, violations 필드를 갖는다.
- 목록의 data는 배열, 단건 조회는 객체, 생성·수정·삭제 및 오류 응답은 null이다.
- 잘못된 요청(400), 존재하지 않는 게시글(404), 서버 오류(500)의 공통 응답과 예시를 제공한다.
- createdAt은 서버 로컬 시각이며 타임존 정보가 없다.
- viewCount는 0부터 시작한다. 상세 조회 성공마다 1 증가한 값을 반환하며 목록 조회에서는 증가하지 않는다.
  같은 사용자의 반복 상세 조회도 매번 집계하고, 제목·본문 수정 시 조회수는 유지한다.
  HTTP 상세 조회는 Facade가 증가와 조회를 조율하며, 내부 Query 서비스의 단건 조회는 조회수를 변경하지 않는다.

## 문서 설정의 책임 분리

- `global/config/OpenApiConfig`: API 기본 정보, 공통 오류 스키마와 500 응답을 등록한다.
- `global/docs/OpenApiResponses`: `ErrorCode`와 `BaseResponse.failure()`를 사용해
  실제 응답 계약과 동일한 오류 예시를 생성한다.
- `post/adapter/in/web/docs/PostOpenApiConfig`: `OpenApiCustomizer`로
  게시글 전용 응답을 추가한다. 전역 설정은 게시글 설정에 의존하지 않는다.
  - `PostListBadRequest`: 목록의 page·size 범위 오류, 숫자·카테고리 형식 오류.
  - `InvalidPostId`: 단건 조회·삭제의 ID 형식 오류.
  - `PostCreateBadRequest`: 생성의 필수 필드 검증, JSON·카테고리 오류.
  - `PostUpdateBadRequest`: 수정의 제목·본문 검증, ID·JSON 오류.
  - `PostNotFound`: 존재하지 않는 게시글.
  생성·수정의 400에는 `ValidationFailed`, `InvalidRequest` 이름으로 각각 예시를 제공한다.
  목록의 400에는 `InvalidPagination`, `InvalidRequest` 예시를 제공한다. 각 컨트롤러는 자신에게 필요한 응답만 참조한다.
- 컨트롤러는 `#/components/responses/PostNotFound` 참조를 유지한다.

회원 도메인을 추가한다면 회원 웹 어댑터에 문서 설정을 두고 `UserNotFound`처럼
도메인을 포함한 키로 응답을 등록한다. 공통 헬퍼와 오류 스키마를 재사용하고,
전역 설정이나 게시글 전용 설정을 수정하지 않는다.
`springdoc.packages-to-scan`은 프로젝트 루트 패키지 `org.sopt`이므로,
이 아래에 새 도메인의 컨트롤러를 추가하면 `application.yml` 수정 없이 문서에 포함된다.
내부 처리용 컨트롤러·메서드는 `@Hidden`으로 문서 노출을 명시적으로 제외한다.
그룹별 명세를 도입한다면 각 그룹에도 도메인 customizer를 연결하는 구성을 별도로 검토한다.

## 요청 실행

Scalar에서 원하는 API를 열고 요청 실행 버튼을 선택한다.
생성 API의 예시 JSON으로 게시글을 만든 뒤 목록에서 ID를 확인하고,
그 ID로 단건 조회·수정·삭제를 실행할 수 있다.
화면에서 실행하는 요청은 연결된 DB의 실제 데이터를 변경한다.

## 검증

```sh
./gradlew test spotlessCheck bootJar
```

`ApiDocumentationTest`는 Scalar HTML의 명세 연결, 게시글 경로와 응답 상태,
필수 입력 필드, 공통 오류 응답 및 목록·단건 제네릭 스키마를 검증한다.
기존 HTTP 테스트로 실제 API 계약도 함께 검증한다.
`ApiDocumentationExtensionTest`는 게시글 패키지 밖의 새 컨트롤러가 설정 변경 없이
문서에 포함되고 내부 `/error` 경로는 제외되는지 검증한다.

참고: [springdoc Scalar 통합](https://springdoc.org/#_scalar_support),
[Scalar](https://scalar.com/)
