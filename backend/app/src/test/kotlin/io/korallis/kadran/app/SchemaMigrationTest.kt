package io.korallis.kadran.app

import io.kotest.matchers.shouldBe
import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import javax.sql.DataSource

/**
 * Vérifie que le changelog s'applique sur une base **vierge**, et que le rollback du
 * premier changeset la ramène à son état initial.
 *
 * PostgreSQL réel via Testcontainers, jamais H2 : JSONB, partitionnement et types Postgres
 * ne s'y comportent pas pareil (spec §10.4). Le conteneur est branché sur le contexte Spring
 * par `@ServiceConnection`, si bien que c'est bien la configuration de `application.yaml`
 * qui est exercée — et non un montage de test parallèle.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class SchemaMigrationTest {
    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Autowired
    private lateinit var dataSource: DataSource

    @Test
    fun `le changelog s'applique integralement sur une base vierge`() {
        val applied =
            jdbc.queryForList(
                "SELECT id FROM databasechangelog WHERE author = 'kadran' ORDER BY orderexecuted",
                String::class.java,
            )

        applied shouldBe
            listOf(
                "20260819_KDN-14_01",
                "20260821_KDN-27_01",
                "20260821_KDN-27_02",
                "20260821_KDN-27_03",
                "20260821_KDN-27_04",
            )
        jdbc.queryForObject("SELECT application FROM schema_baseline", String::class.java) shouldBe "kadran"
    }

    /**
     * Le rollback est **exécuté**, pas seulement écrit. Il l'est en deux temps parce que
     * l'ordre inverse est le seul qui respecte les clés étrangères : `membership` référence
     * `driver`, qui référence `tenant`. Un `--rollback` qui ne serait vérifié que par lecture
     * laisserait passer exactement ce genre d'inversion.
     */
    @Test
    fun `rolling back leaves the database as the previous changeset left it`() {
        withLiquibase { liquibase ->
            liquibase.rollback(IDENTITY_CHANGESETS, null, Contexts(), LabelExpression())
            IDENTITY_TABLES.forEach { tableExists(it) shouldBe false }
            tableExists("schema_baseline") shouldBe true

            liquibase.rollback(1, null, Contexts(), LabelExpression())
            tableExists("schema_baseline") shouldBe false

            liquibase.update(Contexts(), LabelExpression())
            tableExists("schema_baseline") shouldBe true
            IDENTITY_TABLES.forEach { tableExists(it) shouldBe true }
        }
    }

    private fun tableExists(name: String): Boolean =
        jdbc.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean::class.java, name) == true

    private fun withLiquibase(block: (Liquibase) -> Unit) {
        dataSource.connection.use { connection ->
            val database =
                DatabaseFactory
                    .getInstance()
                    .findCorrectDatabaseImplementation(JdbcConnection(connection))
            Liquibase(CHANGELOG, ClassLoaderResourceAccessor(), database).use(block)
        }
    }

    private companion object {
        const val CHANGELOG = "db/changelog/db.changelog-master.xml"

        /** Les quatre tables métier de KDN-27 et le nombre de changesets qui les apportent. */
        val IDENTITY_TABLES = listOf("tenant", "driver", "membership", "vehicle")
        const val IDENTITY_CHANGESETS = 4

        // Testcontainers 2.x a deplace le conteneur dans `org.testcontainers.postgresql` et
        // abandonne le parametre de type recursif : `PostgreSQLContainer<Nothing>` devient
        // `PostgreSQLContainer`. L'ancienne classe existe encore, mais depreciee.
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:18-alpine")
    }
}
