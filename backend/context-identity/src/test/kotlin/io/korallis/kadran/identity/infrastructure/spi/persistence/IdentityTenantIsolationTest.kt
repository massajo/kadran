package io.korallis.kadran.identity.infrastructure.spi.persistence

import io.korallis.kadran.core.DriverId
import io.korallis.kadran.core.TenantId
import io.korallis.kadran.identity.domain.model.AccountId
import io.korallis.kadran.identity.domain.model.Driver
import io.korallis.kadran.identity.domain.model.DriverName
import io.korallis.kadran.identity.domain.model.EnergySource
import io.korallis.kadran.identity.domain.model.LegalName
import io.korallis.kadran.identity.domain.model.MemberRoleChanged
import io.korallis.kadran.identity.domain.model.Membership
import io.korallis.kadran.identity.domain.model.MembershipId
import io.korallis.kadran.identity.domain.model.MembershipRole
import io.korallis.kadran.identity.domain.model.OwnershipMode
import io.korallis.kadran.identity.domain.model.Plate
import io.korallis.kadran.identity.domain.model.Siren
import io.korallis.kadran.identity.domain.model.Tenant
import io.korallis.kadran.identity.domain.model.Vehicle
import io.korallis.kadran.identity.domain.model.VehicleId
import io.korallis.kadran.identity.domain.spi.DriverRepository
import io.korallis.kadran.identity.domain.spi.MembershipRepository
import io.korallis.kadran.identity.domain.spi.TenantRepository
import io.korallis.kadran.identity.domain.spi.VehicleRepository
import io.korallis.kadran.platform.persistence.TenantScopedQuery
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
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
import java.util.UUID
import io.korallis.kadran.platform.tenancy.TenantId as ScopeId

/**
 * Contrôle 3 de la spec §9.1 pour les quatre tables de KDN-27 : deux exploitants, des données
 * croisées, **aucune fuite par aucune méthode publique** des repositories.
 *
 * Depuis l'ADR-001 il n'y a pas de Row-Level Security : l'isolation est une propriété du code,
 * et rien en base ne rattrape un prédicat oublié. Ce test est donc la seule preuve empirique
 * que le dispositif tient. Il porte sur PostgreSQL 18 réel, **jamais H2** — le schéma repose
 * sur des index uniques partiels, des clés étrangères composites et des `CHECK` à expression
 * régulière, dont H2 ne reproduit ni la syntaxe ni le comportement.
 *
 * Le changelog est celui de `app` : le test applique la migration livrée plutôt qu'un DDL
 * recopié, qui divergerait dès le premier ajout de colonne.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IdentityTenantIsolationTest {
    private lateinit var dsl: DSLContext

    private val alice = TenantId.of("aaaaaaaa-0000-0000-0000-000000000001")
    private val bob = TenantId.of("bbbbbbbb-0000-0000-0000-000000000002")

    /**
     * La migration s'exécute sur une connexion **dédiée** : fermer un `Liquibase` ferme la
     * connexion qu'il porte, et la connexion de travail ne survivrait pas à la migration.
     */
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
        // Qualifiées par `kadran` depuis KDN-136 : les quatre tables ne vivent plus dans
        // `public`, où le search_path par défaut de la connexion irait sinon les chercher.
        dsl.execute("TRUNCATE kadran.membership, kadran.vehicle, kadran.driver, kadran.tenant CASCADE")
    }

    // ---------------------------------------------------------------- le schéma lui-même

    /**
     * Une table de `kadran` dépourvue de `tenant_id NOT NULL` fait échouer le build, **quel que
     * soit le contexte qui l'a créée**. Le filtre est une liste d'exemptions et non une liste de
     * tables métier : c'est le seul sens qui protège les tables qui n'existent pas encore.
     *
     * Le balayage porte sur `kadran`, le schéma opérationnel (KDN-136) — pas sur `public`, où
     * ne vivent plus que les tables de Liquibase, ni sur `audit`, dont le modèle de permissions
     * et le cycle de vie sont distincts (spec §8.4, §9.3, ADR-013).
     */
    @Test
    fun `every business table carries a non-nullable tenant_id`() {
        val unscoped =
            dsl
                .fetch(
                    """
                    SELECT t.table_name
                    FROM information_schema.tables t
                    LEFT JOIN information_schema.columns c
                           ON c.table_schema = t.table_schema
                          AND c.table_name = t.table_name
                          AND c.column_name = 'tenant_id'
                          AND c.is_nullable = 'NO'
                    WHERE t.table_schema = 'kadran'
                      AND t.table_type = 'BASE TABLE'
                      AND t.table_name NOT IN ($QUOTED_EXEMPTIONS)
                      AND c.column_name IS NULL
                    ORDER BY t.table_name
                    """.trimIndent(),
                ).map { it.get(0, String::class.java) }

        unscoped.shouldBeEmpty()
        inspectedTables() shouldContainAll IDENTITY_TABLES
    }

    /**
     * `CLAUDE.md` §2.3 : `tenant_id` **en tête** de chaque index composite. Un index qui commence
     * ailleurs n'est pas seulement moins sélectif — il signale un accès pensé sans l'exploitant,
     * donc un prédicat d'isolation ajouté après coup.
     *
     * Les index à une seule colonne sont hors sujet : `ux_tenant_siren_open` porte le SIREN seul,
     * et c'est voulu — l'unicité d'une entité juridique est globale, pas par exploitant.
     */
    @Test
    fun `every composite index leads with tenant_id`() {
        val offenders =
            dsl
                .fetch(
                    """
                    SELECT c.relname AS table_name, i.relname AS index_name, a.attname AS first_column
                    FROM pg_index x
                    JOIN pg_class c ON c.oid = x.indrelid
                    JOIN pg_class i ON i.oid = x.indexrelid
                    JOIN pg_namespace n ON n.oid = c.relnamespace
                    JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum = x.indkey[0]
                    WHERE n.nspname = 'kadran'
                      AND c.relkind = 'r'
                      AND c.relname NOT IN ($QUOTED_EXEMPTIONS)
                      AND x.indnatts > 1
                      AND a.attname <> 'tenant_id'
                    ORDER BY c.relname, i.relname
                    """.trimIndent(),
                ).map { "${it.get(0, String::class.java)}.${it.get(1, String::class.java)}" }

        offenders.shouldBeEmpty()
        inspectedTables() shouldContainAll IDENTITY_TABLES
    }

    // ------------------------------------------------- critère d'acceptation n°1 : aucune fuite

    @Test
    fun `a tenant listing its drivers never sees another tenant's`() {
        val aliceDriver = registerDriver(alice, "Alice Martin")
        val bobDriver = registerDriver(bob, "Bob Durand")

        drivers(alice).findAll().map { it.id } shouldContainExactly listOf(aliceDriver)
        drivers(bob).findAll().map { it.id } shouldContainExactly listOf(bobDriver)
    }

    @Test
    fun `a foreign driver is unreachable by its identifier, even when it is known`() {
        val bobDriver = registerDriver(bob, "Bob Durand")

        drivers(alice).findById(bobDriver).shouldBeNull()
    }

    @Test
    fun `deleting a foreign driver deletes nothing`() {
        val bobDriver = registerDriver(bob, "Bob Durand")

        drivers(alice).deleteById(bobDriver) shouldBe false
        drivers(bob).findById(bobDriver)!!.displayName shouldBe DriverName.of("Bob Durand")
        drivers(bob).deleteById(bobDriver) shouldBe true
    }

    @Test
    fun `the tenant row is itself scoped, so each tenant reads only its own`() {
        registerTenant(alice, "Alice VTC", "732829320")
        registerTenant(bob, "Bob Transports", "404833048")

        tenants(alice).findCurrent()!!.legalName shouldBe LegalName.of("Alice VTC")
        tenants(bob).findCurrent()!!.legalName shouldBe LegalName.of("Bob Transports")
        tenants(TenantId.of("cccccccc-0000-0000-0000-000000000003")).findCurrent().shouldBeNull()
    }

    @Test
    fun `memberships and vehicles are isolated by every read the port offers`() {
        val aliceDriver = registerDriver(alice, "Alice Martin")
        val bobDriver = registerDriver(bob, "Bob Durand")
        val aliceMembership = openMembership(alice, aliceDriver, MembershipRole.OWNER, HIRED_AT)
        val bobMembership = openMembership(bob, bobDriver, MembershipRole.DRIVER, HIRED_AT)
        registerVehicle(alice, "AA-123-AA")
        val bobVehicle = registerVehicle(bob, "BB-456-BB")

        memberships(alice).findById(bobMembership).shouldBeNull()
        memberships(alice).findByDriver(bobDriver).shouldBeEmpty()
        memberships(alice).findOpen().map { it.id } shouldContainExactly listOf(aliceMembership)
        memberships(bob).findOpen().map { it.id } shouldContainExactly listOf(bobMembership)

        vehicles(alice).findById(bobVehicle).shouldBeNull()
        vehicles(alice).findAll().map { it.plate } shouldContainExactly listOf(Plate.of("AA-123-AA"))
        vehicles(alice).deleteById(bobVehicle) shouldBe false
        vehicles(bob).findAll() shouldHaveSize 1
    }

    @Test
    fun `an account is a cross-tenant identity, yet each tenant reads only its own membership`() {
        val account = AccountId(UUID.fromString("55555555-5555-5555-5555-555555555555"))
        val aliceDriver = registerDriver(alice, "Jean Dupont")
        val bobDriver = registerDriver(bob, "Jean Dupont")
        openMembership(alice, aliceDriver, MembershipRole.DRIVER, HIRED_AT, account)
        openMembership(bob, bobDriver, MembershipRole.DRIVER, HIRED_AT, account)

        memberships(alice).findByAccount(account).map { it.driverId } shouldContainExactly listOf(aliceDriver)
        memberships(bob).findByAccount(account).map { it.driverId } shouldContainExactly listOf(bobDriver)
    }

    // -------------------------------- critère d'acceptation n°2 : deux appartenances datées

    @Test
    fun `a driver who moved from one tenant to the next keeps both dated memberships`() {
        val account = AccountId(UUID.fromString("55555555-5555-5555-5555-555555555555"))
        val leftAt = Instant.parse("2026-06-30T22:00:00Z")
        val joinedAt = Instant.parse("2026-07-01T06:00:00Z")

        val atAlice = registerDriver(alice, "Jean Dupont")
        val first = openMembership(alice, atAlice, MembershipRole.DRIVER, HIRED_AT, account)
        val revoked = memberships(alice).findById(first)!!.revokeAt(leftAt)
        memberships(alice).save(revoked.state)

        val atBob = registerDriver(bob, "Jean Dupont")
        val second = openMembership(bob, atBob, MembershipRole.DRIVER, joinedAt, account)

        // Les deux appartenances subsistent, chacune datée, chacune chez son exploitant.
        val closed = memberships(alice).findById(first)!!
        closed.period.validFrom shouldBe HIRED_AT
        closed.period.validUntil shouldBe leftAt
        closed.isActiveAt(HIRED_AT) shouldBe true
        closed.isActiveAt(joinedAt) shouldBe false
        memberships(alice).findOpen().shouldBeEmpty()

        val open = memberships(bob).findById(second)!!
        open.period.validFrom shouldBe joinedAt
        open.period.validUntil.shouldBeNull()
        memberships(bob).findOpen() shouldHaveSize 1

        // Et l'historique d'un exploitant reste le sien : rien de l'autre n'y apparaît.
        memberships(alice).findByDriver(atAlice) shouldHaveSize 1
        memberships(bob).findByDriver(atAlice).shouldBeEmpty()
    }

    // ------------------------------------------------- critère d'acceptation n°3 : événements

    @Test
    fun `a role change is persisted and carries the event that justifies it`() {
        val driver = registerDriver(alice, "Alice Martin")
        val id = openMembership(alice, driver, MembershipRole.DRIVER, HIRED_AT)

        val transition = memberships(alice).findById(id)!!.changeRoleTo(MembershipRole.MANAGER, HIRED_AT)
        memberships(alice).save(transition.state)

        memberships(alice).findById(id)!!.role shouldBe MembershipRole.MANAGER
        transition.event shouldBe
            MemberRoleChanged(
                alice,
                id,
                MembershipRole.DRIVER,
                MembershipRole.MANAGER,
                HIRED_AT,
            )
    }

    // --------------------------------------------------- ce que le typage et la base refusent

    @Test
    fun `writing a row that names another tenant is refused before it reaches the database`() {
        registerTenant(alice, "Alice VTC", "732829320")
        val foreign = Driver.register(DriverId.next(), bob, DriverName.of("Bob Durand"))

        shouldThrow<IllegalArgumentException> { drivers(alice).save(foreign) }
        drivers(alice).findAll().shouldBeEmpty()
    }

    @Test
    fun `a membership cannot reference a driver of another tenant`() {
        registerTenant(alice, "Alice VTC", "732829320")
        val bobDriver = registerDriver(bob, "Bob Durand")
        val stolen =
            Membership
                .invite(MembershipId.next(), alice, bobDriver, MembershipRole.DRIVER, HIRED_AT)
                .state

        // Clé étrangère composite `(tenant_id, driver_id)` : la base refuse, en plus du code.
        shouldThrowAny { memberships(alice).save(stolen) }
    }

    @Test
    fun `a driver cannot hold two open memberships at once`() {
        val driver = registerDriver(alice, "Alice Martin")
        openMembership(alice, driver, MembershipRole.OWNER, HIRED_AT)

        shouldThrowAny { openMembership(alice, driver, MembershipRole.MANAGER, HIRED_AT) }
    }

    // ------------------------------------------------------------------------- utilitaires

    private fun scoped(tenantId: TenantId): TenantScopedQuery =
        TenantScopedQuery.forTenant(ScopeId(tenantId.value), dsl)

    private fun tenants(tenantId: TenantId): TenantRepository = JooqTenantRepository(scoped(tenantId))

    private fun drivers(tenantId: TenantId): DriverRepository = JooqDriverRepository(scoped(tenantId))

    private fun memberships(tenantId: TenantId): MembershipRepository = JooqMembershipRepository(scoped(tenantId))

    private fun vehicles(tenantId: TenantId): VehicleRepository = JooqVehicleRepository(scoped(tenantId))

    private fun registerTenant(
        tenantId: TenantId,
        legalName: String,
        siren: String,
    ) {
        val tenant = Tenant.register(tenantId, LegalName.of(legalName), Siren.of(siren), HIRED_AT).state
        tenants(tenantId).save(tenant)
    }

    /** Enregistre l'exploitant si besoin — la clé étrangère de `driver` l'exige — puis le chauffeur. */
    private fun registerDriver(
        tenantId: TenantId,
        name: String,
    ): DriverId {
        if (tenants(tenantId).findCurrent() == null) {
            registerTenant(tenantId, name, if (tenantId == alice) "732829320" else "404833048")
        }
        val driver = Driver.register(DriverId.next(), tenantId, DriverName.of(name))
        drivers(tenantId).save(driver)
        return driver.id
    }

    private fun openMembership(
        tenantId: TenantId,
        driverId: DriverId,
        role: MembershipRole,
        at: Instant,
        accountId: AccountId? = null,
    ): MembershipId {
        val membership =
            Membership.invite(MembershipId.next(), tenantId, driverId, role, at, accountId).state
        memberships(tenantId).save(membership)
        return membership.id
    }

    private fun registerVehicle(
        tenantId: TenantId,
        plate: String,
    ): VehicleId {
        val vehicle =
            Vehicle(
                id = VehicleId.next(),
                tenantId = tenantId,
                plate = Plate.of(plate),
                energy = EnergySource.ELECTRIC,
                ownership = OwnershipMode.LEASE_LLD,
                firstRegisteredOn = LocalDate.of(2024, 3, 12),
            )
        vehicles(tenantId).save(vehicle)
        return vehicle.id
    }

    /**
     * Les tables effectivement soumises à la règle.
     *
     * Les deux contrôles ci-dessus cherchent des contre-exemples : sur une base où la migration
     * n'aurait pas tourné, ils n'en trouveraient aucun et seraient verts pour la pire des
     * raisons. Vérifier que le balayage voit au moins les quatre tables de KDN-27 est ce qui
     * distingue « rien à redire » de « rien à regarder ».
     */
    private fun inspectedTables(): List<String> =
        dsl
            .fetch(
                """
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'kadran' AND table_type = 'BASE TABLE'
                  AND table_name NOT IN ($QUOTED_EXEMPTIONS)
                ORDER BY table_name
                """.trimIndent(),
            ).map { it.get(0, String::class.java) }

    private companion object {
        val HIRED_AT: Instant = Instant.parse("2026-01-05T08:00:00Z")
        val IDENTITY_TABLES: List<String> = listOf("driver", "membership", "tenant", "vehicle")

        /**
         * **Refus par défaut** — le parti pris que KDN-16 a retenu pour son balayage des
         * littéraux SQL. Toute table de `kadran` est réputée devoir être scopée ; seules les
         * lignes ci-dessous y échappent.
         *
         * Filtrer sur une liste de tables *métier* ferait l'inverse, et serait un faux
         * garde-fou : le contrôle ne verrait que les tables qu'on a pensé à y inscrire, il
         * passerait au vert sur celles qu'un autre contexte ajoutera demain, et sa réussite ne
         * voudrait rien dire. Ici, oublier d'inscrire une table n'a aucune conséquence — le
         * défaut est le comportement sûr — tandis qu'ajouter une exemption est un acte
         * délibéré, qui se relit en PR.
         *
         * `schema_baseline` est la table de contrôle de KDN-14, déplacée dans `kadran` par
         * KDN-136 mais toujours hors périmètre du contrôle. `databasechangelog` et
         * `databasechangeloglock` appartiennent à Liquibase et restent dans `public` (KDN-136) :
         * elles n'apparaîtraient de toute façon jamais dans un balayage de `kadran`, mais la
         * liste les garde par cohérence avec le balayage de `SqlLiteralScanner`, qui porte sur
         * les littéraux SQL du code plutôt que sur un schéma. **N'ajouter une entrée ici qu'avec
         * sa justification** : chaque ligne est un trou dans le contrôle 1 de la spec §9.1.
         */
        val UNSCOPED_TABLES: Set<String> =
            setOf("schema_baseline", "databasechangelog", "databasechangeloglock")

        /** Liste littérale, plutôt qu'un paramètre lié : PostgreSQL n'accepte pas un tableau en `IN`. */
        val QUOTED_EXEMPTIONS: String = UNSCOPED_TABLES.joinToString(", ") { "'" + it + "'" }
        const val CHANGELOG = "db/changelog/db.changelog-master.xml"

        lateinit var connection: Connection

        fun openConnection(): Connection =
            DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)

        @Container
        @JvmStatic
        val postgres =
            // Crée kadran/audit et épingle le search_path du rôle avant la première
            // connexion (KDN-136) — sans volume persistant ici, rejoué à chaque conteneur.
            PostgreSQLContainer("postgres:18-alpine")
                .withInitScript("db/bootstrap/create-schemas.sql")

        /**
         * Racine des ressources de `app`, où vit le changelog (spec §11.1). On remonte jusqu'au
         * `settings.gradle.kts` plutôt que d'écrire un chemin relatif au module : le test
         * survit ainsi à un déplacement de `context-identity`.
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
