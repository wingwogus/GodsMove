# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY api/build.gradle.kts ./api/build.gradle.kts
COPY application/build.gradle.kts ./application/build.gradle.kts
COPY batch/build.gradle.kts ./batch/build.gradle.kts
COPY domain/build.gradle.kts ./domain/build.gradle.kts

RUN chmod +x ./gradlew

COPY . .
RUN --mount=type=cache,target=/root/.gradle ./gradlew :api:bootJar --no-daemon \
    && jar_path="$(find api/build/libs -name '*.jar' ! -name '*-plain.jar' -print -quit)" \
    && cp "$jar_path" /workspace/app.jar

FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app

RUN groupadd --system app \
    && useradd --system --gid app --home-dir /app app

COPY --from=builder /workspace/app.jar ./app.jar

ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS=""

EXPOSE 8080

USER app

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
