package io.korallis.kadran.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class PlatformIdTest :
    StringSpec({
        // ADR-008 (spec §2.3) : Bolt et Heetch sont hors périmètre d'implémentation en v1, mais
        // leurs profils sont déjà spécifiés (§3.4, §3.6) et l'enum doit déjà les nommer pour que
        // KDN-36 ne les ait pas à réintroduire plus tard.
        "PlatformId names every platform from spec section 7.2" {
            PlatformId.entries.map { it.name } shouldContainExactlyInAnyOrder
                listOf("UBER", "BOLT", "HEETCH", "FREENOW", "ALLOCAB", "DIRECT", "OTHER")
        }
    })

class GrainTest :
    StringSpec({
        "Grain is ordered from the finest to the coarsest" {
            Grain.entries.map { it.name } shouldBe listOf("TRIP", "OUTING", "DAY", "PERIOD")
        }

        "TRIP is finer than PERIOD, matching declaration order" {
            (Grain.TRIP.ordinal < Grain.PERIOD.ordinal) shouldBe true
        }
    })
