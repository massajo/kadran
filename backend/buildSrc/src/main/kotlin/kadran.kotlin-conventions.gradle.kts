import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")
    jacoco
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

group = "io.korallis.kadran"
version = "0.1.0-SNAPSHOT"

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        // `-Werror` : un avertissement non traité devient une dette qu'on ne paie jamais.
        allWarningsAsErrors.set(true)
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

dependencies {
    "testImplementation"(kotlin("test"))
    "testImplementation"(libs.kotest.runner)
    "testImplementation"(libs.kotest.assertions)
    "testImplementation"(libs.kotest.property)
}

// detekt embarque son propre compilateur Kotlin et refuse de tourner sous une version
// differente de celle avec laquelle il a ete compile. Le Kotlin Gradle Plugin, lui, aligne
// tout le groupe `org.jetbrains.kotlin` sur la version du projet — y compris sur la
// configuration `detekt`, ce qui casse detekt.
//
// La regle est donc reenregistree dans `afterEvaluate`, apres celle du KGP : a egalite,
// c'est la derniere `useVersion` qui l'emporte. Elle ne porte que sur la configuration
// `detekt` ; le code de production reste compile avec la version du catalogue.
val detektKotlin = io.gitlab.arturbosch.detekt.getSupportedKotlinVersion()
afterEvaluate {
    configurations.matching { it.name == "detekt" }.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") {
                useVersion(detektKotlin)
                because("detekt $detektKotlin ne tourne pas sous un autre Kotlin")
            }
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("gradle/detekt.yml"))
    parallel = true
}

ktlint {
    version.set("1.5.0")
    filter { exclude("**/build/**") }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    finalizedBy(tasks.named("jacocoTestReport"))
    testLogging { events("failed") }
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
