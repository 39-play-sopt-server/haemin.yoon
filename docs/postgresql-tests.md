# PostgreSQL 통합 테스트

일반 `test`는 기존 H2 기반 검증을 유지합니다. 별도 `postgresTest` 작업은
Testcontainers가 생성한 임시 PostgreSQL 18.2 컨테이너에서 실행합니다.
테스트 의존성 버전은 Spring Boot의 의존성 관리에 따릅니다.

## 실행

JDK 21과 실행 중인 Docker 엔진이 필요합니다.

```sh
./gradlew postgresTest
./gradlew test postgresTest spotlessCheck bootJar
```

최초 실행에는 PostgreSQL 및 컨테이너 정리용 이미지 다운로드가 필요할 수 있습니다.
Docker를 사용할 수 없으면 테스트는 실패하며, H2로 대체하거나 건너뛰지 않습니다.
`postgresTest`는 이전 실행 결과가 있어도 매번 새 컨테이너에서 실행합니다.
`bootRun`과 일반 `test`에는 Docker가 필요하지 않습니다.

컨테이너의 임의 포트·접속 정보를 Spring 테스트 속성으로 주입하므로
애플리케이션의 DB_URL이나 로컬·운영 DB는 사용하지 않습니다.
컨테이너는 테스트 종료 시 정리합니다. 테스트 DB에는 잠금 대기 10초,
SQL 실행 20초 제한을 적용해 잠금 오류로 테스트가 무한 대기하지 않게 합니다.

## 검증 계약

`src/postgresTest/java`의 `PostgreSqlPostIntegrationTest`에서 다음을 검증합니다.

- 빈 PostgreSQL에 Flyway V1~V4 적용과 JPA 매핑 검증.
- 작업자 8개의 동시 HTTP 생성 40건: 유실 없음, ID 고유성, 모든 제목 보존, 초기 조회수 0.
- 동시 HTTP 상세 조회 40건: 최종 조회수 40, 각 응답의 조회수가 정확히 1~40.
- 오래된 엔티티로 본문을 수정해도 다른 트랜잭션의 조회수 증가가 유지됨.
- 한 게시글의 행 잠금을 유지하는 중에도 다른 게시글의 상세 열람이 완료됨.
- 후속 처리 실패 시 상세 조회수 증가가 롤백됨.
- 별도 기존 스키마의 자동 baseline 거부, 명시적 baseline 1 후 기존 데이터 보존 및 V2~V4 적용.
- 같은 버전을 읽은 트랜잭션 두 개 중 하나만 수정 커밋 성공.
- 같은 버전의 동시 HTTP 수정 요청에서 200 하나와 409·CONFLICT 응답 검증.
- 실제 조회 직후 배리어로 두 HTTP 요청의 스냅샷을 맞춰 커밋 예외가 409로 전달됨을 검증.
- 기존 V3 본문·조회수 보존 및 V4의 초기 편집 버전 0 검증.

기존 스키마 전환 fixture는 현재 마이그레이션 SQL과 독립적으로 정의합니다.
임의 이름의 전환 전용 스키마는 검증 후 삭제합니다.

## 보고서와 확장

로컬 보고서는 `build/reports/tests/postgresTest/index.html`입니다.

회원 도메인 등에서 실제 DB 계약 검증이 필요하면 해당 도메인의 테스트를
`src/postgresTest/java`에 추가합니다. 운영 코드와 H2 테스트 설정은 수정하지 않습니다.
컨테이너 구성 중복이 실제로 발생하면 그때 공통 테스트 설정으로 분리합니다.
현재 테스트는 정합성 검증이며 처리량·응답 지연·대규모 잠금 경합의 부하 테스트는 아닙니다.

참고: [Testcontainers PostgreSQL 모듈](https://java.testcontainers.org/modules/databases/postgres/),
[Gradle JVM Test Suite](https://docs.gradle.org/current/userguide/jvm_test_suite_plugin.html).
