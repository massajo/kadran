package io.korallis.kadran.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant

class WorkPeriodTest :
    StringSpec({
        val t0 = Instant.parse("2026-06-02T20:00:00Z")
        val t1 = Instant.parse("2026-06-03T02:00:00Z")

        "a period ending before it starts is rejected" {
            shouldThrow<IllegalArgumentException> { WorkPeriod(t1, t0) }
        }

        "a single instant is a valid, zero-length period" {
            WorkPeriod(t0, t0).duration() shouldBe Duration.ZERO
        }

        "duration is the elapsed time between from and to" {
            WorkPeriod(t0, t1).duration() shouldBe Duration.ofHours(6)
        }

        "two periods that share time overlap" {
            WorkPeriod(t0, t1).overlaps(WorkPeriod(t0.plusSeconds(1), t1.plusSeconds(1))) shouldBe true
        }

        "two periods touching only at their boundary do not overlap" {
            // [t0, t1] et [t1, t1+1h[ : le point de contact ne compte pas comme un chevauchement,
            // sans quoi deux sessions consecutives se compteraient comme simultanees.
            WorkPeriod(t0, t1).overlaps(WorkPeriod(t1, t1.plusSeconds(3_600))) shouldBe false
        }

        "two disjoint periods do not overlap" {
            WorkPeriod(t0, t0.plusSeconds(60)).overlaps(WorkPeriod(t1, t1.plusSeconds(60))) shouldBe false
        }
    })
