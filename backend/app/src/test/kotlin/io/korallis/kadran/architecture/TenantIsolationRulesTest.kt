package io.korallis.kadran.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest

/**
 * Isolation multi-tenant — `CLAUDE.md` §2.3, spec §9.1 contrôle 2.
 *
 * L'ADR-001 écarte le Row-Level Security : aucune policy PostgreSQL ne rattrapera un
 * prédicat oublié. Ces règles sont la moitié préventive du dispositif — l'autre moitié étant
 * les tests d'isolation par table (contrôle 3) et le journal d'audit (contrôle 4).
 *
 * `DoNotIncludeTests` : un test a le droit d'ouvrir une connexion — `SchemaMigrationTest`
 * interroge `databasechangelog` au `JdbcTemplate`, et c'est précisément son travail. Ce qu'on
 * verrouille, c'est le code de production. Les classes de violation délibérée qui prouvent
 * ces règles ([ArchRulesRejectViolationsTest]) vivent dans les sources de test, et sont
 * exclues d'ici par la même option.
 */
@AnalyzeClasses(
    packages = ["io.korallis.kadran"],
    importOptions = [ImportOption.DoNotIncludeTests::class],
)
class TenantIsolationRulesTest {
    @ArchTest
    fun `database is accessed only from platform`(classes: JavaClasses) {
        KadranArchRules.databaseAccessOnlyFromPlatform.check(classes)
    }

    @ArchTest
    fun `tenant scoping does not leak into the application layer`(classes: JavaClasses) {
        KadranArchRules.tenantScopingDoesNotLeakIntoApplication.check(classes)
    }
}
