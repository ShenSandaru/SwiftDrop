# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw \
    && ./mvnw --batch-mode --no-transfer-progress dependency:go-offline

COPY src/ src/
RUN ./mvnw --batch-mode --no-transfer-progress package -DskipTests \
    && cp target/swiftdrop-backend-*.jar /workspace/app.jar

# Build a minimal JRE that includes jdk.random (required by RandomGenerator.getDefault() in
# WebhookSenderConfiguration; omitted from eclipse-temurin:21-jre-alpine).
RUN jlink \
    --add-modules java.se,jdk.charsets,jdk.crypto.cryptoki,jdk.crypto.ec,jdk.dynalink,jdk.httpserver,jdk.localedata,jdk.management,jdk.management.agent,jdk.naming.dns,jdk.naming.rmi,jdk.net,jdk.nio.mapmode,jdk.random,jdk.security.auth,jdk.security.jgss,jdk.unsupported,jdk.xml.dom,jdk.zipfs \
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --compress=zip-6 \
    --output /opt/jre

FROM alpine:3.21 AS runner
WORKDIR /app

RUN apk add --no-cache ca-certificates tzdata libstdc++ \
    && addgroup --system swiftdrop \
    && adduser --system --ingroup swiftdrop swiftdrop

ENV JAVA_HOME=/opt/jre
ENV PATH="${JAVA_HOME}/bin:${PATH}"

COPY --from=builder /opt/jre /opt/jre
COPY --from=builder --chown=swiftdrop:swiftdrop /workspace/app.jar /app/app.jar

USER swiftdrop

# Container-aware JVM memory cap; override via JAVA_TOOL_OPTIONS in Compose / Kubernetes.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65.0 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health >/dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
