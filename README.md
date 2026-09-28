# 멍자국

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
feature
├─ park
├─ kim
├─ jeong
└─ choi
```

## DB 정보 연결

src/main/resources/application.properties안에

spring.datasource.url=jdbc:mysql://localhost:3306/mungjaguk?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
spring.datasource.username=root
spring.datasource.password=비밀번호
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

spring.jpa.hibernate.ddl-auto=none
spring.jpa.show-sql=true
코드를 추가합니다.

## 서비스용 서울 보행 도로망 데이터 파일 관리

`/ai-server/data/seoul_walk.graphml` 파일은 용량이 약 1.4GB로 크기 때문에 Git LFS로 관리합니다.

해당 파일은 `.gitattributes`에 Git LFS 추적 대상으로 등록되어 있습니다.

프로젝트를 처음 clone하거나 Git LFS를 처음 사용하는 경우 아래 명령어를 실행해주세요.

```bash
git lfs install
```
