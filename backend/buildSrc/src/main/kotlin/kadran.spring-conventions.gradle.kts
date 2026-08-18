plugins {
    id("kadran.kotlin-conventions")
    id("org.jetbrains.kotlin.plugin.spring")
    id("io.spring.dependency-management")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

// Le BOM Spring Boot epingle `kotlin.version` sur la version avec laquelle Spring a ete
// construit, et `io.spring.dependency-management` l'applique a **toutes** les configurations
// — y compris celles, internes, dont le Kotlin Gradle Plugin se sert pour la compilation
// incrementale. Sans cet alignement, `kotlin-build-tools-impl` est retrograde et la
// compilation echoue des qu'un module a des sources.
extra["kotlin.version"] = libs.versions.kotlin.get()

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.springBoot.get()}")
    }
}

dependencies {
    // MockK est réservé à la couche `application` : le domaine se teste sans mock (spec §10.4).
    "testImplementation"(libs.mockk)
}
