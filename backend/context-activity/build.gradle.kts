// Contexte borné `activity` — structure interne : domain/{model,api,spi} · application ·
// infrastructure/{api,spi} (ADR-005). Les dépendances vers shared-kernel et platform
// sont apportées par la convention.
plugins { id("kadran.context-conventions") }

dependencies {
    // PostgreSQL réel via Testcontainers, **jamais H2** (spec §10.4) — même motif que
    // `context-identity` (KDN-27) : les tests d'isolation d'`Outing` (KDN-34) et de
    // `RevenueRecord` (KDN-35) portent sur des clés étrangères composites et un index GIN,
    // qu'H2 ne reproduit ni dans la syntaxe ni dans le comportement.
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit)
    // Le changelog vit dans `app` (spec §11.1) ; le test d'isolation l'applique lui-même
    // plutôt que de recopier le DDL.
    testImplementation(libs.liquibase.core)
    testRuntimeOnly(libs.postgresql)
}

// docker-java, qu'embarque Testcontainers, annonce encore l'API Docker 1.32, que les démons
// Docker 29+ rejettent — même réglage que `context-identity/build.gradle.kts` (KDN-27) et
// `app/build.gradle.kts`.
tasks.withType<Test>().configureEach {
    systemProperty("api.version", System.getenv("DOCKER_API_VERSION") ?: "1.44")
}
