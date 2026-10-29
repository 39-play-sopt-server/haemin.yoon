# Flyway로 스키마 변경 관리

## 도입 목적

Hibernate의 `ddl-auto: update` 대신 버전별 SQL을 Git에서 관리한다.
애플리케이션이 시작되면 Flyway가 미적용 SQL을 실행하고, Hibernate는
`ddl-auto: validate`로 엔티티 매핑을 검증한다.

- SQL 위치: `src/main/resources/db/migration`
- 초기 스키마: `V1__create_posts.sql`
- 조회수 추가: `V2__add_post_view_count.sql` (기존·신규 게시글 0, NOT NULL, 음수 금지)
- 카테고리별 페이지 조회 인덱스: `V3__add_post_category_id_index.sql` (`category, id`)
- 편집 버전: `V4__add_post_version.sql` (기존·신규 게시글 0, NOT NULL, 음수 금지)
- 수정일: `V5__add_post_updated_at.sql` (기존 데이터는 생성일로 초기화)
- 회원·작성자 연결: `V6__create_members_and_link_posts.sql` ([전환 정책과 호환성](members.md))
- 최신순 목록 인덱스: `V7__add_post_latest_listing_indexes.sql` (생성일·ID 내림차순, 전체·카테고리별 조회 지원)
- PostgreSQL 제목 검색: `db/postgresql/V8__add_post_title_trigram_index.sql` (pg_trgm·GIN)
- 작성자 검색: `V9__add_post_author_listing_indexes.sql` (작성자별·작성자/카테고리별 최신순 복합 인덱스)
- 좋아요: `V10__create_post_likes.sql` (다대다 연결·중복 방지·삭제 cascade, 기존·신규 글의 초기 like_count=0·음수 금지)
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

조회수 추가는 V2, 카테고리 조회 인덱스는 V3, 편집 버전은 V4, 수정일은 V5, 회원·작성자 연결은 V6,
최신순 목록 인덱스는 V7, 제목 검색 인덱스는 V8, 작성자 목록 인덱스는 V9, 좋아요는 V10으로 적용한다.
다음 스키마 변경은 `V11__설명.sql`로 추가한다. 이미 적용한 파일은
수정하지 않고 새 버전을 추가한다. 기존 DB와 빈 DB 양쪽에서 변경 결과를 검증한다.
기존 baseline 1 DB에서는 V2~V10, 빈 DB에서는 V1~V10까지 순서대로 실행된다.
V4는 기존 본문과 조회수를 보존한다. 구버전 쓰기 서버와의 공존 및 클라이언트 전환 조건은
[편집 충돌 처리](post-edit-conflicts.md#스키마와-배포)를 참고한다.

## 검증

```sh
./gradlew test spotlessCheck bootJar
```

- 테스트 프로필도 `ddl-auto: validate`를 사용한다. Flyway가 테이블을 생성해야
  기존 게시글 저장·조회 테스트가 실행된다.
- `FlywayMigrationTest`는 기존 테이블의 자동 등록 거부와 명시적 baseline 후
  기존 게시글 보존, V1 생략, V2 적용 후 조회수 0과 V3~V10 적용 이력·편집 버전 0·좋아요 수 0을 H2 PostgreSQL 모드에서 검증한다.
- H2 검증은 실제 PostgreSQL 검증을 대체하지 않는다.
- `./gradlew postgresTest`는 임시 PostgreSQL에서 빈 DB 마이그레이션과 명시적 baseline 이후
  기존 데이터 보존·V2~V10 적용·V3 데이터의 V4 전환·재실행 시 추가 적용 없음까지 검증한다.
  실행 환경과 검증 범위는 [PostgreSQL 통합 테스트](postgresql-tests.md)를 참고한다.

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

## PostgreSQL 전용 인덱스와 테스트 환경

운영과 postgresTest는 `classpath:db/migration,classpath:db/postgresql`을 함께 사용합니다.
H2 일반 테스트는 `classpath:db/migration,classpath:db/h2`로 실행하고 V8은 명시적 무작업 마이그레이션입니다.
H2가 GIN을 흉내 내는 인덱스를 만들지는 않습니다. 제목 검색 성능 계약은 실제 PostgreSQL에서 검증합니다.
새 마이그레이션은 DB를 구분하더라도 전역 버전 번호를 중복 사용하지 않습니다(같은 V8의 DB별 대체본은 예외).
V1~V7 파일은 수정하지 않습니다.

V8은 pg_trgm을 public 스키마에 설치하고 public.gin_trgm_ops를 사용합니다.
DB 실행 계정에는 확장 설치에 필요한 CREATE 권한이 있어야 하며, 확장을 별도 관리한다면
배포 전에 public 스키마의 pg_trgm 설치를 준비합니다. 인덱스는 일반 CREATE INDEX이므로
큰 기존 테이블에서 적용할 때 쓰기 잠금·생성 시간을 고려해 배포 시간을 정해야 합니다.
현재 실행 DB에 직접 마이그레이션하지 않았으며 애플리케이션 시작 시 Flyway가 적용합니다.
