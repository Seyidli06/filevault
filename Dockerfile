FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml .

RUN mvn \
    --batch-mode \
    --no-transfer-progress \
    dependency:go-offline

COPY src ./src

RUN mvn \
    --batch-mode \
    --no-transfer-progress \
    clean package \
    -DskipTests


FROM eclipse-temurin:21-jre AS runtime

WORKDIR /app

RUN groupadd \
        --system \
        filevault \
    && useradd \
        --system \
        --gid filevault \
        --home-dir /app \
        --no-create-home \
        filevault \
    && mkdir -p \
        /data/filevault/uploads \
        /data/filevault/temp \
    && chown -R \
        filevault:filevault \
        /app \
        /data/filevault

COPY \
    --from=build \
    --chown=filevault:filevault \
    /workspace/target/filevault-*.jar \
    /app/app.jar

USER filevault

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]