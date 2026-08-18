plugins {
    id("kadran.spring-conventions")
}

dependencies {
    "implementation"(project(":shared-kernel"))
    "implementation"(project(":platform"))
}

// Couverture bloquante sur les paquets `domain` uniquement — c'est là que vit la règle
// métier. Ailleurs elle est indicative (spec §10.6).
tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
    violationRules {
        rule {
            element = "CLASS"
            includes = listOf("io.korallis.kadran.*.domain.*")
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.named("check") { dependsOn(tasks.named("jacocoTestCoverageVerification")) }
