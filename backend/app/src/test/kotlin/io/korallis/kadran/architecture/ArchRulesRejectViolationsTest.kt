package io.korallis.kadran.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain

private const val FIXTURES = "io.korallis.kadran.architecture.fixtures"

private fun rejects(
    regle: ArchRule,
    classes: JavaClasses,
): String = shouldThrow<AssertionError> { regle.check(classes) }.message.orEmpty()

/**
 * Contre-épreuve des règles d'isolation : chacune doit **rejeter** une violation délibérée.
 *
 * Le dépôt n'a encore ni repository, ni table métier, ni contexte borné peuplé. Toutes les
 * règles de la spec §9.1 y seraient donc vertes même écrites de travers — une règle qui ne
 * matche rien est pire qu'absente, elle rassure à tort. Ce test est ce qui distingue « la
 * règle passe » de « la règle protège ».
 *
 * Les classes fautives vivent dans `architecture.fixtures`, dans les sources de test, et sont
 * exclues des règles réelles par `ImportOption.DoNotIncludeTests`. Ce sont **les mêmes objets
 * `ArchRule`** qui sont exercés ici et sur la production : recopier une règle dans sa propre
 * preuve ne prouverait rien.
 */
class ArchRulesRejectViolationsTest :
    StringSpec({
        val violatingClasses = ClassFileImporter().importPackages(FIXTURES)
        val productionClasses =
            ClassFileImporter()
                .withImportOption(ImportOption.DoNotIncludeTests())
                .importPackages("io.korallis.kadran")

        val dbAccessRule = KadranArchRules.databaseAccessOnlyFromPlatform
        val domainPurityRule = KadranArchRules.domainDoesNotDependOnPlatform
        val scopingConfinedRule = KadranArchRules.tenantScopingDoesNotLeakIntoApplication

        "a repository using JdbcTemplate is rejected" {
            val failure = rejects(dbAccessRule, violatingClasses)

            failure shouldContain "RepositoryUsingJdbcTemplate"
            failure shouldContain "JdbcTemplate"
        }

        "a repository holding a DataSource is rejected" {
            val failure = rejects(dbAccessRule, violatingClasses)

            failure shouldContain "RepositoryUsingDataSource"
            failure shouldContain "javax.sql.DataSource"
        }

        "a repository holding a Connection is rejected" {
            val failure = rejects(dbAccessRule, violatingClasses)

            failure shouldContain "RepositoryUsingConnection"
            failure shouldContain "java.sql.Connection"
        }

        "a repository holding a DSLContext is rejected" {
            val failure = rejects(dbAccessRule, violatingClasses)

            failure shouldContain "RepositoryUsingDslContext"
            failure shouldContain "org.jooq.DSLContext"
        }

        // Second angle, sur la production celle-ci : la garde retirée — l'exemption de
        // `platform` —, la règle désigne le seul détenteur légitime d'un `DSLContext` dans le
        // dépôt. C'est la démonstration que c'est bien l'exemption qui rend la règle verte, et
        // non l'absence de tout code à matcher.
        "without the guard, the rule points at the DSLContext holder" {
            val withoutExemption =
                noClasses()
                    .that()
                    .resideInAPackage("io.korallis.kadran..")
                    .should()
                    .dependOnClassesThat(KadranArchRules.directDatabaseAccess)

            val failure = rejects(withoutExemption, productionClasses)

            failure shouldContain "TenantScopedQuery"
            failure shouldContain "org.jooq.DSLContext"
        }

        "a domain aggregate naming the tenant is rejected" {
            val failure = rejects(domainPurityRule, violatingClasses)

            failure shouldContain "AggregateNamingTheTenant"
            failure shouldContain "TenantId"
        }

        "a use case naming tenant scoping is rejected" {
            val failure = rejects(scopingConfinedRule, violatingClasses)

            failure shouldContain "UseCaseThatScopes"
            failure shouldContain "TenantScopedTable"
        }
    })
