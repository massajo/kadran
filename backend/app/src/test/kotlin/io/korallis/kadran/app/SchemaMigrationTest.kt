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
                "SELECT id FROM kadran.databasechangelog WHERE author = 'kadran' ORDER BY orderexecuted",
                String::class.java,
            )

        applied shouldBe
            listOf(
                "20260819_KDN-14_01",
                "20260821_KDN-27_01",
                "20260821_KDN-27_02",
                "20260821_KDN-27_03",
                "20260821_KDN-27_04",
                "20260822_KDN-136_01",
            )
        // `schema_baseline` a été déplacée dans `kadran` par KDN-136 : non qualifiée, cette
        // requête chercherait `public.schema_baseline`, qui n'existe plus.
        jdbc.queryForObject(
            "SELECT application FROM kadran.schema_baseline",
            String::class.java,
        ) shouldBe "kadran"
    }

    /**
     * Le rollback est **exécuté**, pas seulement écrit. Il l'est en trois temps parce que
     * l'ordre inverse est le seul qui respecte les dépendances :
     *
     * 1. Le changeset KDN-136 d'abord — son rollback ramène les cinq tables dans `public`
     *    (il ne touche plus aux schémas eux-mêmes, créés hors Liquibase). Sans ce premier
     *    pas, les `--rollback` des changesets KDN-27 (`DROP TABLE tenant`, non qualifiés)
     *    chercheraient une table dans `public` alors qu'elle vit encore dans `kadran`, et
     *    échoueraient.
     * 2. Les quatre changesets KDN-27, dans l'ordre inverse des clés étrangères : `membership`
     *    référence `driver`, qui référence `tenant`.
     * 3. Le changeset KDN-14, seul, pour vérifier qu'il se détache aussi proprement.
     *
     * Un `--rollback` qui ne serait vérifié que par lecture laisserait passer une inversion de
     * cet ordre.
     */
    @Test
    fun `rolling back leaves the database as the previous changeset left it`() {
        withLiquibase { liquibase ->
            liquibase.rollback(SCHEMA_CHANGESETS + IDENTITY_CHANGESETS, null, Contexts(), LabelExpression())
            IDENTITY_TABLES.forEach { tableExists("public", it) shouldBe false }
            tableExists("public", "schema_baseline") shouldBe true

            liquibase.rollback(1, null, Contexts(), LabelExpression())
            tableExists("public", "schema_baseline") shouldBe false

            liquibase.update(Contexts(), LabelExpression())
            tableExists("kadran", "schema_baseline") shouldBe true
            IDENTITY_TABLES.forEach { tableExists("kadran", it) shouldBe true }
        }
    }

    private fun tableExists(
        schema: String,
        name: String,
    ): Boolean = jdbc.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean::class.java, "$schema.$name") == true

    private fun withLiquibase(block: (Liquibase) -> Unit) {
        dataSource.connection.use { connection ->
            val database =
                DatabaseFactory
                    .getInstance()
                    .findCorrectDatabaseImplementation(JdbcConnection(connection))
            // Cette instance ne sait rien de `spring.liquibase.liquibase-schema` (KDN-136) —
            // c'est une propriété que seule l'autoconfiguration Spring Boot lit, pas l'API
            // liquibase-core nue. Sans cette ligne, elle chercherait ses propres tables de
            // suivi dans le schéma par défaut de la connexion (`public` pour le rôle de test),
            // ne trouverait pas celles du contexte Spring déjà démarré (dans `kadran`), et en
            // recréerait une seconde, vide — exactement le bug que KDN-136 a corrigé côté
            // application, rejoué ici côté test faute de cette ligne.
            database.liquibaseSchemaName = LIQUIBASE_SCHEMA
            Liquibase(CHANGELOG, ClassLoaderResourceAccessor(), database).use(block)
        }
    }

    private companion object {
        const val CHANGELOG = "db/changelog/db.changelog-master.xml"
        const val LIQUIBASE_SCHEMA = "kadran"

        /** Les quatre tables métier de KDN-27 et le nombre de changesets qui les apportent. */
        val IDENTITY_TABLES = listOf("tenant", "driver", "membership", "vehicle")
        const val IDENTITY_CHANGESETS = 4

        /** Le changeset de séparation des schémas (KDN-136), appliqué après les quatre ci-dessus. */
        const val SCHEMA_CHANGESETS = 1

        // Testcontainers 2.x a deplace le conteneur dans `org.testcontainers.postgresql` et
        // abandonne le parametre de type recursif : `PostgreSQLContainer<Nothing>` devient
        // `PostgreSQLContainer`. L'ancienne classe existe encore, mais depreciee.
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres =
            // Crée kadran/audit et épingle le search_path du rôle avant la première
            // connexion (KDN-136) — sans volume persistant ici, rejoué à chaque conteneur.
            PostgreSQLContainer("postgres:18-alpine")
                .withInitScript("db/bootstrap/create-schemas.sql")
    }
}
