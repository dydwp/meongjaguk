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
