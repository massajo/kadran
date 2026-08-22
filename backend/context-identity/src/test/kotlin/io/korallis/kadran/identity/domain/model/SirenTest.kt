package io.korallis.kadran.identity.domain.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/**
 * La clé de contrôle du SIREN, que la spec §9.4 exige de vérifier à l'étape 1.
 *
 * Les deux SIREN valides sont réels et vérifiables : ils servent de témoin. Le SIREN faux est
 * le premier, à un chiffre près — c'est exactement la faute de frappe qu'un contrôle de
 * longueur seul laisserait passer, et donc le seul cas qui prouve que la clé est calculée.
 */
class SirenTest :
    StringSpec({
        val danone = "732829320"

        "a real SIREN passes the Luhn check" {
            Siren.of(danone).value shouldBe danone
            Siren.of("404833048").value shouldBe "404833048"
        }

        "a single wrong digit is rejected" {
            shouldThrow<IllegalArgumentException> { Siren.of("732829321") }
        }

        "separators used on official documents are ignored" {
            Siren.of("732 829 320") shouldBe Siren.of(danone)
            Siren.of("732.829.320") shouldBe Siren.of(danone)
        }

        "anything but nine digits is rejected" {
            shouldThrow<IllegalArgumentException> { Siren.of("73282932") }
            shouldThrow<IllegalArgumentException> { Siren.of("7328293201") }
            shouldThrow<IllegalArgumentException> { Siren.of("73282932A") }
            shouldThrow<IllegalArgumentException> { Siren.of("") }
        }

        "parse returns null instead of throwing on untrusted input" {
            Siren.parse("732829321").shouldBeNull()
            Siren.parse(null).shouldBeNull()
            Siren.parse(danone) shouldBe Siren.of(danone)
        }

        "the readable form groups digits by three" {
            Siren.of(danone).formatted() shouldBe "732 829 320"
        }

        "the stored form carries no separator" {
            Siren.of("732 829 320").toString() shouldBe danone
        }
    })
