# 1. Build Stage (빌드 환경)
FROM gradle:8.7-jdk21-alpine AS BUILD_STAGE
WORKDIR /app
COPY build.gradle settings.gradle /app/
COPY gradlew /app/
COPY .gradle /app/.gradle
COPY src /app/src

RUN ./gradlew build -x test

# 2. Run Stage (실행 환경)
FROM amazoncorretto:21-alpine-jdk
WORKDIR /app
# 빌드 스테이지에서 .jar 파일을 복사
COPY --from=BUILD_STAGE /app/build/libs/*.jar app.jar
# 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "app.jar"]