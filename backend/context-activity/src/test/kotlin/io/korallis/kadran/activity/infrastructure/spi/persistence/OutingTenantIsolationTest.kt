package io.korallis.kadran.activity.infrastructure.spi.persistence

import io.korallis.kadran.activity.domain.model.MileageSource
import io.korallis.kadran.activity.domain.model.Outing
import io.korallis.kadran.activity.domain.model.OutingDetails
import io.korallis.kadran.activity.domain.model.OutingId
import io.korallis.kadran.activity.domain.model.OutingTiming
import io.korallis.kadran.activity.domain.model.TripPurpose
import io.korallis.kadran.activity.domain.model.with
import io.korallis.kadran.activity.domain.spi.OutingRepository
import io.korallis.kadran.core.Distance
import io.korallis.kadran.core.Money
import io.korallis.kadran.core.RevenueRecordId
import io.korallis.kadran.core.TenantId
import io.korallis.kadran.core.WorkPeriod
import io.korallis.kadran.platform.persistence.TenantScopedQuery
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
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
import java.time.LocalDate
import io.korallis.kadran.platform.tenancy.TenantId as ScopeId

/**
 * Contrôle 3 de la spec §9.1 pour `outing` (KDN-34), même patron que
 * `IdentityTenantIsolationTest` (KDN-27) : deux exploitants, des données croisées, aucune
 * fuite par aucune méthode publique du repository.
 *
 * PostgreSQL 18 réel via Testcontainers, **jamais H2** — le schéma repose sur une clé
 * étrangère composite `(tenant_id, id)` implicite (clé primaire) et une contrainte
 * `REFERENCES kadran.tenant`, dont H2 ne reproduit ni la syntaxe ni le comportement.
 *
 * Les lignes de `kadran.tenant` que ce test insère le sont **en SQL brut**, pas via
 * `context-identity` : les deux contextes ne se dépendent pas l'un l'autre (règle ArchUnit
 * `un contexte borne ne depend pas d'un autre contexte borne`), et ce test n'a besoin que
 * d'une ligne satisfaisant la contrainte `outing.tenant_id REFERENCES tenant.tenant_id`, pas
 * d'un agrégat `Tenant` complet.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OutingTenantIsolationTest {
    private lateinit var dsl: DSLContext

    private val alice = TenantId.of("aaaaaaaa-0000-0000-0000-000000000011")
    private val bob = TenantId.of("bbbbbbbb-0000-0000-0000-000000000012")

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
    fun resetTables() {
        dsl = DSL.using(connection, SQLDialect.POSTGRES)
        dsl.execute("TRUNCATE kadran.outing, kadran.tenant CASCADE")
        seedTenant(alice, "732829320")
        seedTenant(bob, "404833048")
    }

    // ---------------------------------------------------------------- le schéma lui-même

    /** `CLAUDE.md` §2.3 : `tenant_id` non-nullable, en tête de chaque index composite. */
    @Test
    fun `the outing table carries a non-nullable tenant_id`() {
        val isNullable =
            dsl
                .fetchOne(
                    """
                    SELECT is_nullable FROM information_schema.columns
                    WHERE table_schema = 'kadran' AND table_name = 'outing' AND column_name = 'tenant_id'
                    """.trimIndent(),
                )!!
                .get(0, String::class.java)

        isNullable shouldBe "NO"
    }

    @Test
    fun `every composite index on outing leads with tenant_id`() {
        val leadingColumns =
            dsl
                .fetch(
                    """
                    SELECT a.attname AS first_column
                    FROM pg_index x
                    JOIN pg_class c ON c.oid = x.indrelid
                    JOIN pg_class i ON i.oid = x.indexrelid
                    JOIN pg_namespace n ON n.oid = c.relnamespace
                    JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum = x.indkey[0]
                    WHERE n.nspname = 'kadran' AND c.relname = 'outing' AND x.indnatts > 1
                    """.trimIndent(),
                ).map { it.get(0, String::class.java) }

        leadingColumns.shouldNotBeEmpty()
        leadingColumns.forEach { it shouldBe "tenant_id" }
    }

    // ------------------------------------------------- critère d'acceptation n°4 : isolation

    @Test
    fun `a tenant listing its outings never sees another tenant's`() {
        val aliceOuting = recordOuting(alice, LocalDate.of(2026, 6, 2))
        val bobOuting = recordOuting(bob, LocalDate.of(2026, 6, 2))

        outings(alice).findAll().map { it.id } shouldContainExactly listOf(aliceOuting)
        outings(bob).findAll().map { it.id } shouldContainExactly listOf(bobOuting)
    }

    @Test
    fun `a foreign outing is unreachable by its identifier, even when it is known`() {
        val bobOuting = recordOuting(bob, LocalDate.of(2026, 6, 2))

        outings(alice).findById(bobOuting).shouldBeNull()
    }

    @Test
    fun `listing by business day never crosses tenants`() {
        val day = LocalDate.of(2026, 6, 2)
        recordOuting(alice, day)
        recordOuting(bob, day)

        outings(alice).findByBusinessDay(day) shouldHaveSize 1
        outings(bob).findByBusinessDay(day) shouldHaveSize 1
    }

    @Test
    fun `deleting a foreign outing deletes nothing`() {
        val bobOuting = recordOuting(bob, LocalDate.of(2026, 6, 2))

        outings(alice).deleteById(bobOuting) shouldBe false
        outings(bob).findById(bobOuting) shouldBe outings(bob).findAll().single()
        outings(bob).deleteById(bobOuting) shouldBe true
        outings(bob).findAll().shouldBeEmpty()
    }

    @Test
    fun `a round trip through the repository preserves every field the table persists`() {
        val window =
            WorkPeriod(
                from = Instant.parse("2026-06-02T20:00:00Z"),
                to = Instant.parse("2026-06-03T01:00:00Z"),
            )
        val linkedRevenue = RevenueRecordId.next()
        val transition =
            Outing
                .record(
                    id = OutingId.next(),
                    tenantId = alice,
                    timing = OutingTiming.fromWindow(window),
                    distance = Distance(130_700),
                    purpose = TripPurpose.PROFESSIONNEL,
                    source = MileageSource.DRIVERSNOTE,
                    recordedAt = Instant.parse("2026-06-04T09:00:00Z"),
                ).with(
                    OutingDetails(
                        startLabel = "Home",
                        endLabel = "Gare de Lyon",
                        mileageAllowance = Money.euroCents(6_180),
                        linkedRevenue = linkedRevenue,
                    ),
                )
        outings(alice).save(transition.state)

        val reloaded = outings(alice).findById(transition.state.id)!!

        reloaded.tenantId shouldBe alice
        reloaded.businessDay shouldBe LocalDate.of(2026, 6, 2)
        reloaded.window shouldBe window
        reloaded.spansMidnight shouldBe true
        reloaded.distance shouldBe Distance(130_700)
        reloaded.purpose shouldBe TripPurpose.PROFESSIONNEL
        reloaded.source shouldBe MileageSource.DRIVERSNOTE
        reloaded.mileageAllowance shouldBe Money.euroCents(6_180)
        reloaded.linkedRevenue shouldBe linkedRevenue
        // La table ne persiste pas les labels (PII non reduite, KDN-47) : ils reviennent nuls.
        reloaded.startLabel.shouldBeNull()
        reloaded.endLabel.shouldBeNull()
    }

    // ------------------------------------------------------------------------- utilitaires

    private fun scoped(tenantId: TenantId): TenantScopedQuery =
        TenantScopedQuery.forTenant(ScopeId(tenantId.value), dsl)

    private fun outings(tenantId: TenantId): OutingRepository = JooqOutingRepository(scoped(tenantId))

    private fun recordOuting(
        tenantId: TenantId,
        businessDay: LocalDate,
    ): OutingId {
        val outing =
            Outing
                .record(
                    id = OutingId.next(),
                    tenantId = tenantId,
                    timing = OutingTiming.withoutWindow(businessDay),
                    distance = Distance(42_000),
                    purpose = TripPurpose.PROFESSIONNEL,
                    source = MileageSource.DRIVERSNOTE,
                    recordedAt = Instant.parse("2026-06-04T09:00:00Z"),
                ).state
        outings(tenantId).save(outing)
        return outing.id
    }

    /** Ligne minimale satisfaisant `outing.tenant_id REFERENCES tenant.tenant_id` — SQL brut. */
    private fun seedTenant(
        tenantId: TenantId,
        siren: String,
    ) {
        dsl.execute(
            """
            INSERT INTO kadran.tenant (tenant_id, legal_name, siren, onboarding_status)
            VALUES ('${tenantId.value}', 'Exploitant de test', '$siren', 'COMPLETED')
            """.trimIndent(),
        )
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
