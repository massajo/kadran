package io.korallis.kadran.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

/**
 * Règles de découpage en couches (spec §10.2, ADR-005).
 *
 * `api` = ce que le module offre, `spi` = ce qu'il exige d'un fournisseur. La convention
 * s'applique symétriquement aux ports (`domain/`) et aux adaptateurs (`infrastructure/`) ;
 * ces règles sont ce qui l'empêche de se déliter au fil des PR.
 */
@AnalyzeClasses(packages = ["io.korallis.kadran"])
class LayeringRulesTest {
    @ArchTest
    fun `le domaine ne depend pas de l'infrastructure`(classes: JavaClasses) {
        noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..infrastructure..")
            .because("le domaine est le centre : il ne connait aucun adaptateur")
            .check(classes)
    }

    @ArchTest
    fun `le domaine ne depend pas de la couche application`(classes: JavaClasses) {
        noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..application..")
            .because("la dependance va de l'application vers le domaine, jamais l'inverse")
            .check(classes)
    }

    @ArchTest
    fun `domain model ne depend ni de domain api ni de domain spi`(classes: JavaClasses) {
        noClasses()
            .that()
            .resideInAPackage("..domain.model..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..domain.api..", "..domain.spi..")
            .because("le modele ne connait pas les ports qui l'exposent ou le servent (spec §10.2)")
            .check(classes)
    }

    @ArchTest
    fun `la couche application n'importe jamais infrastructure spi`(classes: JavaClasses) {
        noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..infrastructure.spi..")
            .because("l'application consomme les ports de domain/spi, pas leurs adaptateurs (spec §10.2)")
            .check(classes)
    }

    @ArchTest
    fun `un contexte borne ne depend pas d'un autre contexte borne`(classes: JavaClasses) {
        val contexts = listOf("identity", "ingestion", "activity", "costmodel", "fiscal", "performance")
        contexts.forEach { context ->
            val others = contexts.filter { it != context }.map { "io.korallis.kadran.$it.." }.toTypedArray()
            noClasses()
                .that()
                .resideInAPackage("io.korallis.kadran.$context..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(*others)
                .because("les contextes communiquent par evenements, pas par appel direct (spec §7.1)")
                .check(classes)
        }
    }
}
