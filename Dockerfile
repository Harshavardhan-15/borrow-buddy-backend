# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# BorrowBuddy API image
#
# Build from this repository's root:
#   docker build -t borrowbuddy-api .
#
# The React frontend is deployed separately and is not packaged into this
# image; this container serves the REST API and the uploaded images.
# ---------------------------------------------------------------------------

# ---------- build stage ----------
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Cache dependencies first
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

# Copy sources and build the jar (tests run in CI, not in the image build)
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- runtime stage ----------
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system borrowbuddy \
    && useradd --system --gid borrowbuddy --home-dir /app borrowbuddy \
    && mkdir -p /app/uploads \
    && chown -R borrowbuddy:borrowbuddy /app

COPY --from=build /workspace/target/borrowbuddy-1.0.0.jar /app/app.jar

USER borrowbuddy

ENV SPRING_PROFILES_ACTIVE=prod \
    PORT=8080 \
    UPLOAD_DIR=/app/uploads \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS "http://127.0.0.1:${PORT}/api/public/stats" >/dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
