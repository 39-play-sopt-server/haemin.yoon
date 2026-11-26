# 이메일·비밀번호 인증과 JWT

## 클라이언트 계약

| 요청 | 자격 증명과 결과 |
| --- | --- |
| `POST /auth/signup` | 이름·이메일·비밀번호로 가입합니다. 신규 회원의 권한은 `ROLE_USER`입니다. |
| `POST /auth/login` | 이메일·비밀번호를 검증한 뒤 Access Token을 `Authorization: Bearer …` 응답 헤더로, Refresh Token을 `refreshToken` 쿠키로 발급합니다. 기존 클라이언트용 본문에도 Access Token과 유효 시간(초)이 있습니다. |
| `POST /auth/refresh` | 쿠키와 `X-Refresh-Request: 1` 헤더로 두 토큰을 재발급합니다. 이전 Refresh Token은 즉시 만료됩니다. |
| `POST /auth/logout` | Access Token으로 현재 로그인 세션을 닫고 쿠키를 만료시킵니다. |
| `DELETE /members/{id}` | 본인 또는 관리자가 회원을 탈퇴시킵니다. 탈퇴가 커밋되면 모든 기기의 토큰이 인증·재발급 단계에서 거부됩니다. |
| `GET /members/me` | 로그인한 회원의 정보를 반환합니다. |
| `GET /members` | 관리자만 전체 활성 회원을 ID순으로 조회합니다. |

일반 API에는 Access Token을 요청 헤더로 전달합니다. 게시글 조회는 공개이며, 작성·좋아요는 본인 회원 ID로, 수정·삭제는 작성한 글에만 가능합니다. 관리자는 다른 회원의 글과 계정을 관리할 수 있습니다. 인증 실패는 401, 인증된 회원의 권한 부족은 403입니다.

쿠키는 `Secure; HttpOnly; SameSite=Strict; Path=/auth`입니다. 브라우저 클라이언트는 같은 사이트의 HTTPS 환경에서 사용합니다. 별도 Origin이면 서버 허용 목록에 등록하고 로그인·재발급 요청에 `credentials: "include"`를 지정합니다. 재발급 전용 헤더는 브라우저의 교차 Origin 요청에서 CORS preflight를 요구합니다.

## 세션과 권한 검증

Access Token은 HS256으로 서명하며 `sub`에 회원 ID, `sid`에 로그인 세션 ID, `jti`에 발급별 식별자, `token_use`에 access, `iat`와 `exp`에 발급·만료 시각을 담습니다. 기본 유효 시간은 30분입니다. 서명 키는 환경 변수 `JWT_SECRET`으로 설정합니다(`openssl rand -base64 32`).

Refresh Token은 256비트 난수입니다. DB에는 SHA-256 해시만 보관하고 재발급 시 기존 해시가 일치하는 행 하나를 조건부 갱신합니다. 세션 만료는 최초 로그인에서 14일로 고정합니다. 로그아웃은 해당 행을 삭제하며, 탈퇴는 회원의 비활성 상태로 모든 세션을 무효화합니다. 만료 행은 다음 로그인 때 정리합니다.

`AccessTokenIssuer`가 서명하고 `AccessTokenReader`가 서명·만료·용도와 식별자를 검증합니다. 인증 필터는 `SessionAuthority`로 활성 세션과 현재 회원 권한을 확인합니다. 이 구조는 역할 변경과 로그아웃을 다음 요청부터 반영하기 위해 Bearer 요청마다 DB 조회 한 번을 수행합니다.

새 관리자 API에는 `@AdminOnly`, 일반 회원과 관리자 공통 API에는 `@UserOnly`를 붙입니다. `@SelfOrAdmin`은 인자 이름이 `memberId`인 작업에서 본인 여부를 확인합니다. 게시글 소유권 판정은 게시글 웹 어댑터가 담당합니다. 운영 관리자는 신뢰된 DB 관리 경로에서 회원의 role을 ROLE_ADMIN으로 변경합니다.

## 회원 자격 증명

- 이메일은 앞뒤 공백을 제거하고 소문자로 저장합니다. 대소문자만 다른 이메일은 같은 계정입니다.
  탈퇴 회원의 이메일도 재사용할 수 없으며 중복은 409 `MEMBER_EMAIL_DUPLICATED`입니다.
  사전 확인 이후의 동시 가입도 DB 유일 제약(`uk_members_email`)으로 같은 오류가 됩니다.
- 비밀번호는 8자 이상, UTF-8 기준 72바이트 이하입니다. BCrypt는 72바이트 이후를 무시하므로
  더 긴 비밀번호는 400 `INVALID_PASSWORD`로 거부합니다. 저장 값은 BCrypt 해시(`password_hash`)입니다.
- 로그인 실패는 이메일이 없거나, 형식이 잘못되었거나, 비밀번호가 틀리거나, 탈퇴한 회원이어도
  모두 같은 401 `INVALID_CREDENTIALS` 응답입니다. 없는 이메일도 BCrypt 비교를 수행해 응답 시간 차이를 줄입니다.
- V12 이전 회원은 이메일과 비밀번호가 없어 기존 게시글은 유지되며 로그인할 수 없습니다.
