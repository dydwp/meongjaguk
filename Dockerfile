# syntax=docker/dockerfile:1

# ---------- 1단계: 빌드 (JDK) ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY src src

# Windows 에서 체크아웃하면 mvnw 가 CRLF 라 리눅스에서 실행되지 않으므로 LF 로 변환
# Maven 저장소는 BuildKit 캐시로 재사용해 재빌드 속도 향상
RUN --mount=type=cache,target=/root/.m2 \
    sed -i 's/\r$//' mvnw && chmod +x mvnw \
    && ./mvnw -B package -DskipTests

# ---------- 2단계: 실행 (JRE) ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# root 가 아닌 사용자로 실행
# 반려견 사진은 작업 폴더 기준 src/main/resources/static/images/pets 에 저장되므로 미리 생성 (compose 에서 볼륨 연결)
RUN groupadd --system app && useradd --system --gid app app \
    && mkdir -p src/main/resources/static/images/pets \
    && chown -R app:app /app

COPY --from=build /workspace/target/*.jar app.jar

USER app
EXPOSE 8081

# 앱 상태 확인: /actuator/health 가 UP 이 아니면 unhealthy (DB 연결 끊김 등도 감지)
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -fs http://127.0.0.1:8081/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
