# 멍자국

반려견과 함께하는 산책을 위한 웹 서비스입니다.
현재 위치 기반 코스 추천부터 산책 기록, 동행 모집까지 제공합니다.

## 주요 기능

- **소셜 로그인**: Google · Kakao · Naver
- **반려견 관리**: 프로필 등록 및 수정
- **산책 코스 추천**: 코스 조회 및 현재 위치 기반 AI 추천
- **산책 기록**: GPS 경로 기록과 활동 내역 확인
- **같이 걷기**: 동행 모집·참여 및 신청 수락·거절

## 기술 구성

| 구분 | 기술 |
| --- | --- |
| 웹 서버 | Java 21, Spring Boot, Spring Security, Spring Data JPA |
| 화면 | Thymeleaf, JavaScript, Kakao Maps |
| 데이터베이스 | MySQL |
| AI 서버 | Python 3.12, FastAPI, XGBoost, OSMnx, NetworkX |

## 로컬 실행

### 웹 서버

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

### AI 서버

AI 추천을 사용하려면 별도 서버가 필요합니다.
현재 프론트엔드는 `http://127.0.0.1:8000`으로 추천을 요청합니다.
설치와 실행은 [AI 서버 README](ai-server/README.md)를 참고하세요.

도로망 파일은 Git LFS로 관리합니다. 최초 설정 시 저장소 루트에서 다운로드합니다.

```bash
git lfs install
git lfs pull
```

## 테스트

| 대상 | 실행 명령(저장소 루트 기준) |
| --- | --- |
| Java | Windows: `.\mvnw.cmd test` / macOS·Linux: `./mvnw test` |
| JavaScript | Node.js 설치 후 `npm ci`, `npm test` 순서로 실행 |
| AI 서버 | [AI 테스트 안내](ai-server/tests/README.md) 참고 |
