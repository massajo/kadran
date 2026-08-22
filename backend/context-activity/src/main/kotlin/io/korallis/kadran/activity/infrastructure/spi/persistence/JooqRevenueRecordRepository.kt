package io.korallis.kadran.activity.infrastructure.spi.persistence

import io.korallis.kadran.activity.domain.model.ActivityCounts
import io.korallis.kadran.activity.domain.model.DataProvenance
import io.korallis.kadran.activity.domain.model.ExternalRef
import io.korallis.kadran.activity.domain.model.RevenueBreakdown
import io.korallis.kadran.activity.domain.model.RevenueRecord
import io.korallis.kadran.activity.domain.model.RevenueRecordId
import io.korallis.kadran.activity.domain.model.RevenueRecordJson
import io.korallis.kadran.activity.domain.model.SourceDocument
import io.korallis.kadran.activity.domain.model.VatBreakdown
import io.korallis.kadran.activity.domain.spi.RevenueRecordRepository
import io.korallis.kadran.activity.infrastructure.spi.persistence.RevenueRecordTables.Columns
import io.korallis.kadran.activity.infrastructure.spi.persistence.RevenueRecordTables.REVENUE_RECORD
import io.korallis.kadran.core.Distance
import io.korallis.kadran.core.Grain
import io.korallis.kadran.core.Money
import io.korallis.kadran.core.PlatformId
import io.korallis.kadran.core.Ratio
import io.korallis.kadran.core.TenantId
import io.korallis.kadran.core.WorkPeriod
import io.korallis.kadran.platform.persistence.TenantScopedQuery
import org.jooq.Field
import org.jooq.JSONB
import org.jooq.Record
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Currency

/**
 * Adaptateur `revenue_record` (KDN-35).
 *
 * `findById`/`findAll` projettent explicitement [RevenueRecordTables.Reads.CANONICAL] plutôt
 * que `SELECT *` (`TenantScopedQuery.select` contre `selectFrom`) : `raw_payload` n'y figure
 * jamais, ce qui rend impossible, par construction, qu'une lecture du domaine le fasse
 * remonter. `RevenueRecordRawPayloadNeverReadTest` le vérifie sur le SQL généré.
 */
class JooqRevenueRecordRepository(
    private val query: TenantScopedQuery,
) : RevenueRecordRepository {
    override fun findById(id: RevenueRecordId): RevenueRecord? =
        query
            .select(REVENUE_RECORD, RevenueRecordTables.Reads.CANONICAL)
            .and(Columns.ID.eq(id.value))
            .fetchOne()
            ?.toRevenueRecord()

    override fun findAll(): List<RevenueRecord> =
        query
            .select(REVENUE_RECORD, RevenueRecordTables.Reads.CANONICAL)
            .orderBy(Columns.COVERAGE_FROM.asc())
            .fetch()
            .map { it.toRevenueRecord() }

    override fun save(
        record: RevenueRecord,
        rawPayload: RevenueRecordJson,
    ) {
        query
            .insertInto(REVENUE_RECORD, insertValues(record, rawPayload))
            .onConflict(REVENUE_RECORD.tenantId, Columns.ID)
            .doUpdate()
            .set(mutableValues(record, rawPayload))
            .execute()
    }

    override fun deleteById(id: RevenueRecordId): Boolean =
        query
            .deleteFrom(REVENUE_RECORD)
            .and(Columns.ID.eq(id.value))
            .execute() > 0

    private fun insertValues(
        record: RevenueRecord,
        rawPayload: RevenueRecordJson,
    ): Map<Field<*>, Any?> =
        mutableValues(record, rawPayload) +
            mapOf<Field<*>, Any?>(
                REVENUE_RECORD.tenantId to record.tenantId.value,
                Columns.ID to record.id.value,
            )

    private fun mutableValues(
        record: RevenueRecord,
        rawPayload: RevenueRecordJson,
    ): Map<Field<*>, Any?> {
        val amounts = record.amounts
        val vat = record.vat
        val counts = record.counts
        return linkedMapOf(
            Columns.PLATFORM to record.platform.name,
            Columns.GRAIN to record.grain.name,
            Columns.COVERAGE_FROM to record.coverage.from.toColumnValue(),
            Columns.COVERAGE_TO to record.coverage.to.toColumnValue(),
            Columns.EXTERNAL_REFS to
                record.externalRefs
                    .externalRefsToJson()
                    .toJsonText()
                    .let(JSONB::valueOf),
            Columns.PROVENANCE to
                record.provenance
                    .provenanceToJson()
                    .toJsonText()
                    .let(JSONB::valueOf),
            Columns.CURRENCY to amounts.currency.currencyCode,
            Columns.AMOUNT_GROSS_CENTS to amounts.gross.amountCents,
            Columns.AMOUNT_NET_CENTS to amounts.net.amountCents,
            Columns.AMOUNT_COMMISSION_CENTS to amounts.commission.amountCents,
            Columns.AMOUNT_TIPS_CENTS to amounts.tips.amountCents,
            Columns.AMOUNT_INCENTIVES_CENTS to amounts.incentives.amountCents,
            Columns.AMOUNT_SURCHARGES_CENTS to amounts.surcharges.amountCents,
            Columns.VAT_BASE_CENTS to vat?.baseExcludingVat?.amountCents,
            Columns.VAT_AMOUNT_CENTS to vat?.vatAmount?.amountCents,
            Columns.VAT_TOTAL_CENTS to vat?.totalIncludingVat?.amountCents,
            Columns.VAT_RATE to vat?.rate?.value,
            Columns.COUNTS_TRIPS to counts?.trips,
            Columns.COUNTS_ONLINE_TIME_SECONDS to counts?.onlineTime?.seconds,
            Columns.COUNTS_DISTANCE_M to counts?.distance?.meters,
            Columns.PLATFORM_EXTRAS to record.platformExtras.toJsonText().let(JSONB::valueOf),
            Columns.RAW_PAYLOAD to rawPayload.toJsonText().let(JSONB::valueOf),
        )
    }

    private fun Record.toRevenueRecord(): RevenueRecord {
        val currency = Currency.getInstance(read(Columns.CURRENCY))
        return RevenueRecord(
            id = RevenueRecordId(read(Columns.ID)),
            tenantId = TenantId(read(REVENUE_RECORD.tenantId)),
            platform = PlatformId.valueOf(read(Columns.PLATFORM)),
            grain = Grain.valueOf(read(Columns.GRAIN)),
            coverage = WorkPeriod(read(Columns.COVERAGE_FROM).toInstant(), read(Columns.COVERAGE_TO).toInstant()),
            externalRefs = parseRevenueRecordJson(read(Columns.EXTERNAL_REFS).data()).toExternalRefs(),
            amounts = buildAmounts(currency),
            vat = buildVat(currency),
            counts = buildCounts(),
            platformExtras = parseRevenueRecordJson(read(Columns.PLATFORM_EXTRAS).data()),
            provenance = parseRevenueRecordJson(read(Columns.PROVENANCE).data()).toProvenance(),
        )
    }

    private fun Record.buildAmounts(currency: Currency): RevenueBreakdown =
        RevenueBreakdown(
            gross = Money(read(Columns.AMOUNT_GROSS_CENTS), currency),
            net = Money(read(Columns.AMOUNT_NET_CENTS), currency),
            commission = Money(read(Columns.AMOUNT_COMMISSION_CENTS), currency),
            tips = Money(read(Columns.AMOUNT_TIPS_CENTS), currency),
            incentives = Money(read(Columns.AMOUNT_INCENTIVES_CENTS), currency),
            surcharges = Money(read(Columns.AMOUNT_SURCHARGES_CENTS), currency),
        )

    private fun Record.buildVat(currency: Currency): VatBreakdown? {
        val base = readOrNull(Columns.VAT_BASE_CENTS) ?: return null
        val amount =
            checkNotNull(readOrNull(Columns.VAT_AMOUNT_CENTS)) { "vat_amount_cents nul avec vat_base_cents pose" }
        val total =
            checkNotNull(readOrNull(Columns.VAT_TOTAL_CENTS)) { "vat_total_cents nul avec vat_base_cents pose" }
        val rate = checkNotNull(readOrNull(Columns.VAT_RATE)) { "vat_rate nul avec vat_base_cents pose" }
        return VatBreakdown(Money(base, currency), Money(amount, currency), Money(total, currency), Ratio(rate))
    }

    private fun Record.buildCounts(): ActivityCounts? {
        val trips = readOrNull(Columns.COUNTS_TRIPS)
        val onlineTimeSeconds = readOrNull(Columns.COUNTS_ONLINE_TIME_SECONDS)
        val distanceM = readOrNull(Columns.COUNTS_DISTANCE_M)
        if (trips == null && onlineTimeSeconds == null && distanceM == null) return null
        return ActivityCounts(trips, onlineTimeSeconds?.let(Duration::ofSeconds), distanceM?.let(::Distance))
    }
}

// -------------------------------------------------------------------- conversions JSON <-> domaine

private fun Set<ExternalRef>.externalRefsToJson(): RevenueRecordJson =
    RevenueRecordJson.Arr(
        map { ref ->
            RevenueRecordJson.Obj(
                mapOf(
                    "label" to RevenueRecordJson.Str(ref.label),
                    "value" to RevenueRecordJson.Str(ref.value),
                ),
            )
        },
    )

private fun RevenueRecordJson.toExternalRefs(): Set<ExternalRef> {
    val items = (this as? RevenueRecordJson.Arr)?.items ?: error("external_refs doit etre un tableau JSON")
    return items
        .map { item ->
            val obj = item as? RevenueRecordJson.Obj ?: error("une reference externe doit etre un objet JSON")
            ExternalRef(obj.stringField("label"), obj.stringField("value"))
        }.toSet()
}

private fun Set<DataProvenance>.provenanceToJson(): RevenueRecordJson =
    RevenueRecordJson.Arr(
        map { provenance ->
            RevenueRecordJson.Obj(
                mapOf(
                    "document" to RevenueRecordJson.Str(provenance.document.name),
                    "importedAt" to RevenueRecordJson.Str(provenance.importedAt.toString()),
                ),
            )
        },
    )

private fun RevenueRecordJson.toProvenance(): Set<DataProvenance> {
    val items = (this as? RevenueRecordJson.Arr)?.items ?: error("provenance doit etre un tableau JSON")
    return items
        .map { item ->
            val obj = item as? RevenueRecordJson.Obj ?: error("une provenance doit etre un objet JSON")
            DataProvenance(
                document = SourceDocument.valueOf(obj.stringField("document")),
                importedAt = Instant.parse(obj.stringField("importedAt")),
            )
        }.toSet()
}

private fun RevenueRecordJson.Obj.stringField(name: String): String =
    (fields[name] as? RevenueRecordJson.Str)?.value ?: error("champ JSON '$name' absent ou non textuel")

// ------------------------------------------------------------------------------- colonnes JDBC

/**
 * L'offset est **UTC et non `Europe/Paris`** : la colonne est un point sur la ligne du temps,
 * le fuseau d'exploitation (spec §4.3) s'applique à la lecture métier, jamais au stockage
 * (même choix que `RecordAccess.toColumnValue` en KDN-27).
 */
private fun Instant.toColumnValue(): OffsetDateTime = atOffset(ZoneOffset.UTC)

/** Lit une colonne en convertissant vers le type attendu, plutôt que de parier sur celui du pilote JDBC. */
private fun <T : Any> Record.readOrNull(field: Field<T>): T? = get(field, field.type)

/** @throws IllegalStateException si la colonne est nulle alors que le schéma l'interdit. */
private fun <T : Any> Record.read(field: Field<T>): T =
    checkNotNull(readOrNull(field)) { "colonne ${field.name} nulle alors que le schema l'interdit" }
