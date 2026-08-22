package io.korallis.kadran.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.util.Currency

class MoneyTest :
    StringSpec({
        val usd = Currency.getInstance("USD")

        "adding two amounts in the same currency sums the cents" {
            (Money.euroCents(1_000) + Money.euroCents(250)) shouldBe Money.euroCents(1_250)
        }

        "subtracting can go negative — a loss is a valid amount" {
            (Money.euroCents(100) - Money.euroCents(300)) shouldBe Money.euroCents(-200)
        }

        "combining two currencies is refused" {
            shouldThrow<IllegalArgumentException> { Money.euroCents(100) + Money(100, usd) }
            shouldThrow<IllegalArgumentException> { Money.euroCents(100) - Money(100, usd) }
        }

        "applying a ratio rounds to the nearest cent, half up" {
            // 148.30 EUR * 0.25 = 37.075 -> arrondi a 37.08
            (Money.euroCents(14_830) * Ratio(BigDecimal("0.25"))) shouldBe Money.euroCents(3_708)
            // .5 pile rond vers le haut
            (Money.euroCents(1) * Ratio(BigDecimal("0.5"))) shouldBe Money.euroCents(1)
        }

        "a negative amount is reported as such" {
            Money.euroCents(-1).isNegative shouldBe true
            Money.euroCents(0).isNegative shouldBe false
        }

        "unary minus flips the sign without touching the currency" {
            (-Money.euroCents(500)) shouldBe Money.euroCents(-500)
        }
    })
