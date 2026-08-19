// Bootstrap Spring Boot et composition des contextes (spec §10.1).
plugins {
    id("kadran.spring-conventions")
    // Sans version : le plugin est deja sur le classpath via buildSrc, qui le tient
    // du catalogue de versions. Le declarer deux fois ferait echouer la resolution.
    id("org.springframework.boot")
    alias(libs.plugins.liquibase)
}

dependencies {
    implementation(project(":shared-kernel"))
    implementation(project(":platform"))
    implementation(project(":context-identity"))
    implementation(project(":context-ingestion"))
    implementation(project(":context-activity"))
    implementation(project(":context-costmodel"))
    implementation(project(":context-fiscal"))
    implementation(project(":context-performance"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation(libs.liquibase.core)
    runtimeOnly(libs.postgresql)
    // Redémarrage à chaud du contexte Spring en développement (docker/compose.yml, KDN-5) —
    // exclu du JAR de production par construction (`developmentOnly`), donc sans effet sur
    // l'image de KDN-6.
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit)

    // Le plugin Gradle Liquibase execute la CLI dans un classpath separe : il lui faut
    // son propre moteur, son pilote JDBC et picocli, qui analyse la ligne de commande.
    liquibaseRuntime(libs.liquibase.core)
    liquibaseRuntime(libs.postgresql)
    liquibaseRuntime(libs.picocli)
}

// Migrations pilotées en ligne de commande — `./gradlew liquibaseUpdate liquibaseRollbackCount
// -PliquibaseCommandValue=1` (CLAUDE.md §5). Les valeurs par défaut sont celles
// d'`application.yaml` : qui a démarré la base de développement n'a rien à configurer, et
// aucun mot de passe n'est écrit en dur ici.
//
// `liquibaseCommandValue` est la propriété héritée du plugin 2.x ; la 3.x, seule compatible
// Gradle 9, attend un argument nommé. Elle est donc traduite ici en `--count`, l'argument de
// `rollbackCount` — les autres commandes l'ignorent. La ligne de commande documentée reste
// ainsi la seule à connaître.
liquibase {
    activities.register("main") {
        arguments =
            buildMap {
                put("searchPath", file("src/main/resources").absolutePath)
                put("changelogFile", "db/changelog/db.changelog-master.xml")
                put("url", System.getenv("KADRAN_DB_URL") ?: "jdbc:postgresql://localhost:5432/kadran")
                put("username", System.getenv("KADRAN_DB_USER") ?: "kadran")
                put("password", System.getenv("KADRAN_DB_PASSWORD") ?: "kadran")
                providers.gradleProperty("liquibaseCommandValue").orNull?.let { put("count", it) }
            }
    }
}

// docker-java, qu'embarque Testcontainers, annonce encore l'API Docker 1.32, que les démons
// Docker 29+ rejettent (« client version 1.32 is too old »). La version d'API est donc fixée
// explicitement pour les tests, sans écraser un réglage déjà présent dans l'environnement.
// À remonter dans les conventions le jour où un autre module aura des tests Testcontainers.
tasks.withType<Test>().configureEach {
    systemProperty("api.version", System.getenv("DOCKER_API_VERSION") ?: "1.44")
}

// JAR en couches : le Dockerfile de KDN-6 n'a alors à reconstruire que la couche
// applicative quand seul le code métier change.
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    layered { enabled.set(true) }
}
