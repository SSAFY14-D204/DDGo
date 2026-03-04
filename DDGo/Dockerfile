FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY src src

# gradlew 실행 권한 부여 및 빌드 (테스트 제외)
RUN chmod +x ./gradlew
RUN ./gradlew build -x test

# 실제 실행할 가벼운 이미지
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
# 빌더 이미지에서 생성된 jar 파일을 복사
COPY --from=builder /app/build/libs/*-SNAPSHOT.jar app.jar

# 기본 포트 노출
EXPOSE 8080

# 환경변수 프로필을 주입받아 실행 (예: -Dspring.profiles.active=prod)
ENTRYPOINT ["java", "-jar", "app.jar"]
