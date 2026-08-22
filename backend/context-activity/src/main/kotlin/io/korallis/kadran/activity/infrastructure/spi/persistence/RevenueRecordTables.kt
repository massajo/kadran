package io.korallis.kadran.activity.infrastructure.spi.persistence

import io.korallis.kadran.platform.persistence.TenantScopedTable
import org.jooq.Field
import org.jooq.JSONB
import org.jooq.Record
import org.jooq.impl.DSL
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Déclaration de la table `revenue_record` (KDN-35) et de ses colonnes.
 *
 * Propre à `RevenueRecord` : `Outing`, construit en parallèle dans ce même module (KDN-34),
 * déclare sa propre table dans son propre fichier plutôt que de se disputer celui-ci — même
 * motif que `RevenueRecordJson` (voir sa documentation).
 *
 * Comme `IdentityTables` (KDN-27), la table se déclare à la main via
 * [TenantScopedTable.named] : la génération de code jOOQ n'est pas encore en place.
 */
internal object RevenueRecordTables {
    val REVENUE_RECORD: TenantScopedTable<Record> = TenantScopedTable.named("revenue_record")

    private const val SCHEMA = TenantScopedTable.OPERATIONAL_SCHEMA
    private const val TABLE = "revenue_record"

    private fun uuid(column: String): Field<UUID> = DSL.field(DSL.name(SCHEMA, TABLE, column), UUID::class.java)

    private fun text(column: String): Field<String> = DSL.field(DSL.name(SCHEMA, TABLE, column), String::class.java)

    private fun timestamp(column: String): Field<OffsetDateTime> =
        DSL.field(DSL.name(SCHEMA, TABLE, column), OffsetDateTime::class.java)

    private fun bigint(column: String): Field<Long> = DSL.field(DSL.name(SCHEMA, TABLE, column), Long::class.java)

    private fun integer(column: String): Field<Int> = DSL.field(DSL.name(SCHEMA, TABLE, column), Int::class.java)

    private fun numeric(column: String): Field<BigDecimal> =
        DSL.field(DSL.name(SCHEMA, TABLE, column), BigDecimal::class.java)

    private fun jsonb(column: String): Field<JSONB> = DSL.field(DSL.name(SCHEMA, TABLE, column), JSONB::class.java)

    /** Colonnes de `revenue_record` (spec §7.3, §7.6). */
    object Columns {
        val ID: Field<UUID> = uuid("id")
        val PLATFORM: Field<String> = text("platform")
        val GRAIN: Field<String> = text("grain")
        val COVERAGE_FROM: Field<OffsetDateTime> = timestamp("coverage_from")
        val COVERAGE_TO: Field<OffsetDateTime> = timestamp("coverage_to")

        /** Zone « canonique » structurée — communes à toute plateforme, jamais lues sans typage. */
        val EXTERNAL_REFS: Field<JSONB> = jsonb("external_refs")
        val PROVENANCE: Field<JSONB> = jsonb("provenance")

        val CURRENCY: Field<String> = text("currency")
        val AMOUNT_GROSS_CENTS: Field<Long> = bigint("amount_gross_cents")
        val AMOUNT_NET_CENTS: Field<Long> = bigint("amount_net_cents")
        val AMOUNT_COMMISSION_CENTS: Field<Long> = bigint("amount_commission_cents")
        val AMOUNT_TIPS_CENTS: Field<Long> = bigint("amount_tips_cents")
        val AMOUNT_INCENTIVES_CENTS: Field<Long> = bigint("amount_incentives_cents")
        val AMOUNT_SURCHARGES_CENTS: Field<Long> = bigint("amount_surcharges_cents")

        val VAT_BASE_CENTS: Field<Long> = bigint("vat_base_cents")
        val VAT_AMOUNT_CENTS: Field<Long> = bigint("vat_amount_cents")
        val VAT_TOTAL_CENTS: Field<Long> = bigint("vat_total_cents")
        val VAT_RATE: Field<BigDecimal> = numeric("vat_rate")

        val COUNTS_TRIPS: Field<Int> = integer("counts_trips")
        val COUNTS_ONLINE_TIME_SECONDS: Field<Long> = bigint("counts_online_time_seconds")
        val COUNTS_DISTANCE_M: Field<Long> = bigint("counts_distance_m")

        /** Zone « extras » (spec §7.6) — spécifique plateforme, clés préfixées, indexée GIN. */
        val PLATFORM_EXTRAS: Field<JSONB> = jsonb("platform_extras")

        /** Zone « brut » (spec §7.6) — jamais projetée par [Reads.CANONICAL], jamais lue par un calcul. */
        val RAW_PAYLOAD: Field<JSONB> = jsonb("raw_payload")
    }

    /**
     * Ce que les lectures du domaine projettent — délibérément **sans** [Columns.RAW_PAYLOAD].
     *
     * `JooqRevenueRecordRepository` construit ses `SELECT` avec `TenantScopedQuery.select`
     * plutôt que `selectFrom` (qui ferait `SELECT *`) précisément pour que cette liste, et
     * elle seule, gouverne ce qui revient du domaine. `RevenueRecordRawPayloadNeverReadTest`
     * vérifie sur le SQL généré que `raw_payload` n'y figure jamais.
     */
    object Reads {
        val CANONICAL: List<Field<*>> =
            listOf(
                REVENUE_RECORD.tenantId,
                Columns.ID,
                Columns.PLATFORM,
                Columns.GRAIN,
                Columns.COVERAGE_FROM,
                Columns.COVERAGE_TO,
                Columns.EXTERNAL_REFS,
                Columns.PROVENANCE,
                Columns.CURRENCY,
                Columns.AMOUNT_GROSS_CENTS,
                Columns.AMOUNT_NET_CENTS,
                Columns.AMOUNT_COMMISSION_CENTS,
                Columns.AMOUNT_TIPS_CENTS,
                Columns.AMOUNT_INCENTIVES_CENTS,
                Columns.AMOUNT_SURCHARGES_CENTS,
                Columns.VAT_BASE_CENTS,
                Columns.VAT_AMOUNT_CENTS,
                Columns.VAT_TOTAL_CENTS,
                Columns.VAT_RATE,
                Columns.COUNTS_TRIPS,
                Columns.COUNTS_ONLINE_TIME_SECONDS,
                Columns.COUNTS_DISTANCE_M,
                Columns.PLATFORM_EXTRAS,
            )
    }
}
