package io.korallis.kadran.activity.infrastructure.spi.persistence

import io.korallis.kadran.activity.domain.model.MileageSource
import io.korallis.kadran.activity.domain.model.Outing
import io.korallis.kadran.activity.domain.model.OutingId
import io.korallis.kadran.activity.domain.model.TripPurpose
import io.korallis.kadran.activity.domain.spi.OutingRepository
import io.korallis.kadran.activity.infrastructure.spi.persistence.OutingTables.OUTING
import io.korallis.kadran.activity.infrastructure.spi.persistence.OutingTables.OutingColumns
import io.korallis.kadran.core.Distance
import io.korallis.kadran.core.Money
import io.korallis.kadran.core.RevenueRecordId
import io.korallis.kadran.core.TenantId
import io.korallis.kadran.core.WorkPeriod
import io.korallis.kadran.platform.persistence.TenantScopedQuery
import org.jooq.Field
import org.jooq.Record
import java.time.LocalDate

/**
 * Adaptateur `outing` (KDN-34).
 *
 * `startLabel`/`endLabel` ne sont lus ni écrits ici — la table `outing` ne les persiste pas
 * (PII non réduite, voir le changeset et `Outing.kt`) — si bien qu'un aller-retour par ce
 * repository les rend toujours à `null`, quelle que soit la valeur portée par l'agrégat en
 * mémoire au moment de `save`.
 */
class JooqOutingRepository(
    private val query: TenantScopedQuery,
) : OutingRepository {
    override fun findById(id: OutingId): Outing? =
        query
            .selectFrom(OUTING)
            .and(OutingColumns.ID.eq(id.value))
            .fetchOne()
            ?.toOuting()

    override fun findByBusinessDay(businessDay: LocalDate): List<Outing> =
        query
            .selectFrom(OUTING)
            .and(OutingColumns.OUTING_DATE.eq(businessDay))
            .fetch()
            .map { it.toOuting() }

    override fun findAll(): List<Outing> =
        query
            .selectFrom(OUTING)
            .orderBy(OutingColumns.OUTING_DATE.desc())
            .fetch()
            .map { it.toOuting() }

    override fun save(outing: Outing) {
        query
            .insertInto(OUTING, insertValues(outing))
            .onConflict(OUTING.tenantId, OutingColumns.ID)
            .doUpdate()
            .set(mutableValues(outing))
            .execute()
    }

    override fun deleteById(id: OutingId): Boolean =
        query
            .deleteFrom(OUTING)
            .and(OutingColumns.ID.eq(id.value))
            .execute() > 0

    private fun insertValues(outing: Outing): Map<Field<*>, Any?> =
        mutableValues(outing) +
            mapOf<Field<*>, Any?>(
                OUTING.tenantId to outing.tenantId.value,
                OutingColumns.ID to outing.id.value,
            )

    private fun mutableValues(outing: Outing): Map<Field<*>, Any?> =
        mapOf(
            OutingColumns.OUTING_DATE to outing.businessDay,
            OutingColumns.WINDOW_FROM to outing.window?.from?.toColumnValue(),
            OutingColumns.WINDOW_TO to outing.window?.to?.toColumnValue(),
            OutingColumns.SPANS_MIDNIGHT to outing.spansMidnight,
            OutingColumns.DISTANCE_METERS to outing.distance.meters,
            OutingColumns.PURPOSE to outing.purpose.name,
            OutingColumns.MILEAGE_ALLOWANCE_CENTS to outing.mileageAllowance?.amountCents,
            OutingColumns.SOURCE to outing.source.name,
            OutingColumns.LINKED_REVENUE_RECORD_ID to outing.linkedRevenue?.value,
        )

    private fun Record.toOuting(): Outing {
        val from = readOrNull(OutingColumns.WINDOW_FROM)
        val to = readOrNull(OutingColumns.WINDOW_TO)
        return Outing(
            id = OutingId(read(OutingColumns.ID)),
            tenantId = TenantId(read(OUTING.tenantId)),
            businessDay = read(OutingColumns.OUTING_DATE),
            window = if (from != null && to != null) WorkPeriod(from.toInstant(), to.toInstant()) else null,
            spansMidnight = read(OutingColumns.SPANS_MIDNIGHT),
            distance = Distance(read(OutingColumns.DISTANCE_METERS)),
            purpose = TripPurpose.valueOf(read(OutingColumns.PURPOSE)),
            startLabel = null,
            endLabel = null,
            mileageAllowance = readOrNull(OutingColumns.MILEAGE_ALLOWANCE_CENTS)?.let { Money.euroCents(it) },
            source = MileageSource.valueOf(read(OutingColumns.SOURCE)),
            linkedRevenue = readOrNull(OutingColumns.LINKED_REVENUE_RECORD_ID)?.let { RevenueRecordId(it) },
        )
    }
}
