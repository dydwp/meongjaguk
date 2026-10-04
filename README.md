# 멍자국

반려견과 함께하는 산책을 위한 웹 서비스입니다.
현재 위치 기반 코스 추천부터 산책 기록, 동행 모집까지 제공합니다.

🌐 서비스 주소: <https://meongjaguk.cloud>

## 팀 구성

| 이름 | 역할 |
| --- | --- |
| 박용제 | 팀장 |
| 정석진 | 팀원 |
| 최주영 | 팀원 |
| 김환중 | 팀원 |

## 주요 기능

- **소셜 로그인**: Google · Kakao · Naver
- **반려견 관리**: 프로필 등록·수정·삭제 및 사진 업로드
- **산책 코스 추천**: 코스 조회 및 현재 위치 기반 AI 추천
- **산책 기록**: GPS 경로 기록, 카카오맵 경로 표시, 활동 내역 확인
- **산책로 공유**: 추천 산책로 게시글 등록·수정·삭제
- **같이 걷기**: 동행 모집·참여, 신청 수락·거절, 댓글, 동행 산책 시작·종료
- **알림**: 동행 신청 등 주요 이벤트 알림

## 기술 구성

| 구분 | 기술 |
| --- | --- |
| 웹 서버 | Java 21, Spring Boot, Spring Security, Spring Data JPA, Spring Actuator |
| 화면 | Thymeleaf, JavaScript, Kakao Maps |
| 데이터베이스 | MySQL 8.0 |
| AI 서버 | Python 3.12, FastAPI, XGBoost, OSMnx, NetworkX |
| 배포·CI | Docker, Docker Compose, AWS EC2, Caddy, GitHub Actions |

## 시스템 구성

```text
브라우저 ──→ Caddy (HTTPS) ──→ Spring 앱 (8081) ──→ MySQL (3306)
                                    │
                                    └──→ AI 서버 (FastAPI, 8000)
```

- AI 산책로 추천은 브라우저가 Spring의 `/api/routes/recommend`를 호출하면, Spring이 AI 서버에 요청을 전달하는 방식입니다.
- MySQL과 AI 서버는 외부에 공개하지 않고 서버 내부에서만 호출합니다.

## 로컬 실행

### 방법 1. Docker Compose (권장)

Docker Desktop만 있으면 MySQL, Spring 앱, AI 서버를 한 번에 실행할 수 있습니다.

1. AI 서버의 도로망 파일을 받습니다. (Git LFS로 관리)

   ```bash
   git lfs install
   git lfs pull
   ```

2. [.env.example](.env.example)을 복사해 `.env`를 만들고 `MYSQL_ROOT_PASSWORD`, OAuth 인증 정보, `KAKAO_MAP_JSKEY`를 채웁니다. `.env`는 커밋하지 않습니다.
3. 저장소 루트에서 실행합니다.

   ```bash
   docker compose up --build
   ```

| 서비스 | 주소 |
| --- | --- |
| 웹 | <http://localhost:8081> |
| AI 서버 API 문서 | <http://localhost:8000/docs> |
| MySQL | `localhost:13306` (로컬 MySQL과 충돌하지 않도록 13306 사용) |

테이블은 첫 실행 때 [sql](sql/) 폴더의 SQL로 자동 생성됩니다.
AI 서버는 시작할 때 도로망 파일을 읽느라 몇 분 정도 걸릴 수 있습니다.

### 방법 2. 직접 실행

#### 웹 서버

JDK 21과 MySQL을 준비한 뒤 다음을 설정합니다.

1. MySQL에 `meongjaguk` 데이터베이스를 생성합니다. 기본 접속 주소는 `localhost:3306`입니다.
2. `src/main/resources/application-secret.properties`에 DB 계정, Google·Kakao·Naver OAuth 인증 정보, `kakao.map.js-key`를 설정합니다. 이 파일은 Git 추적 대상에서 제외되어 있습니다.
3. 각 서비스의 개발자 콘솔에 소셜 로그인과 지도 사용을 위한 로컬 주소를 등록합니다.

공통 설정은 [application.properties](src/main/resources/application.properties),
환경변수 항목은 [.env.example](.env.example)을 참고하세요.

저장소 루트에서 실행합니다(Windows PowerShell).

```powershell
.\mvnw.cmd spring-boot:run
```

macOS / Linux에서는 `./mvnw spring-boot:run`을 사용합니다.
실행 후 <http://localhost:8081>에 접속합니다.

#### AI 서버

AI 추천을 사용하려면 AI 서버도 함께 실행해야 합니다.
Spring 앱은 `ai.server.url`(기본값 `http://localhost:8000`)로 AI 서버를 호출합니다.
설치와 실행은 [AI 서버 README](ai-server/README.md)를 참고하세요.

도로망 파일은 Git LFS로 관리합니다. 최초 설정 시 저장소 루트에서 다운로드합니다.

```bash
git lfs install
git lfs pull
```

## 상태 확인

Spring Actuator로 서버 상태를 확인할 수 있습니다. DB 등 상세 정보는 숨기고 `UP` / `DOWN`만 응답합니다.

```text
GET /actuator/health
```

## 테스트

| 대상 | 실행 명령(저장소 루트 기준) |
| --- | --- |
| Java | Windows: `.\mvnw.cmd test` / macOS·Linux: `./mvnw test` |
| JavaScript | Node.js 설치 후 `npm ci`, `npm test` 순서로 실행 |
| AI 서버 | [AI 테스트 안내](ai-server/tests/README.md) 참고 |

## CI / 배포

- **CI**: GitHub Actions로 PR과 push 때 자동으로 빌드·테스트합니다.
  - Spring 앱: [app-ci.yml](.github/workflows/app-ci.yml) — `dev`·`main` 대상 PR, `main` push (ai-server만 바뀐 경우 제외)
  - AI 서버: [ai-ci.yml](.github/workflows/ai-ci.yml) — `dev`·`main` 대상 PR과 push
- **배포**: AWS EC2 한 대에서 Docker Compose로 MySQL, Spring 앱, AI 서버, Caddy를 함께 실행합니다.
  배포 순서와 명령어는 [AWS 배포 가이드](infra/README.md)를 참고하세요.
