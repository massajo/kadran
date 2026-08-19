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
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
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

        applied shouldBe listOf("20260819_KDN-14_01")
        jdbc.queryForObject("SELECT application FROM schema_baseline", String::class.java) shouldBe "kadran"
    }

    @Test
    fun `le rollback du premier changeset defait la table de controle`() {
        withLiquibase { liquibase ->
            liquibase.rollback(1, null, Contexts(), LabelExpression())
            tableExists("schema_baseline") shouldBe false

            liquibase.update(Contexts(), LabelExpression())
            tableExists("schema_baseline") shouldBe true
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

        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }
}
