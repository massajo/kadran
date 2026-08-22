package io.korallis.kadran.activity.domain.model

import io.korallis.kadran.core.Distance
import io.korallis.kadran.core.Grain
import io.korallis.kadran.core.Money
import io.korallis.kadran.core.PlatformId
import io.korallis.kadran.core.Ratio
import io.korallis.kadran.core.TenantId
import io.korallis.kadran.core.WorkPeriod
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency
import java.util.UUID

/**
 * L'agrégat `RevenueRecord` — sans le moindre mock : le domaine est pur (`CLAUDE.md` §2.4).
 */
class RevenueRecordTest :
    StringSpec({
        val now = Instant.parse("2026-08-17T08:00:00Z")
        val tenantId = TenantId.of("11111111-1111-1111-1111-111111111111")
        val eur = Currency.getInstance("EUR")
        val usd = Currency.getInstance("USD")

        fun money(
            cents: Long,
            currency: Currency = eur,
        ) = Money(cents, currency)

        fun breakdown(currency: Currency = eur) =
            RevenueBreakdown(
                gross = money(2_000, currency),
                net = money(1_700, currency),
                commission = money(300, currency),
                tips = money(150, currency),
                incentives = money(50, currency),
                surcharges = money(0, currency),
            )

        fun tripCoverage() = WorkPeriod(now, now)

        fun uberInvoiceRecord(
            id: RevenueRecordId = RevenueRecordId.next(),
            coverage: WorkPeriod = tripCoverage(),
            grain: Grain = Grain.TRIP,
        ) = RevenueRecord(
            id = id,
            tenantId = tenantId,
            platform = PlatformId.UBER,
            grain = grain,
            coverage = coverage,
            externalRefs = setOf(ExternalRef("NuméroFacture", "A-99-9999-9999999")),
            amounts = breakdown(),
            vat = null,
            counts = null,
            platformExtras = RevenueRecordJson.empty(),
            provenance = setOf(DataProvenance(SourceDocument.UBER_INVOICE, now)),
        )

        // ------------------------------------------------------------------------- RevenueBreakdown

        "a breakdown mixing currencies is refused" {
            shouldThrow<IllegalArgumentException> {
                RevenueBreakdown(
                    gross = money(2_000, eur),
                    net = money(1_700, usd),
                    commission = money(300, eur),
                    tips = money(0, eur),
                    incentives = money(0, eur),
                    surcharges = money(0, eur),
                )
            }
        }

        "a breakdown built in one currency reports that currency" {
            breakdown(eur).currency shouldBe eur
        }

        // ---------------------------------------------------------------------------- VatBreakdown

        "a VAT breakdown mixing currencies is refused" {
            shouldThrow<IllegalArgumentException> {
                VatBreakdown(
                    baseExcludingVat = money(1_000, eur),
                    vatAmount = money(100, usd),
                    totalIncludingVat = money(1_100, eur),
                    rate = Ratio(BigDecimal("0.10")),
                )
            }
        }

        "a VAT breakdown stores the three amounts as given, never recomputing one from the others" {
            // Ecart connu et volontaire (spec §3.3) : 1000 + 100 = 1100 ici, mais rien dans le
            // domaine ne l'exige — la plateforme peut fournir un ecart d'arrondi (CLAUDE.md §8).
            val vat =
                VatBreakdown(
                    baseExcludingVat = money(99_999),
                    vatAmount = money(10_001),
                    totalIncludingVat = money(110_001),
                    rate = Ratio(BigDecimal("0.10")),
                )

            vat.baseExcludingVat shouldBe money(99_999)
            vat.vatAmount shouldBe money(10_001)
            vat.totalIncludingVat shouldBe money(110_001)
        }

        // -------------------------------------------------------------------------- ActivityCounts

        "an ActivityCounts entirely empty must be represented as null, not constructed" {
            shouldThrow<IllegalArgumentException> { ActivityCounts(trips = null, onlineTime = null, distance = null) }
        }

        "an ActivityCounts with a single known counter is valid" {
            val counts = ActivityCounts(trips = 94, onlineTime = null, distance = null)

            counts.trips shouldBe 94
        }

        "a negative trip count is refused" {
            shouldThrow<IllegalArgumentException> {
                ActivityCounts(trips = -1, onlineTime = null, distance = Distance.zero())
            }
        }

        // ---------------------------------------------------------------------------- ExternalRef

        "an external reference with a blank label is refused" {
            shouldThrow<IllegalArgumentException> { ExternalRef(label = " ", value = "A-99-9999-9999999") }
        }

        "an external reference with a blank value is refused" {
            shouldThrow<IllegalArgumentException> { ExternalRef(label = "NuméroFacture", value = "") }
        }

        // --------------------------------------------------------------------------- RevenueRecord

        "a TRIP-grain record covers a single instant, from equal to" {
            val record = uberInvoiceRecord()

            record.coverage.from shouldBe record.coverage.to
        }

        "a TRIP-grain record built on a non-zero window is refused" {
            shouldThrow<IllegalArgumentException> {
                uberInvoiceRecord(coverage = WorkPeriod(now, now.plusSeconds(1)), grain = Grain.TRIP)
            }
        }

        "a PERIOD-grain record may cover an actual window, Bolt's export period" {
            val week = WorkPeriod(now, now.plusSeconds(7 * 24 * 3_600L))

            val record = uberInvoiceRecord(coverage = week, grain = Grain.PERIOD)

            record.grain shouldBe Grain.PERIOD
            record.coverage shouldBe week
        }

        "recording a revenue renders the state together with the event that justifies it" {
            val built = uberInvoiceRecord()

            val (state, event) = RevenueRecord.record(built, now)

            state shouldBe built
            event shouldBe RevenueRecorded(tenantId, built.id, PlatformId.UBER, Grain.TRIP, now)
        }

        // ------------------------------------------------------------------------- RevenueRecordId

        "an identifier reads back through its string form, unique per record" {
            val id = RevenueRecordId(UUID.fromString("22222222-2222-2222-2222-222222222222"))

            id.toString() shouldBe "22222222-2222-2222-2222-222222222222"
            (id == RevenueRecordId.next()) shouldBe false
        }
    })
