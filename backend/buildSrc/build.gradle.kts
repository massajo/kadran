plugins { `kotlin-dsl` }

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.allopen.plugin)
    implementation(libs.detekt.gradle.plugin)
    implementation(libs.ktlint.gradle.plugin)
    implementation(libs.spring.boot.gradle.plugin)
    implementation(libs.spring.depmgmt.gradle.plugin)

    // Le plugin Gradle Liquibase lit le catalogue d'arguments de Liquibase dès son
    // application : il lui faut `liquibase-core` sur le classpath de construction, bien
    // avant que la configuration `liquibaseRuntime` du module `app` ne soit peuplée.
    implementation(libs.liquibase.core)

    // Rend le catalogue de versions accessible depuis les plugins de convention.
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
