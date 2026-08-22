package io.korallis.kadran.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class DistanceTest :
    StringSpec({
        "a negative distance is rejected" {
            shouldThrow<IllegalArgumentException> { Distance(-1) }
        }

        "adding two distances sums the meters" {
            (Distance(1_000) + Distance(300)) shouldBe Distance(1_300)
        }

        "zero is a valid distance" {
            Distance.zero() shouldBe Distance(0)
        }
    })
