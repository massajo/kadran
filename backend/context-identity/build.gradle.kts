// Contexte borné `identity` — structure interne : domain/{model,api,spi} · application ·
// infrastructure/{api,spi} (ADR-005). Les dépendances vers shared-kernel et platform
// sont apportées par la convention.
plugins { id("kadran.context-conventions") }

dependencies {
    // PostgreSQL réel via Testcontainers, **jamais H2** (spec §10.4). Le schéma de KDN-27
    // repose sur des index uniques partiels, des clés étrangères composites et des `CHECK`
    // à expression régulière : H2 en accepterait certains et en ignorerait d'autres, si bien
    // qu'un test vert n'y prouverait rien de l'isolation.
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit)
    // Le changelog vit dans `app` (spec §11.1) ; le test d'isolation l'applique lui-même
    // plutôt que de recopier le DDL. Un DDL recopié diverge du changeset au premier
    // ajout de colonne, et le test cesse alors de tester le schéma livré.
    testImplementation(libs.liquibase.core)
    testRuntimeOnly(libs.postgresql)
}

// docker-java, qu'embarque Testcontainers, annonce encore l'API Docker 1.32, que les démons
// Docker 29+ rejettent. Même réglage que dans `app/build.gradle.kts` — c'est le second module
// à avoir des tests Testcontainers, donc le moment de le remonter dans les conventions
// approche ; il y sera fait une fois, pas trois.
tasks.withType<Test>().configureEach {
    systemProperty("api.version", System.getenv("DOCKER_API_VERSION") ?: "1.44")
}
