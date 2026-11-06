# AI-assisted deployment configuration with Codex.
FROM node:24-bookworm-slim AS frontend
WORKDIR /build/frontend
RUN npm install -g pnpm@11.25.0
COPY frontend/package.json frontend/pnpm-lock.yaml frontend/pnpm-workspace.yaml frontend/.npmrc ./
RUN pnpm install --frozen-lockfile
COPY frontend/ ./
RUN pnpm exec vite build

FROM maven:3.9.11-eclipse-temurin-21 AS backend
WORKDIR /build
COPY backend/pom.xml backend/pom.xml
RUN mvn -B -ntp -f backend/pom.xml dependency:go-offline
COPY backend/src backend/src
COPY --from=frontend /build/frontend/dist frontend/dist
RUN mvn -B -ntp -f backend/pom.xml verify

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --system shop && useradd --system --gid shop --home /app shop
WORKDIR /app
COPY --from=backend --chown=shop:shop /build/backend/target/shop-0.1.0.jar app.jar
USER shop
ENV PORT=8080 SERVER_ADDRESS=0.0.0.0 TZ=Asia/Shanghai
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-jar", "/app/app.jar"]
