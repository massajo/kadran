// Tenancy, sécurité, chiffrement, audit, outbox (spec §10.1).
plugins {
    id("kadran.spring-conventions")
    // `java-library` pour disposer de la configuration `api` : `TenantScopedTable` expose
    // des types jOOQ dans sa signature, ils font donc partie du contrat du module et non de
    // ses coulisses. Sans cela, aucun contexte borné ne peut écrire un repository.
    `java-library`
}

dependencies {
    implementation(project(":shared-kernel"))

    // Sans version : le BOM Spring Boot, importé par la convention `kadran.spring-conventions`,
    // les épingle toutes — y compris `kotlinx-coroutines` et `jakarta.servlet-api`. Rien à
    // déclarer dans le catalogue de versions tant qu'on reste dans son périmètre.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    // jOOQ sert le DSL analytique et `TenantScopedQuery` (spec §10.3). En `api` parce que
    // `Table`, `Field` et `Record` apparaissent dans la signature de `TenantScopedTable` :
    // un consommateur qui ne les voit pas ne peut pas déclarer sa table. Seul l'artefact
    // `jooq` est requis — la génération de code n'est pas encore en place, les tables se
    // déclarent à la main via `TenantScopedTable.named(...)`.
    api("org.jooq:jooq")
    implementation("org.slf4j:slf4j-api")
    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-web")
    implementation("org.springframework.boot:spring-boot")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("jakarta.servlet:jakarta.servlet-api")

    // Spring Security (spec §10.3, ligne `Auth`). En `api` : `TenantIdResolver` et le
    // contrôleur d'authentification exposent `Jwt`, `PasswordEncoder` et `SecurityFilterChain`
    // dans leurs signatures, et le module `app` doit les voir pour composer sa configuration.
    // `-oauth2-resource-server` apporte le validateur de jeton porteur et son extraction
    // d'en-tête ; `-oauth2-jose` apporte Nimbus, donc l'encodeur et le décodeur JWT. Les deux
    // sont nécessaires : le premier valide, le second signe, et rien n'émet de jeton sans lui.
    api("org.springframework.boot:spring-boot-starter-security")
    api("org.springframework.security:spring-security-oauth2-resource-server")
    api("org.springframework.security:spring-security-oauth2-jose")

    // `MockHttpServletRequest` et consorts : le filtre se teste sans démarrer de conteneur.
    testImplementation("org.springframework:spring-test")

    // Sans implémentation SLF4J, `MDC` retombe sur un adaptateur NOP qui avale tout : les
    // assertions sur le MDC passeraient à vide, ce qui est pire que pas de test du tout.
    testRuntimeOnly("ch.qos.logback:logback-classic")
}
