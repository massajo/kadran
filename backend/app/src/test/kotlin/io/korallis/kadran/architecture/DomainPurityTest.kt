package io.korallis.kadran.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

/**
 * Pureté du domaine — règle d'or 2.4 de `CLAUDE.md`.
 *
 * `domain/model` ne dépend que de `shared-kernel` et de la bibliothèque standard. Ni Spring,
 * ni Jackson, ni annotation de persistance. Un domaine pur se teste sans mock ; si un test du
 * domaine en réclame un, c'est le modèle qu'il faut corriger, pas le test.
 *
 * `DoNotIncludeTests` : ces règles verrouillent le code de production. Les classes de
 * violation délibérée qui prouvent qu'elles rejettent vraiment quelque chose
 * ([ArchRulesRejectViolationsTest]) vivent dans les sources de test — sans cette option,
 * elles feraient échouer le build qu'elles servent à valider.
 */
@AnalyzeClasses(
    packages = ["io.korallis.kadran"],
    importOptions = [ImportOption.DoNotIncludeTests::class],
)
class DomainPurityTest {
    @ArchTest
    fun `le domaine ignore Spring`(classes: JavaClasses) {
        noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..")
            .because("le domaine ne depend d'aucun conteneur (CLAUDE.md §2.4)")
            .check(classes)
    }

    @ArchTest
    fun `le domaine ignore Jackson`(classes: JavaClasses) {
        noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("com.fasterxml.jackson..")
            .because("la serialisation est une preoccupation d'adaptateur, pas de modele")
            .check(classes)
    }

    @ArchTest
    fun `le domaine ignore la persistance`(classes: JavaClasses) {
        noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "jakarta.persistence..",
                "org.jooq..",
                "javax.sql..",
            ).because("aucune annotation ni type de persistance dans le modele (CLAUDE.md §2.4)")
            .check(classes)
    }

    /**
     * Ajoutée en revue de KDN-15 : `platform` tire Spring, si bien qu'un domaine qui
     * nommerait `TenantId` ferait entrer le conteneur par la bande, sous le nez de la règle
     * ci-dessus. C'est aussi la condition à laquelle `TenantId` peut rester dans `platform` —
     * le domaine ne nomme jamais le tenant.
     */
    @ArchTest
    fun `domain ignores platform`(classes: JavaClasses) {
        KadranArchRules.domainDoesNotDependOnPlatform.check(classes)
    }

    @ArchTest
    fun `shared-kernel ignore Spring`(classes: JavaClasses) {
        noClasses()
            .that()
            .resideInAPackage("io.korallis.kadran.core..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..")
            .because("shared-kernel est reutilisable hors de tout conteneur (spec §10.1)")
            .check(classes)
    }
}
