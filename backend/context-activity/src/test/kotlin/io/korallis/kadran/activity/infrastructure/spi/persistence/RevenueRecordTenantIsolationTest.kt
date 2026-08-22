package io.korallis.kadran.activity.infrastructure.spi.persistence

import io.korallis.kadran.activity.domain.model.ExternalRef
import io.korallis.kadran.activity.domain.model.RevenueBreakdown
import io.korallis.kadran.activity.domain.model.RevenueRecord
import io.korallis.kadran.activity.domain.model.RevenueRecordId
import io.korallis.kadran.activity.domain.model.RevenueRecordJson
import io.korallis.kadran.activity.domain.spi.RevenueRecordRepository
import io.korallis.kadran.core.Grain
import io.korallis.kadran.core.Money
import io.korallis.kadran.core.PlatformId
import io.korallis.kadran.core.TenantId
import io.korallis.kadran.core.WorkPeriod
import io.korallis.kadran.platform.persistence.TenantScopedQuery
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.DirectoryResourceAccessor
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import java.util.Currency
import io.korallis.kadran.platform.tenancy.TenantId as ScopeId

/**
 * Contrôle 3 de la spec §9.1 pour `revenue_record` (KDN-35) : deux exploitants, des données
 * croisées, aucune fuite par aucune méthode publique du repository — même patron que
 * `IdentityTenantIsolationTest` (KDN-27), sur PostgreSQL 18 réel, **jamais H2**.
 *
 * Le changelog est celui de `app` : le test applique la migration livrée, `revenue_record`
 * comprise, plutôt qu'un DDL recopié qui divergerait dès le premier ajout de colonne.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RevenueRecordTenantIsolationTest {
    private lateinit var dsl: DSLContext

    private val alice = TenantId.of("aaaaaaaa-0000-0000-0000-000000000001")
    private val bob = TenantId.of("bbbbbbbb-0000-0000-0000-000000000002")
    private val eur: Currency = Currency.getInstance("EUR")
    private val recordedAt: Instant = Instant.parse("2026-08-17T08:00:00Z")

    @BeforeAll
    fun migrate() {
        openConnection().use { migrationConnection ->
            val database =
                DatabaseFactory
                    .getInstance()
                    .findCorrectDatabaseImplementation(JdbcConnection(migrationConnection))
            Liquibase(CHANGELOG, DirectoryResourceAccessor(changelogRoot().toFile()), database).use {
                it.update(Contexts(), LabelExpression())
            }
        }
        connection = openConnection()
    }

    @AfterAll
    fun closeConnection() {
        connection.close()
    }

    @BeforeEach
    fun resetBusinessTables() {
        dsl = DSL.using(connection, SQLDialect.POSTGRES)
        dsl.execute("TRUNCATE kadran.revenue_record, kadran.tenant CASCADE")
        seedTenant(alice)
        seedTenant(bob)
    }

    // ------------------------------------------------- critère d'acceptation n°1 : aucune fuite

    @Test
    fun `a tenant listing its revenue records never sees another tenant's`() {
        val aliceRecord = recordUberInvoice(alice)
        val bobRecord = recordUberInvoice(bob)

        repository(alice).findAll().map { it.id } shouldContainExactly listOf(aliceRecord)
        repository(bob).findAll().map { it.id } shouldContainExactly listOf(bobRecord)
    }

    @Test
    fun `a foreign revenue record is unreachable by its identifier, even when it is known`() {
        val bobRecord = recordUberInvoice(bob)

        repository(alice).findById(bobRecord).shouldBeNull()
    }

    @Test
    fun `deleting a foreign revenue record deletes nothing`() {
        val bobRecord = recordUberInvoice(bob)

        repository(alice).deleteById(bobRecord) shouldBe false
        repository(bob).findById(bobRecord)!!.platform shouldBe PlatformId.UBER
        repository(bob).deleteById(bobRecord) shouldBe true
        repository(bob).findAll().shouldBeEmpty()
    }

    @Test
    fun `writing a row that names another tenant is refused before it reaches the database`() {
        val foreign = uberInvoiceRecord(tenantId = bob)

        shouldThrow<IllegalArgumentException> { repository(alice).save(foreign, RevenueRecordJson.empty()) }
        repository(alice).findAll().shouldBeEmpty()
    }

    // ------------------------------------------------------------------------- le schéma lui-même

    @Test
    fun `revenue_record leads its primary key with tenant_id, per the composite index rule`() {
        val leadingColumn =
            dsl
                .fetch(
                    """
                    SELECT a.attname
                    FROM pg_index x
                    JOIN pg_class c ON c.oid = x.indrelid
                    JOIN pg_namespace n ON n.oid = c.relnamespace
                    JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum = x.indkey[0]
                    WHERE n.nspname = 'kadran' AND c.relname = 'revenue_record' AND x.indisprimary
                    """.trimIndent(),
                ).map { it.get(0, String::class.java) }
                .single()

        leadingColumn shouldBe "tenant_id"
    }

    @Test
    fun `platform_extras carries the GIN index required to keep it queryable`() {
        val indexDef =
            dsl
                .fetch(
                    """
                    SELECT indexdef FROM pg_indexes
                    WHERE schemaname = 'kadran' AND tablename = 'revenue_record'
                      AND indexdef ILIKE '%platform_extras%'
                    """.trimIndent(),
                ).map { it.get(0, String::class.java) }
                .single()

        indexDef.contains("USING gin", ignoreCase = true) shouldBe true
    }

    // ------------------------------------------------------------------------------ round-trip

    @Test
    fun `a revenue record round-trips through storage with its raw_payload persisted but not exposed`() {
        val id = RevenueRecordId.next()
        val record = uberInvoiceRecord(tenantId = alice, id = id)
        val rawPayload =
            RevenueRecordJson.Obj(
                mapOf("NuméroFacture" to RevenueRecordJson.Str("A-99-9999-9999999")),
            )

        repository(alice).save(record, rawPayload)

        val reloaded = repository(alice).findById(id)
        reloaded shouldBe record

        // La colonne existe et porte bien le contenu ecrit (zone "brut", conservee pour rejeu) :
        // ce que le domaine ne porte jamais, la base le garde quand meme.
        val storedRawPayload =
            dsl
                .fetch(
                    "SELECT raw_payload FROM kadran.revenue_record WHERE tenant_id = ? AND id = ?",
                    alice.value,
                    id.value,
                ).map { it.get(0, String::class.java) }
                .single()
        storedRawPayload.contains("NuméroFacture") shouldBe true
    }

    // ------------------------------------------------------------------------------- utilitaires

    private fun scoped(tenantId: TenantId): TenantScopedQuery =
        TenantScopedQuery.forTenant(ScopeId(tenantId.value), dsl)

    private fun repository(tenantId: TenantId): RevenueRecordRepository = JooqRevenueRecordRepository(scoped(tenantId))

    /**
     * Un SIREN distinct par exploitant : `ux_tenant_siren_open` (KDN-27) est un index unique
     * partiel, pas un `CHECK` — il a survécu à KDN-137 et refuserait deux tenants ouverts
     * partageant le même SIREN.
     */
    private fun seedTenant(tenantId: TenantId) {
        val siren = if (tenantId == alice) "732829320" else "404833048"
        dsl.execute(
            """
            INSERT INTO kadran.tenant (tenant_id, legal_name, siren, onboarding_status)
            VALUES (?, 'Kadran SASU', ?, 'COMPLETED')
            """.trimIndent(),
            tenantId.value,
            siren,
        )
    }

    private fun uberInvoiceRecord(
        tenantId: TenantId,
        id: RevenueRecordId = RevenueRecordId.next(),
    ): RevenueRecord =
        RevenueRecord(
            id = id,
            tenantId = tenantId,
            platform = PlatformId.UBER,
            grain = Grain.TRIP,
            coverage = WorkPeriod(recordedAt, recordedAt),
            externalRefs = setOf(ExternalRef("NuméroFacture", "A-99-9999-9999999")),
            amounts =
                RevenueBreakdown(
                    gross = Money(2_000, eur),
                    net = Money(1_700, eur),
                    commission = Money(300, eur),
                    tips = Money(150, eur),
                    incentives = Money(0, eur),
                    surcharges = Money(0, eur),
                ),
            vat = null,
            counts = null,
            platformExtras = RevenueRecordJson.empty(),
            provenance = emptySet(),
        )

    private fun recordUberInvoice(tenantId: TenantId): RevenueRecordId {
        val record = uberInvoiceRecord(tenantId)
        repository(tenantId).save(record, RevenueRecordJson.empty())
        return record.id
    }

    private companion object {
        const val CHANGELOG = "db/changelog/db.changelog-master.xml"

        lateinit var connection: Connection

        fun openConnection(): Connection =
            DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)

        @Container
        @JvmStatic
        val postgres =
            PostgreSQLContainer("postgres:18-alpine")
                .withInitScript("db/bootstrap/create-schemas.sql")

        /**
         * Racine des ressources de `app`, où vit le changelog (spec §11.1). On remonte jusqu'au
         * `settings.gradle.kts` plutôt que d'écrire un chemin relatif au module — même utilitaire
         * que `IdentityTenantIsolationTest` (KDN-27), copié plutôt que partagé : voir la note de
         * `RevenueRecordTables` sur la non-mutualisation de fichiers avec `Outing` (KDN-34).
         */
        fun changelogRoot(): Path {
            var current: Path? = Path.of("").toAbsolutePath()
            while (current != null && !Files.exists(current.resolve("settings.gradle.kts"))) {
                current = current.parent
            }
            val root = requireNotNull(current) { "racine de la construction Gradle introuvable" }
            return root.resolve("app/src/main/resources")
        }
    }
}
