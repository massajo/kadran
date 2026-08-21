package io.korallis.kadran.architecture

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path

/**
 * Spec §9.1 contrôle 2, second volet : aucune chaîne SQL du code de production n'interroge
 * une table sans `tenant_id`.
 *
 * Les cas de rejects ne sont pas décoratifs : le dépôt ne contient aujourd'hui aucune requête
 * SQL littérale, si bien que le balayage de production passerait tout aussi bien avec un
 * scanner cassé. Ce sont eux qui prouvent que le contrôle mord.
 */
class SqlLiteralTenantScopeTest :
    StringSpec({
        "no production SQL literal omits tenant_id" {
            SqlLiteralScanner.scanProductionSources(backendRoot()).shouldBeEmpty()
        }

        "a SELECT without tenant_id is rejected" {
            val fautif = """val q = "SELECT id, purpose FROM outing WHERE driver_id = ?" """

            val violatingClasses = SqlLiteralScanner.scanSource("Fixture.kt", fautif)

            violatingClasses shouldHaveSize 1
            violatingClasses.first().table shouldBe "outing"
        }

        "the same SELECT, scoped, is accepted" {
            val correct = """val q = "SELECT id FROM outing WHERE tenant_id = ? AND driver_id = ?" """

            SqlLiteralScanner.scanSource("Fixture.kt", correct).shouldBeEmpty()
        }

        "a join with no tenant_id reports every table" {
            val fautif =
                """
                val q = ""${'"'}
                    SELECT o.id FROM outing o
                    JOIN vehicle v ON v.id = o.vehicle_id
                ""${'"'}
                """.trimIndent()

            SqlLiteralScanner.scanSource("Fixture.kt", fautif).map { it.table } shouldBe listOf("outing", "vehicle")
        }

        // Limite assumée : le verdict porte sur la chaîne entière, pas sur chaque table. Une
        // jointure dont une seule table est scopée passe au travers. Elle ne passe pas au
        // travers du dispositif pour autant — une chaîne littérale ne s'exécute qu'avec un
        // `DSLContext`, un `JdbcTemplate` ou une `Connection`, et les trois sont confisqués
        // hors de `platform`. Ce balayage est le second filet, pas le premier.
        "a partially scoped join is the known limit of the scan" {
            val fautif =
                """
                val q = ""${'"'}
                    SELECT o.id FROM outing o
                    JOIN vehicle v ON v.id = o.vehicle_id
                    WHERE o.tenant_id = ?
                ""${'"'}
                """.trimIndent()

            SqlLiteralScanner.scanSource("Fixture.kt", fautif).shouldBeEmpty()
        }

        "un INSERT sans tenant_id est rejete" {
            val fautif = """val q = "INSERT INTO outing (id, purpose) VALUES (?, ?)" """

            SqlLiteralScanner.scanSource("Fixture.kt", fautif) shouldHaveSize 1
        }

        "un DELETE sans tenant_id est rejete" {
            val fautif = """val q = "DELETE FROM outing WHERE id = ?" """

            SqlLiteralScanner.scanSource("Fixture.kt", fautif) shouldHaveSize 1
        }

        "un UPDATE sans tenant_id est rejete" {
            val fautif = """val q = "UPDATE outing SET purpose = ? WHERE id = ?" """

            SqlLiteralScanner.scanSource("Fixture.kt", fautif) shouldHaveSize 1
        }

        "les tables de controle explicitement listees restent permises" {
            val baseline = """val q = "SELECT application FROM schema_baseline" """

            SqlLiteralScanner.scanSource("Fixture.kt", baseline).shouldBeEmpty()
        }

        "une phrase ordinaire n'est pas prise pour du SQL" {
            val innocent = """val message = "3 lignes importees from bolt vers le lot courant" """

            SqlLiteralScanner.scanSource("Fixture.kt", innocent).shouldBeEmpty()
        }
    })

/**
 * Racine de la construction Gradle du back. Le répertoire de travail d'une tâche `Test` est
 * celui du projet — `backend/app` — d'où la remontée jusqu'au `settings.gradle.kts`, qui
 * survit à un déplacement du module.
 */
private fun backendRoot(): Path {
    val depart = Path.of("").toAbsolutePath()
    var courant: Path? = depart
    while (courant != null && !Files.exists(courant.resolve("settings.gradle.kts"))) {
        courant = courant.parent
    }
    return requireNotNull(courant) { "racine de la construction Gradle introuvable depuis $depart" }
}
