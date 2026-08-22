package io.korallis.kadran.activity.infrastructure.spi.persistence

import io.korallis.kadran.platform.persistence.TenantScopedTable
import org.jooq.Field
import org.jooq.Record
import org.jooq.impl.DSL
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Déclaration de la table `outing` (KDN-34) et de ses colonnes.
 *
 * Même patron que `IdentityTables` (KDN-27) : la table se déclare à la main via
 * [TenantScopedTable.named], la génération de code jOOQ n'étant pas encore en place.
 *
 * Fichier séparé de toute déclaration que `RevenueRecord` (KDN-35) pourrait ajouter au même
 * répertoire : les deux lanes touchent `context-activity` en parallèle, sur des tables
 * différentes, et ne doivent pas se disputer un même fichier.
 */
internal object OutingTables {
    val OUTING: TenantScopedTable<Record> = TenantScopedTable.named("outing")

    private const val SCHEMA = TenantScopedTable.OPERATIONAL_SCHEMA
    private const val TABLE = "outing"

    private fun uuid(column: String): Field<UUID> = DSL.field(DSL.name(SCHEMA, TABLE, column), UUID::class.java)

    private fun text(column: String): Field<String> = DSL.field(DSL.name(SCHEMA, TABLE, column), String::class.java)

    private fun timestamp(column: String): Field<OffsetDateTime> =
        DSL.field(DSL.name(SCHEMA, TABLE, column), OffsetDateTime::class.java)

    /** Colonnes de `outing`. Sa clé primaire est `(tenant_id, id)`. */
    object OutingColumns {
        val ID: Field<UUID> = uuid("id")

        /** Journée d'exploitation (spec §4.3), jamais la date calendaire de début. */
        val OUTING_DATE: Field<LocalDate> = DSL.field(DSL.name(SCHEMA, TABLE, "outing_date"), LocalDate::class.java)
        val WINDOW_FROM: Field<OffsetDateTime> = timestamp("window_from")
        val WINDOW_TO: Field<OffsetDateTime> = timestamp("window_to")
        val SPANS_MIDNIGHT: Field<Boolean> = DSL.field(DSL.name(SCHEMA, TABLE, "spans_midnight"), Boolean::class.java)
        val DISTANCE_METERS: Field<Long> = DSL.field(DSL.name(SCHEMA, TABLE, "distance_meters"), Long::class.java)
        val PURPOSE: Field<String> = text("purpose")
        val MILEAGE_ALLOWANCE_CENTS: Field<Long> =
            DSL.field(DSL.name(SCHEMA, TABLE, "mileage_allowance_cents"), Long::class.java)
        val SOURCE: Field<String> = text("source")

        /**
         * Simple référence, sans clé étrangère : la table `revenue_record` (KDN-35) n'existe
         * pas encore au moment de ce changeset.
         */
        val LINKED_REVENUE_RECORD_ID: Field<UUID> = uuid("linked_revenue_record_id")
    }
}
