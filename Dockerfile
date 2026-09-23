FROM eclipse-temurin:21-jdk AS jdk
FROM mcr.microsoft.com/playwright/java:v1.58.0-noble

# Keep Chromium and the Java dependency at the same version. Korean fonts are embedded by Chromium.
COPY --from=jdk /opt/java/openjdk /opt/java/openjdk
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="/opt/java/openjdk/bin:${PATH}"
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
RUN apt-get update && apt-get install -y --no-install-recommends fonts-noto-cjk \
    && rm -rf /var/lib/apt/lists/*

# 작업 디렉토리 설정
WORKDIR /app

# 빌드된 Jar 파일을 이미지 내부로 복사 (CI 과정에서 build/libs/.. 경로에 생성된 jar를 가정)
ARG JAR_FILE=build/libs/*-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar

# 외부 설정 파일이나 로그 등을 위한 볼륨 설정 (선택 사항)
VOLUME ["/logs"]

# 프로덕션 프로필로 실행
ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-jar", "app.jar"]
