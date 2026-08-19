// Tenancy, sécurité, chiffrement, audit, outbox (spec §10.1).
plugins { id("kadran.spring-conventions") }

dependencies {
    implementation(project(":shared-kernel"))

    // Sans version : le BOM Spring Boot, importé par la convention `kadran.spring-conventions`,
    // les épingle toutes — y compris `kotlinx-coroutines` et `jakarta.servlet-api`. Rien à
    // déclarer dans le catalogue de versions tant qu'on reste dans son périmètre.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.slf4j:slf4j-api")
    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-web")
    implementation("org.springframework.boot:spring-boot")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("jakarta.servlet:jakarta.servlet-api")

    // `MockHttpServletRequest` et consorts : le filtre se teste sans démarrer de conteneur.
    testImplementation("org.springframework:spring-test")

    // Sans implémentation SLF4J, `MDC` retombe sur un adaptateur NOP qui avale tout : les
    // assertions sur le MDC passeraient à vide, ce qui est pire que pas de test du tout.
    testRuntimeOnly("ch.qos.logback:logback-classic")
}
