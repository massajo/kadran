# syntax=docker/dockerfile:1
#
# Image de production du backend Kadran (Spring Boot, JAR en couches).
# Symétrique de `web/Dockerfile` (KDN-7) côté back — voir spec §10.6.
#
# Contexte de build : la RACINE du monorepo, pas `backend/` — même convention
# que `web/Dockerfile`, pour que `.dockerignore` (racine) s'applique aux deux.
#
#   docker build -f backend/Dockerfile \
#     --build-arg GIT_SHA="$(git rev-parse HEAD)" \
#     --build-arg GIT_SOURCE="https://github.com/massajo/kadran" \
#     --build-arg BUILD_DATE="$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
#     -t kadran-backend:local .

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# --- Fichiers de configuration Gradle seuls : couche mise en cache tant que ---
# --- ni les plugins de convention ni les dépendances déclarées ne changent ---
COPY backend/gradlew backend/gradlew.bat ./
COPY backend/gradle ./gradle
COPY backend/buildSrc ./buildSrc
COPY backend/settings.gradle.kts backend/build.gradle.kts ./
COPY backend/app/build.gradle.kts ./app/build.gradle.kts
COPY backend/platform/build.gradle.kts ./platform/build.gradle.kts
COPY backend/shared-kernel/build.gradle.kts ./shared-kernel/build.gradle.kts
COPY backend/context-identity/build.gradle.kts ./context-identity/build.gradle.kts
COPY backend/context-ingestion/build.gradle.kts ./context-ingestion/build.gradle.kts
COPY backend/context-activity/build.gradle.kts ./context-activity/build.gradle.kts
COPY backend/context-costmodel/build.gradle.kts ./context-costmodel/build.gradle.kts
COPY backend/context-fiscal/build.gradle.kts ./context-fiscal/build.gradle.kts
COPY backend/context-performance/build.gradle.kts ./context-performance/build.gradle.kts

# `--mount=type=cache` persiste `GRADLE_USER_HOME` entre les builds (BuildKit) :
# les dépendances ne sont retéléchargées que si le catalogue de versions ou un
# `build.gradle.kts` change, jamais quand seul le code métier bouge.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :app:dependencies > /dev/null

# --- Source complète, puis build réel : n'invalide que cette couche-ci ---
COPY backend/app ./app
COPY backend/platform ./platform
COPY backend/shared-kernel ./shared-kernel
COPY backend/context-identity ./context-identity
COPY backend/context-ingestion ./context-ingestion
COPY backend/context-activity ./context-activity
COPY backend/context-costmodel ./context-costmodel
COPY backend/context-fiscal ./context-fiscal
COPY backend/context-performance ./context-performance

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :app:bootJar -x test

# --- Extraction des couches du JAR Spring Boot ---
# `-Djarmode=layertools` est déprécié depuis Spring Boot 3.3 ; le jarmode `tools`
# (disponible et vérifié sur Spring Boot 4.1.0, cf. `libs.versions.toml`) le
# remplace avec la même sémantique de couches (`dependencies`,
# `spring-boot-loader`, `snapshot-dependencies`, `application`).
# Le nom du JAR porte la version du projet (`app-0.1.0-SNAPSHOT.jar`) : on le
# résout dynamiquement plutôt que de le figer en dur ici.
RUN JAR="$(find app/build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar')" && \
    java -Djarmode=tools -jar "$JAR" extract --layers --launcher --destination /workspace/extracted

# --- Runtime : JRE seul, non-root, uniquement les couches extraites ---
FROM eclipse-temurin:21-jre-alpine AS runtime

# Les paquets Alpine de l'image de base accusent le retard des dépôts au moment
# où elle a été publiée : `libexpat` et `p11-kit` y traînent des CVE HIGH déjà
# corrigées en amont. `apk upgrade` les rattrape au build, ce que la porte Trivy
# de la spec §10.6 exige — sans quoi l'image échoue le scan quelle que soit la
# fraîcheur du code applicatif. `--no-cache` évite de laisser l'index apk dans
# la couche finale.
#
# `addgroup`/`adduser` BusyBox : `-G` attache l'utilisateur au groupe créé,
# sinon son groupe primaire retombe sur un groupe générique (cf. web/Dockerfile).
RUN apk upgrade --no-cache \
    && addgroup --system --gid 1001 kadran \
    && adduser --system --uid 1001 -G kadran kadran

WORKDIR /app

ARG GIT_SHA
ARG GIT_SOURCE
ARG BUILD_DATE
LABEL org.opencontainers.image.title="kadran-backend" \
      org.opencontainers.image.description="Backend Kadran (Spring Boot, JAR en couches)" \
      org.opencontainers.image.revision="$GIT_SHA" \
      org.opencontainers.image.source="$GIT_SOURCE" \
      org.opencontainers.image.created="$BUILD_DATE" \
      org.opencontainers.image.licenses="UNLICENSED"

# Ordre des couches : de la moins volatile (dépendances tierces) à la plus
# volatile (code applicatif) — maximise la réutilisation du cache Docker d'une
# image à l'autre quand seul le code métier change.
COPY --from=build --chown=kadran:kadran /workspace/extracted/dependencies/ ./
COPY --from=build --chown=kadran:kadran /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=kadran:kadran /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=kadran:kadran /workspace/extracted/application/ ./

ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport" \
    SERVER_PORT=8080

EXPOSE 8080
USER kadran

# `wget` (BusyBox) est présent dans la base alpine : pas besoin de curl.
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
  CMD wget -q -O /dev/null "http://127.0.0.1:${SERVER_PORT}/actuator/health" || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
