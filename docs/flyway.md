# Flyway로 스키마 변경 관리

## 도입 목적

Hibernate의 `ddl-auto: update` 대신 버전별 SQL을 Git에서 관리한다.
애플리케이션이 시작되면 Flyway가 미적용 SQL을 실행하고, Hibernate는
`ddl-auto: validate`로 엔티티 매핑을 검증한다.

- SQL 위치: `src/main/resources/db/migration`
- 초기 스키마: `V1__create_posts.sql`
- 조회수 추가: `V2__add_post_view_count.sql` (기존·신규 게시글 0, NOT NULL, 음수 금지)
- 카테고리별 페이지 조회 인덱스: `V3__add_post_category_id_index.sql` (`category, id`)
- 이력 테이블: `public.flyway_schema_history`
- 자동 baseline은 비활성화한다. 기존 DB의 최초 등록은 별도 명령으로 수행한다.
- `clean`은 비활성화한다.

## 빈 DB

PostgreSQL에 빈 DB를 생성한 후 실행한다. Flyway는 DB 자체를 생성하지 않는다.

```sh
export DB_URL='jdbc:postgresql://localhost:5432/assignment'
export DB_USERNAME="$(whoami)"
./gradlew bootRun
```

필요한 경우 `DB_PASSWORD`를 환경 변수로 제공한다. 비밀번호는 Git에 저장하지 않는다.
빈 `public` 스키마에서는 V1을 실행하고 SQL 이력을 기록한다.

## 기존 DB를 보존하는 전환

1. 기존 애플리케이션을 중지해 전환 중 스키마와 데이터가 바뀌지 않게 한다.
2. 대상 DB를 백업한다. 아래 명령의 host/user/database는 실제 환경에 맞춘다.
   백업 파일은 저장소 밖에 보관한다.

   ```sh
   pg_dump -h localhost -U "$(whoami)" -d assignment -Fc -f /tmp/assignment-before-flyway.dump
   ```

3. 대상 DB와 스키마를 확인한다.

   ```sh
   psql -h localhost -d assignment -c 'SELECT current_database(), current_schema();'
   psql -h localhost -d assignment -c '\d public.posts'
   psql -h localhost -d assignment -c 'SELECT count(*) FROM public.posts;'
   ```

   실제 컬럼 타입, NULL 제약, identity, PK가 V1과 같은지 확인한다.
   다른 테이블, 인덱스, 제약 등이 있다면 초기 SQL에 필요한 정의를 포함하고
   환경 간 차이를 먼저 해소한다. baseline은 SQL과 실제 스키마를 비교하지 않는다.
   Hibernate의 validate도 전체 스키마 비교 도구는 아니다.

4. DB_URL을 명시하고 **기존 DB에서 한 번만** baseline을 등록한다.

   ```sh
   export DB_URL='jdbc:postgresql://localhost:5432/assignment'
   export DB_USERNAME="$(whoami)"
   ./gradlew flywayBaseline
   ```

   이 Gradle 작업은 Spring Boot가 관리하는 Flyway CLI 버전으로
   `baseline -baselineVersion=1 -schemas=public`만 실행한다.
   기본 DB에 실수로 등록하지 않도록 DB_URL을 생략하면 실패한다.
   V1 DDL을 실행하지 않고 현재 스키마를 버전 1로 등록한다.
   **빈 DB에 baseline을 등록하면 V1이 생략되어 posts가 생성되지 않는다.**

5. 이력과 데이터 개수를 확인하고 앱을 시작한다.

   ```sh
   psql -h localhost -d assignment -c 'SELECT version, type, success FROM public.flyway_schema_history;'
   psql -h localhost -d assignment -c 'SELECT count(*) FROM public.posts;'
   ./gradlew bootRun
   ```

   이력에는 `version=1`, `type=BASELINE`, `success=true`가 있어야 한다.
   앱 시작 시 JPA 검증이 통과하고 게시글 조회가 동작하는지도 확인한다.
   여러 기존 DB가 있다면 각 DB의 구조를 확인하고 각각 등록한다.

## 이후 변경

조회수 추가는 `V2__add_post_view_count.sql`로 적용한다. 카테고리 조회 인덱스는 V3로 적용하며 다음 스키마 변경은 `V4__설명.sql`로 추가한다. 이미 적용한 파일은
수정하지 않고 새 버전을 추가한다. 기존 DB와 빈 DB 양쪽에서 변경 결과를 검증한다.
기존 baseline 1 DB에서는 V2와 V3, 빈 DB에서는 V1부터 V3까지 순서대로 실행된다.

## 검증

```sh
./gradlew test spotlessCheck bootJar
```

- 테스트 프로필도 `ddl-auto: validate`를 사용한다. Flyway가 테이블을 생성해야
  기존 게시글 저장·조회 테스트가 실행된다.
- `FlywayMigrationTest`는 기존 테이블의 자동 등록 거부와 명시적 baseline 후
  기존 게시글 보존, V1 생략, V2 적용 후 조회수 0과 V3 적용 이력을 H2 PostgreSQL 모드에서 검증한다.
- H2 검증은 실제 PostgreSQL 검증을 대체하지 않는다.

### 2026-10-13 로컬 검증 결과

- 전체 115개 테스트 통과, `spotlessCheck`와 `bootJar` 통과.
- 기존 PostgreSQL 18.6 `assignment` DB를 백업하고 실제 스키마를 확인한 뒤
  baseline 1을 명시적으로 등록했다. 이력은 `BASELINE / 1 / success=true`이며,
  전환 전후 게시글은 0건이었다. 데이터가 있는 경우의 보존은 자동 테스트로 검증했다.
- 기존 DB에서 실행 JAR를 웹 서버 없이 시작해 Flyway V1 생략과 JPA 검증 통과를 확인했다.
- 별도 임시 PostgreSQL DB에서 실행 JAR로 V1 자동 생성과 JPA 검증을 확인하고,
  게시글 INSERT와 identity ID 생성을 검증했다. INSERT는 롤백했고 임시 DB는 정리했다.

참고: [Flyway baseline](https://documentation.red-gate.com/flyway/reference/commands/baseline),
[Spring Boot DB 초기화](https://docs.spring.io/spring-boot/how-to/data-initialization.html)
