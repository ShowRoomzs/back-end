FROM eclipse-temurin:21-jdk-alpine

# 작업 디렉토리 설정
WORKDIR /app

# 빌드된 Jar 파일을 이미지 내부로 복사 (CI 과정에서 build/libs/.. 경로에 생성된 jar를 가정)
ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} app.jar

# 외부 설정 파일이나 로그 등을 위한 볼륨 설정 (선택 사항)
VOLUME ["/logs"]

# 프로덕션 프로필로 실행
ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-jar", "app.jar"]
