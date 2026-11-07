# MallAgent 后端多阶段构建：仅将运行时 JAR 和 JRE 放入最终镜像。
ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-21
ARG JRE_IMAGE=eclipse-temurin:21-jre-jammy

FROM ${MAVEN_IMAGE} AS build
WORKDIR /workspace

COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

FROM ${JRE_IMAGE}
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system mallagent \
    && useradd --system --gid mallagent --home-dir /app mallagent

COPY --from=build /workspace/target/mallagent-*.jar /app/app.jar
RUN chown -R mallagent:mallagent /app

USER mallagent
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=10 \
    CMD curl -fsS http://127.0.0.1:8080/api/v1/system/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
