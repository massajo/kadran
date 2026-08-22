package io.korallis.kadran.activity.infrastructure.spi.persistence

import io.korallis.kadran.activity.infrastructure.spi.persistence.RevenueRecordTables.Columns
import io.korallis.kadran.activity.infrastructure.spi.persistence.RevenueRecordTables.REVENUE_RECORD
import io.korallis.kadran.platform.persistence.TenantScopedQuery
import io.korallis.kadran.platform.tenancy.TenantId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import java.util.UUID

/**
 * Preuve, au niveau SQL, que le critère d'acceptation de l'issue — « aucun calcul ne lit
 * `raw_payload` » (spec §7.6) — tient pour `JooqRevenueRecordRepository`.
 *
 * `findById`/`findAll` projettent [RevenueRecordTables.Reads.CANONICAL] via
 * `TenantScopedQuery.select`, jamais `selectFrom` (qui ferait `SELECT *`). Ce test rend le
 * SQL que produirait exactement ce chemin, **sans connexion à une base** — `DSL.using` sans
 * source de données sait rendre une requête sans l'exécuter — et vérifie que `raw_payload`
 * n'y figure nulle part : ni dans le SQL généré, ni dans la liste de champs elle-même.
 */
class RevenueRecordRawPayloadNeverReadTest :
    StringSpec({
        val dsl = DSL.using(SQLDialect.POSTGRES)
        val query = TenantScopedQuery.forTenant(TenantId(UUID.randomUUID()), dsl)

        "the SQL used to read a single revenue record never mentions raw_payload" {
            val sql =
                query
                    .select(REVENUE_RECORD, RevenueRecordTables.Reads.CANONICAL)
                    .and(Columns.ID.eq(UUID.randomUUID()))
                    .sql

            sql.contains("raw_payload", ignoreCase = true) shouldBe false
        }

        "the SQL used to list every revenue record never mentions raw_payload" {
            val sql = query.select(REVENUE_RECORD, RevenueRecordTables.Reads.CANONICAL).sql

            sql.contains("raw_payload", ignoreCase = true) shouldBe false
        }

        "the canonical read projection itself never lists the raw_payload column" {
            val projectedNames = RevenueRecordTables.Reads.CANONICAL.map { it.name.lowercase() }

            projectedNames.contains(Columns.RAW_PAYLOAD.name.lowercase()) shouldBe false
        }
    })
