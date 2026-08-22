package io.korallis.kadran.identity.infrastructure.spi.persistence

import io.korallis.kadran.platform.persistence.TenantScopedTable
import org.jooq.Field
import org.jooq.Record
import org.jooq.impl.DSL
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Déclaration des quatre tables du contexte `identity` et de leurs colonnes (KDN-27).
 *
 * Les tables se déclarent à la main via [TenantScopedTable.named] : la génération de code
 * jOOQ n'est pas encore en place. Le jour où elle le sera, seul ce fichier disparaît — les
 * repositories parlent déjà en `Field<T>` typés.
 *
 * **Les quatre sont scopées, `tenant` comprise.** Sa colonne d'isolation est sa clé primaire :
 * une lecture scopée y rend au plus une ligne, celle de l'appelant. Aucune table du contexte
 * n'échappe donc à `TenantScopedQuery`, et aucune n'a besoin d'une dérogation.
 *
 * Chaque colonne est **qualifiée par sa table**. Ce n'est pas de la coquetterie : `id`,
 * `tenant_id` et `created_at` existent sur plusieurs de ces tables, et une jointure sur des
 * colonnes non qualifiées produirait une ambiguïté — ou pire, un prédicat qui protège la
 * mauvaise table.
 */
internal object IdentityTables {
    val TENANT: TenantScopedTable<Record> = TenantScopedTable.named("tenant")
    val DRIVER: TenantScopedTable<Record> = TenantScopedTable.named("driver")
    val MEMBERSHIP: TenantScopedTable<Record> = TenantScopedTable.named("membership")
    val VEHICLE: TenantScopedTable<Record> = TenantScopedTable.named("vehicle")

    private fun uuid(
        table: String,
        column: String,
    ): Field<UUID> = DSL.field(DSL.name(table, column), UUID::class.java)

    private fun text(
        table: String,
        column: String,
    ): Field<String> = DSL.field(DSL.name(table, column), String::class.java)

    private fun timestamp(
        table: String,
        column: String,
    ): Field<OffsetDateTime> = DSL.field(DSL.name(table, column), OffsetDateTime::class.java)

    /** Colonnes de `tenant`. Sa clé primaire est [TenantScopedTable.tenantId]. */
    object TenantColumns {
        val LEGAL_NAME: Field<String> = text("tenant", "legal_name")
        val SIREN: Field<String> = text("tenant", "siren")
        val ONBOARDING_STATUS: Field<String> = text("tenant", "onboarding_status")
        val CLOSED_AT: Field<OffsetDateTime> = timestamp("tenant", "closed_at")
    }

    /** Colonnes de `driver`. */
    object DriverColumns {
        val ID: Field<UUID> = uuid("driver", "id")
        val DISPLAY_NAME: Field<String> = text("driver", "display_name")
    }

    /** Colonnes de `membership`. */
    object MembershipColumns {
        val ID: Field<UUID> = uuid("membership", "id")
        val DRIVER_ID: Field<UUID> = uuid("membership", "driver_id")
        val ACCOUNT_ID: Field<UUID> = uuid("membership", "account_id")
        val ROLE: Field<String> = text("membership", "role")
        val VALID_FROM: Field<OffsetDateTime> = timestamp("membership", "valid_from")
        val VALID_UNTIL: Field<OffsetDateTime> = timestamp("membership", "valid_until")
    }

    /** Colonnes de `vehicle`. */
    object VehicleColumns {
        val ID: Field<UUID> = uuid("vehicle", "id")
        val PLATE: Field<String> = text("vehicle", "plate")
        val ENERGY: Field<String> = text("vehicle", "energy")
        val OWNERSHIP_MODE: Field<String> = text("vehicle", "ownership_mode")
        val FIRST_REGISTERED_ON: Field<LocalDate> =
            DSL.field(DSL.name("vehicle", "first_registered_on"), LocalDate::class.java)
    }
}
