package io.korallis.kadran.activity.domain.model

import io.korallis.kadran.core.BusinessDayPolicy
import io.korallis.kadran.core.Distance
import io.korallis.kadran.core.Money
import io.korallis.kadran.core.RevenueRecordId
import io.korallis.kadran.core.TenantId
import io.korallis.kadran.core.WorkPeriod
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * Domaine pur, sans mock (`CLAUDE.md` §2.4) : les critères d'acceptation de l'issue KDN-34,
 * un par un.
 */
class OutingTest :
    StringSpec({
        val tenantId = TenantId.of("11111111-1111-1111-1111-111111111111")
        val outingId = OutingId.next()
        val recordedAt = Instant.parse("2026-06-04T09:00:00Z")

        "a night shift crossing midnight is attached to the business day it started on" {
            // spec §4.3, echantillon Driversnote : 02/06 22:00 -> 03/06 03:00 heure de Paris.
            val window =
                WorkPeriod(
                    from = Instant.parse("2026-06-02T20:00:00Z"), // 22:00 heure de Paris (CEST)
                    to = Instant.parse("2026-06-03T01:00:00Z"), // 03:00 heure de Paris (CEST)
                )

            val (outing, event) =
                Outing.record(
                    id = outingId,
                    tenantId = tenantId,
                    timing = OutingTiming.fromWindow(window),
                    distance = Distance(130_700),
                    purpose = TripPurpose.PROFESSIONNEL,
                    source = MileageSource.DRIVERSNOTE,
                    recordedAt = recordedAt,
                )

            outing.businessDay shouldBe LocalDate.of(2026, 6, 2)
            outing.spansMidnight shouldBe true
            outing.window shouldBe window
            event shouldBe OutingRecorded(tenantId, outingId, LocalDate.of(2026, 6, 2), recordedAt)
        }

        "a shift that starts and ends on the same local day does not span midnight" {
            val window =
                WorkPeriod(
                    from = Instant.parse("2026-06-02T07:00:00Z"), // 09:00 heure de Paris
                    to = Instant.parse("2026-06-02T15:00:00Z"), // 17:00 heure de Paris
                )

            val timing = OutingTiming.fromWindow(window)

            timing.spansMidnight shouldBe false
            timing.businessDay shouldBe LocalDate.of(2026, 6, 2)
        }

        "the business day follows a custom cutoff, never the raw calendar date of the instant" {
            // Seuil a minuit : la journee d'exploitation redevient la date calendaire pure.
            val midnight = BusinessDayPolicy(cutoff = LocalTime.MIDNIGHT)
            val window =
                WorkPeriod(
                    from = Instant.parse("2026-06-02T20:30:00Z"), // 22:30 heure de Paris
                    to = Instant.parse("2026-06-02T21:00:00Z"), // 23:00 heure de Paris
                )

            OutingTiming.fromWindow(window, policy = midnight).businessDay shouldBe LocalDate.of(2026, 6, 2)
        }

        "a source giving only a date leaves the window null instead of inventing an hour" {
            val (outing, event) =
                Outing.record(
                    id = outingId,
                    tenantId = tenantId,
                    timing = OutingTiming.withoutWindow(LocalDate.of(2026, 6, 2)),
                    distance = Distance(87_300),
                    purpose = TripPurpose.PROFESSIONNEL,
                    source = MileageSource.DRIVERSNOTE,
                    recordedAt = recordedAt,
                )

            outing.window.shouldBeNull()
            outing.spansMidnight shouldBe false
            outing.businessDay shouldBe LocalDate.of(2026, 6, 2)
            event shouldBe OutingRecorded(tenantId, outingId, LocalDate.of(2026, 6, 2), recordedAt)
        }

        "a personal outing is kept but excluded from profitability" {
            val personal =
                Outing
                    .record(
                        id = outingId,
                        tenantId = tenantId,
                        timing = OutingTiming.withoutWindow(LocalDate.of(2026, 6, 2)),
                        distance = Distance(12_000),
                        purpose = TripPurpose.PERSONNEL,
                        source = MileageSource.DRIVERSNOTE,
                        recordedAt = recordedAt,
                    ).state
            val professional = personal.copy(purpose = TripPurpose.PROFESSIONNEL)

            personal.purpose shouldBe TripPurpose.PERSONNEL
            personal.countsTowardProfitability shouldBe false
            professional.countsTowardProfitability shouldBe true
            // La sortie personnelle reste malgre tout comptee au total odometrique (spec §4.4) :
            // rien dans le modele n'exclut sa distance, seule la marge l'ignore.
            personal.distance shouldBe Distance(12_000)
        }

        "an outing carries the optional fields the aggregate declares" {
            val revenueRecordId = RevenueRecordId.next()
            val outing =
                Outing
                    .record(
                        id = outingId,
                        tenantId = tenantId,
                        timing = OutingTiming.withoutWindow(LocalDate.of(2026, 6, 2)),
                        distance = Distance(87_300),
                        purpose = TripPurpose.PROFESSIONNEL,
                        source = MileageSource.DRIVERSNOTE,
                        recordedAt = recordedAt,
                    ).with(
                        OutingDetails(
                            startLabel = "Home",
                            endLabel = "Aeroport CDG",
                            mileageAllowance = Money.euroCents(4_896),
                            linkedRevenue = revenueRecordId,
                        ),
                    ).state

            outing.startLabel shouldBe "Home"
            outing.endLabel shouldBe "Aeroport CDG"
            outing.mileageAllowance shouldBe Money.euroCents(4_896)
            outing.linkedRevenue shouldBe revenueRecordId
            outing.source shouldBe MileageSource.DRIVERSNOTE
        }

        "attaching details after recording leaves the event untouched" {
            val transition =
                Outing.record(
                    id = outingId,
                    tenantId = tenantId,
                    timing = OutingTiming.withoutWindow(LocalDate.of(2026, 6, 2)),
                    distance = Distance(87_300),
                    purpose = TripPurpose.PROFESSIONNEL,
                    source = MileageSource.DRIVERSNOTE,
                    recordedAt = recordedAt,
                )

            val enriched = transition.with(OutingDetails(startLabel = "Home"))

            enriched.event shouldBe transition.event
            enriched.state.startLabel shouldBe "Home"
        }

        "an outing built manually has no linked revenue and no allowance by default" {
            val outing =
                Outing
                    .record(
                        id = outingId,
                        tenantId = tenantId,
                        timing = OutingTiming.withoutWindow(LocalDate.of(2026, 6, 2)),
                        distance = Distance.zero(),
                        purpose = TripPurpose.PROFESSIONNEL,
                        source = MileageSource.MANUAL,
                        recordedAt = recordedAt,
                    ).state

            outing.linkedRevenue.shouldBeNull()
            outing.mileageAllowance.shouldBeNull()
            outing.startLabel.shouldBeNull()
            outing.endLabel.shouldBeNull()
        }

        "identifiers minted for two outings differ, and render legibly for the audit journal" {
            (OutingId.next() == OutingId.next()) shouldBe false
            OutingId(UUID.fromString("22222222-2222-2222-2222-222222222222")).toString() shouldBe
                "22222222-2222-2222-2222-222222222222"
        }

        // Ces deux vocabulaires sont persistes par `.name` (voir le changeset KDN-34) : leur
        // ordre et leur libelle sont une donnee, pas un detail d'implementation.
        "the persisted vocabularies are pinned" {
            TripPurpose.entries.map { it.name } shouldBe listOf("PROFESSIONNEL", "PERSONNEL")
            MileageSource.entries.map { it.name } shouldBe listOf("DRIVERSNOTE", "CSV_GENERIC", "MANUAL")
        }
    })
