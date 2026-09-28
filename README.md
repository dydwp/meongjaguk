# 멍자국

반려견과 함께하는 산책을 위한 웹 서비스입니다.

현재 위치 기반 산책 코스 추천, 산책 기록, 반려견 프로필,
같이 걷기 및 마이페이지 기능을 제공합니다.

## 주요 기능

- 소셜 로그인 (Google / Kakao / Naver)
- 반려견 프로필 등록 및 관리
- 추천 산책 코스 조회
- 현재 위치 기반 AI 산책 코스 추천
- GPS 기반 산책 시작·종료 및 경로 기록
- 같이 걷기 모집 및 참여
- 마이페이지
  - 반려견 목록
  - 내가 공유한 글
  - 동행 신청 수락·거절
  - 산책 활동 내역 및 상세 경로 확인

## Branch Strategy

### main

- 운영 배포 브랜치
- 최종 검증이 완료된 코드만 병합
- `dev` 브랜치에서 Pull Request를 통해 병합

### dev

- 개발 통합 및 최종 확인 브랜치
- 각 개인 작업 브랜치에서 개발 완료 후 `dev`로 병합
- 기능 통합 및 테스트 진행

### 개인 작업 브랜치

기능 개발은 각 팀원의 개인 브랜치에서 진행합니다.

```text
features
├─ park
├─ kim
├─ jeong
└─ choi
```

## DB 및 비밀 설정

로컬 DB는 MySQL의 `meongjaguk` 데이터베이스를 사용합니다.

DB 비밀번호, OAuth 인증 정보, Kakao Maps JavaScript Key 등
GitHub에 업로드하면 안 되는 정보는 다음 파일에서 관리합니다.

```text
src/main/resources/application-secret.properties
```

해당 파일은 `.gitignore`에 포함되어 있으므로
프로젝트를 새로 clone한 경우 별도로 설정해야 합니다.

## 프로젝트 실행

Windows 기준:

```bash
./mvnw.cmd spring-boot:run
```

컴파일 및 테스트:

```bash
./mvnw.cmd compile
./mvnw.cmd test
```

## AI 산책 코스 추천

`ai-server`에서 현재 위치를 기반으로 산책 코스를 추천합니다.

주요 구성:

- FastAPI
- XGBoost
- OSMnx / NetworkX
- 서울 보행 도로망 데이터

AI 서버 관련 세부 실행 방법은 `ai-server/README.md`를 참고합니다.

## 서비스용 서울 보행 도로망 데이터 파일 관리

`/ai-server/data/seoul_walk.graphml` 파일은 용량이 약 1.4GB로 크기 때문에 Git LFS로 관리합니다.

해당 파일은 `.gitattributes`에 Git LFS 추적 대상으로 등록되어 있습니다.

프로젝트를 처음 clone하거나 Git LFS를 처음 사용하는 경우 아래 명령어를 실행해주세요.

```bash
git lfs install
```

대용량 파일이므로 네트워크 환경에 따라 다운로드에 시간이 걸릴 수 있습니다.
