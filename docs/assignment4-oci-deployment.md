# 4주차 과제: OCI 인스턴스 + GitHub Actions 자동 배포

목표는 OCI Console에서 Ubuntu 인스턴스를 한 번 만든 뒤, 서버에 직접 접속하지 않고
`feat/11/assignment4` 브랜치 push만으로 배포하는 것입니다.

최종 제출 주소는 `https://lemonham.p-e.kr/scalar`입니다.

## 전체 순서

```text
OCI SSH key pair 다운로드
→ OCI Ubuntu 인스턴스 생성
→ OCI 22/80/443 ingress 설정
→ lemonham.p-e.kr A 레코드 연결
→ GitHub Secrets 등록
→ feat/11/assignment4 push
→ Actions가 Docker·Certbot 설치와 HTTPS 배포 자동 수행
```

## 1. 배포용 SSH key 준비

OCI 인스턴스 생성 화면에서 **Generate a key pair for me**를 선택하고 private/public key를
모두 다운로드합니다. private key 전체 내용은 GitHub Secret `OCI_SSH_PRIVATE_KEY`에
등록합니다.

private key는 저장소나 OCI 인스턴스에 복사하지 않습니다.

## 2. OCI 인스턴스 생성

OCI Console에서 **Compute → Instances → Create instance**를 선택합니다.

권장값:

| 항목 | 값 |
|---|---|
| Name | `assignment-play` |
| Image | Canonical Ubuntu 24.04 |
| Shape | `VM.Standard.E2.1.Micro` (Always Free-eligible) |
| Network | Public subnet, public IPv4 할당 |
| SSH key | `oci_assignment_deploy_key.pub` 업로드 |
| Boot volume | 기본값 약 46.6GB |

E2/AMD는 x86_64이므로 이후 GitHub Variable은 `linux/amd64`로 설정합니다.

인스턴스가 `RUNNING`이 되면 public IPv4를 기록합니다. 인스턴스를 삭제하지 않는 동안
ephemeral public IP도 유지되지만, 장기 사용한다면 Reserved Public IP가 더 안전합니다.

## 3. OCI ingress 규칙

인스턴스가 속한 Subnet의 Security List 또는 NSG에 다음 Stateful ingress를 추가합니다.

| Source CIDR | Protocol | Destination port | 용도 |
|---|---|---:|---|
| `0.0.0.0/0` | TCP | 22 | GitHub-hosted runner SSH 배포 |
| `0.0.0.0/0` | TCP | 80 | Let's Encrypt와 HTTPS redirect |
| `0.0.0.0/0` | TCP | 443 | HTTPS API |

`5432`와 `8080`은 열지 않습니다. PostgreSQL과 Spring Boot는 Docker 내부 network에서만
통신합니다.

GitHub-hosted runner의 IP가 고정되지 않아 이 과제 구성은 SSH를 전체에 엽니다. Ubuntu는
SSH key-only로 생성되고 첫 배포가 password/root login을 명시적으로 끄며 fail2ban을
설치합니다. 과제 종료 후 인스턴스와 deploy key를 제거하세요.

## 4. DNS 연결

`lemonham.p-e.kr` DNS 관리 화면에 다음 A 레코드를 추가합니다.

| Type | Host/Name | Value | TTL |
|---|---|---|---:|
| A | 빈칸 또는 `@` | OCI public IPv4 | 기본값 |

확인합니다.

```bash
dig +short lemonham.p-e.kr
```

출력이 OCI public IPv4와 같아진 뒤 Actions를 실행해야 합니다. DNS가 아직 전파되지 않으면
Let's Encrypt 인증서 발급이 실패합니다.

## 5. SSH host key 확인

GitHub Actions가 가짜 서버에 접속하지 않도록 known_hosts를 준비합니다.

```bash
ssh-keyscan -H <OCI_PUBLIC_IP> > ~/oci_assignment_known_hosts
ssh-keygen -lf ~/oci_assignment_known_hosts
```

가능하면 출력 fingerprint를 OCI Console serial console 또는 신뢰할 수 있는 최초 접속
정보와 대조합니다. `oci_assignment_known_hosts` 전체 내용을 GitHub Secret에 넣습니다.

## 6. GitHub Secrets 등록

저장소의 **Settings → Secrets and variables → Actions → Repository secrets**에서
등록합니다.

| Secret | 값 |
|---|---|
| `OCI_HOST` | OCI public IPv4 |
| `OCI_SSH_USER` | `ubuntu` |
| `OCI_SSH_PRIVATE_KEY` | OCI에서 다운로드한 private key 전체 내용 |
| `OCI_SSH_KNOWN_HOSTS` | `~/oci_assignment_known_hosts` 전체 내용 |
| `APP_DOMAIN` | `lemonham.p-e.kr` |
| `APP_CORS_ALLOWED_ORIGINS` | 실제 프론트엔드 Origin, 여러 개면 쉼표 구분 |
| `POSTGRES_DB` | `assignment` |
| `POSTGRES_USER` | `assignment` |
| `POSTGRES_PASSWORD` | 아래 명령 결과 |
| `CERTBOT_EMAIL` | 본인 이메일 |

DB 암호는 로컬에서 생성합니다.

```bash
openssl rand -hex 32
```

`APP_CORS_ALLOWED_ORIGINS` 예시:

```text
https://lemonham.p-e.kr,http://localhost:5173
```

Origin에는 경로나 끝 `/`를 넣지 않습니다.

## 7. GitHub Variable 등록

**Settings → Secrets and variables → Actions → Variables**에서
`OCI_DOCKER_PLATFORM`을 등록합니다.

| OCI CPU | 값 |
|---|---|
| Ampere A1 (`aarch64`) | `linux/arm64` |
| AMD/Intel (`x86_64`) | `linux/amd64` |

잘못 설정하면 컨테이너가 `exec format error`로 시작하지 않습니다.

## 8. Push와 자동 배포

워크플로는 main이 아니라 `feat/11/assignment4` push에만 자동 실행됩니다.

```bash
git push origin feat/11/assignment4
```

Actions의 **Deploy assignment 4 to OCI**에서 진행 상황을 확인합니다. 최초 배포는 자동으로
다음을 수행합니다.

1. Gradle 테스트와 Spotless 검사
2. OCI CPU용 Docker 이미지 빌드
3. SSH host key 검증
4. OCI cloud-init 완료 대기
5. Docker, Compose, Certbot, fail2ban 설치
6. 1GB VM을 위한 2GB swap 생성
7. SSH password/root login 비활성화
8. Let's Encrypt 인증서 발급
9. GitHub Secrets로 서버 `.env` 생성 및 mode `600` 적용
10. PostgreSQL, Spring Boot, Nginx 실행
11. Nginx 설정과 Actuator health 검사
12. 실패 시 직전 정상 release rollback

서버에 직접 접속해 스크립트를 실행할 필요가 없습니다.

## 9. 최종 검증

```bash
curl -I http://lemonham.p-e.kr/scalar
curl -I https://lemonham.p-e.kr/scalar
curl -sS https://lemonham.p-e.kr/actuator/health
```

기대 결과:

- HTTP: HTTPS로 `301`
- HTTPS `/scalar`: API 문서 정상 표시
- health: `{"status":"UP"}`

CORS도 확인합니다.

```bash
curl -i -X OPTIONS https://lemonham.p-e.kr/posts \
  -H 'Origin: https://lemonham.p-e.kr' \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: Content-Type'
```

## 문제 해결

- **SSH timeout**: OCI Security List/NSG의 22번과 public IPv4를 확인합니다.
- **Host key verification failed**: `OCI_HOST`와 `OCI_SSH_KNOWN_HOSTS`가 같은 인스턴스인지
  확인합니다.
- **Certbot 실패**: `dig +short lemonham.p-e.kr`가 OCI IP인지, 80번이 열렸는지
  확인하고 Actions를 다시 실행합니다.
- **exec format error**: OCI shape와 `OCI_DOCKER_PLATFORM`을 확인합니다.
- **502 Bad Gateway**: Actions의 deploy 단계에서 app/PostgreSQL 로그를 확인합니다.

## 제출

PR 본문에 다음 주소를 포함합니다.

```text
https://lemonham.p-e.kr/scalar
```
