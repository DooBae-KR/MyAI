# 한 이미지에 프론트엔드(React)와 백엔드(Spring Boot)를 함께 담는다. Spring이 화면 파일을 같은 주소에서 서빙한다.
# 1) 프론트엔드 빌드 → ai-api의 static으로
FROM node:22-slim AS frontend
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
# build:spring은 ../ai-api/src/main/resources/static 으로 내보낸다
RUN mkdir -p /app/ai-api/src/main/resources && npm run build:spring

# 2) 백엔드 빌드(테스트는 CI에서 돌리므로 이미지 빌드에서는 건너뛴다)
FROM eclipse-temurin:21-jdk AS backend
WORKDIR /app
COPY . .
COPY --from=frontend /app/ai-api/src/main/resources/static ai-api/src/main/resources/static
RUN sh gradlew :ai-api:bootJar -x test --no-daemon

# 3) 실행: JRE만, 일반 사용자로
FROM eclipse-temurin:21-jre
RUN useradd --system --create-home app
USER app
WORKDIR /home/app
COPY --from=backend /app/ai-api/build/libs/*.jar app.jar
# 컨테이너 안에서는 플랫폼이 바깥 노출을 관리하므로 0.0.0.0으로 연다. APP_TOKEN이 없으면 앱이 시작을 거부한다.
ENV SERVER_ADDRESS=0.0.0.0 JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
