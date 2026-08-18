plugins { `kotlin-dsl` }

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.allopen.plugin)
    implementation(libs.detekt.gradle.plugin)
    implementation(libs.ktlint.gradle.plugin)
    implementation(libs.spring.boot.gradle.plugin)
    implementation(libs.spring.depmgmt.gradle.plugin)

    // Rend le catalogue de versions accessible depuis les plugins de convention.
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
