package io.korallis.kadran.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import java.time.Duration
import java.time.Instant

/**
 * `IntervalUnion` — spec §7.3 : "source de bug n°1", être connecté a deux plateformes en meme
 * temps ne cree pas deux heures dans une heure. Domaine pur, aucun mock, property-based tests
 * obligatoires (spec §10.4).
 */
class IntervalUnionTest :
    StringSpec({

        val base = Instant.parse("2026-06-02T20:00:00Z")

        fun period(
            fromMinutes: Long,
            toMinutes: Long,
        ) = WorkPeriod(base.plusSeconds(fromMinutes * 60), base.plusSeconds(toMinutes * 60))

        // Genere des periodes denses sur une fenetre de 10h pour garantir un taux de
        // chevauchement/contact eleve : un tirage purement independant sur un axe non borne
        // ne testerait quasiment jamais la fusion, qui est le coeur du sujet.
        val periodArb: Arb<WorkPeriod> =
            arbitrary {
                val from = Arb.long(0L..600L).bind()
                val length = Arb.long(0L..180L).bind()
                period(from, from + length)
            }
        val periodListArb: Arb<List<WorkPeriod>> = Arb.list(periodArb, 0..30)

        // --- Criteres d'acceptation explicites de l'issue -----------------------------------

        "20:00-22:00 and 21:00-23:00 overlap into 3 hours of connected time, not 4" {
            val union = IntervalUnion.of(period(0, 120), period(60, 180))

            union.totalDuration() shouldBe Duration.ofHours(3)
        }

        "two adjacent periods merge into a single period" {
            val union = IntervalUnion.of(period(0, 60), period(60, 120))

            union.periods shouldBe listOf(period(0, 120))
        }

        "union computed in two different input orders yields an identical result" {
            val forward = IntervalUnion.of(period(0, 60), period(30, 90), period(200, 260))
            val backward = IntervalUnion.of(period(200, 260), period(30, 90), period(0, 60))

            forward shouldBe backward
        }

        // --- Bornes : identiques, imbriquees, degenerees --------------------------------------

        "two identical periods collapse to one" {
            val union = IntervalUnion.of(period(0, 60), period(0, 60))

            union.periods shouldBe listOf(period(0, 60))
        }

        "a period nested inside another disappears into the outer one" {
            val union = IntervalUnion.of(period(0, 120), period(30, 60))

            union.periods shouldBe listOf(period(0, 120))
        }

        "two disjoint periods stay separate" {
            val union = IntervalUnion.of(period(0, 60), period(120, 180))

            union.periods shouldBe listOf(period(0, 60), period(120, 180))
        }

        "a degenerate, zero-length period touching a normal period merges without creating a gap" {
            val union = IntervalUnion.of(period(60, 60), period(60, 120))

            union.periods shouldBe listOf(period(60, 120))
        }

        "a degenerate period disjoint from everything else survives as its own zero-length period" {
            val union = IntervalUnion.of(period(0, 60), period(120, 120))

            union.periods shouldBe listOf(period(0, 60), period(120, 120))
        }

        "the empty set of periods produces the empty union" {
            IntervalUnion.of(emptyList()) shouldBe IntervalUnion.EMPTY
            IntervalUnion.EMPTY.totalDuration() shouldBe Duration.ZERO
        }

        // --- totalDuration ---------------------------------------------------------------------

        "totalDuration of disjoint periods is their sum" {
            val union = IntervalUnion.of(period(0, 30), period(60, 100))

            union.totalDuration() shouldBe Duration.ofMinutes(30 + 40)
        }

        // --- union() -----------------------------------------------------------------------------

        "union with the empty union is the identity" {
            val union = IntervalUnion.of(period(0, 60), period(120, 180))

            union.union(IntervalUnion.EMPTY) shouldBe union
        }

        "union of two unions merges across both" {
            val a = IntervalUnion.of(period(0, 60))
            val b = IntervalUnion.of(period(30, 90))

            a.union(b).periods shouldBe listOf(period(0, 90))
        }

        // --- intersect() ---------------------------------------------------------------------

        "intersect keeps only the overlap between two unions" {
            val a = IntervalUnion.of(period(0, 60), period(100, 160))
            val b = IntervalUnion.of(period(30, 130))

            a.intersect(b).periods shouldBe listOf(period(30, 60), period(100, 130))
        }

        "intersect with a disjoint union is empty" {
            val a = IntervalUnion.of(period(0, 60))
            val b = IntervalUnion.of(period(120, 180))

            a.intersect(b) shouldBe IntervalUnion.EMPTY
        }

        "intersect with a single window is a convenience over intersecting with that window's union" {
            val union = IntervalUnion.of(period(0, 60), period(100, 160))

            union.intersect(period(30, 130)) shouldBe union.intersect(IntervalUnion.of(period(30, 130)))
            union.intersect(period(30, 130)).periods shouldBe listOf(period(30, 60), period(100, 130))
        }

        // --- complement() ----------------------------------------------------------------------

        "complement of full coverage over its own window is empty" {
            val union = IntervalUnion.of(period(0, 60), period(60, 120))

            union.complement(period(0, 120)) shouldBe IntervalUnion.EMPTY
        }

        "complement finds the gaps left uncovered inside a window" {
            val union = IntervalUnion.of(period(60, 120), period(180, 240))

            union.complement(period(0, 300)).periods shouldBe
                listOf(period(0, 60), period(120, 180), period(240, 300))
        }

        "complement ignores periods entirely outside the window" {
            val union = IntervalUnion.of(period(0, 30), period(500, 600))

            union.complement(period(100, 200)).periods shouldBe listOf(period(100, 200))
        }

        "complement clips periods that straddle the window boundary" {
            val union = IntervalUnion.of(period(0, 50), period(150, 250))

            union.complement(period(20, 200)).periods shouldBe listOf(period(50, 150))
        }

        "complement of an uncovered degenerate window is that window itself" {
            val union = IntervalUnion.of(period(0, 60))

            union.complement(period(120, 120)) shouldBe IntervalUnion.of(period(120, 120))
        }

        "complement of a covered degenerate window is empty" {
            val union = IntervalUnion.of(period(0, 60))

            union.complement(period(30, 30)) shouldBe IntervalUnion.EMPTY
        }

        // --- Property-based tests (Kotest checkAll) ---------------------------------------------
        // Spec §10.4 : obligatoires sur IntervalUnion. C'est la seule facon de couvrir la
        // combinatoire des recouvrements, pas une formalite.

        "property: union is idempotent - unioning an already-unioned set changes nothing" {
            checkAll(iterations = 500, periodListArb) { periods ->
                val once = IntervalUnion.of(periods)
                val twice = IntervalUnion.of(once.periods)

                twice shouldBe once
            }
        }

        "property: union is commutative - input order never changes the result" {
            checkAll(iterations = 500, periodListArb) { periods ->
                val shuffled = periods.shuffled()

                IntervalUnion.of(periods) shouldBe IntervalUnion.of(shuffled)
            }
        }

        "property: the union's total duration never exceeds the sum of the individual durations" {
            checkAll(iterations = 1_000, periodListArb) { periods ->
                val sumOfDurations = periods.fold(Duration.ZERO) { acc, p -> acc + p.duration() }

                (IntervalUnion.of(periods).totalDuration() <= sumOfDurations) shouldBe true
            }
        }

        "property: unioning with the empty set is the identity" {
            checkAll(iterations = 500, periodListArb) { periods ->
                val union = IntervalUnion.of(periods)

                union.union(IntervalUnion.EMPTY) shouldBe union
                IntervalUnion.of(periods + emptyList()) shouldBe union
            }
        }

        "property: the resulting periods are always sorted and pairwise disjoint, never touching" {
            checkAll(iterations = 500, periodListArb) { periods ->
                val result = IntervalUnion.of(periods).periods

                result.zipWithNext().forEach { (current, next) ->
                    current.to.isBefore(next.from) shouldBe true
                }
            }
        }
    })
