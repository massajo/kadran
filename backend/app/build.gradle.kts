// Bootstrap Spring Boot et composition des contextes (spec §10.1).
plugins {
    id("kadran.spring-conventions")
    // Sans version : le plugin est deja sur le classpath via buildSrc, qui le tient
    // du catalogue de versions. Le declarer deux fois ferait echouer la resolution.
    id("org.springframework.boot")
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

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(libs.archunit.junit5)
}

// JAR en couches : le Dockerfile de KDN-6 n'a alors à reconstruire que la couche
// applicative quand seul le code métier change.
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    layered { enabled.set(true) }
}
