package io.korallis.kadran.identity.domain.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Normalisation des libellés saisis à la main.
 *
 * L'enjeu n'est pas cosmétique : deux raisons sociales qui ne diffèrent que par un espace
 * doivent être la même valeur, sans quoi la déduplication et la recherche mentent.
 */
class NamesTest :
    StringSpec({
        "a legal name is trimmed and its inner whitespace collapsed" {
            LegalName.of("  Kadran   SASU \n") shouldBe LegalName.of("Kadran SASU")
            LegalName.of(" Kadran SASU ").value shouldBe "Kadran SASU"
        }

        "a blank legal name is rejected" {
            shouldThrow<IllegalArgumentException> { LegalName.of("   ") }
            shouldThrow<IllegalArgumentException> { LegalName.of("") }
        }

        "a legal name longer than the column is rejected" {
            val tooLong = "K".repeat(LegalName.MAX_LENGTH + 1)

            shouldThrow<IllegalArgumentException> { LegalName.of(tooLong) }
            LegalName.of("K".repeat(LegalName.MAX_LENGTH)).value.length shouldBe LegalName.MAX_LENGTH
        }

        "a legal name renders as its own text" {
            LegalName.of("Kadran SASU").toString() shouldBe "Kadran SASU"
        }

        "a driver name is normalised the same way" {
            DriverName.of("  Jean   Dupont ") shouldBe DriverName.of("Jean Dupont")
            DriverName.of("Jean Dupont").toString() shouldBe "Jean Dupont"
        }

        "a blank or oversized driver name is rejected" {
            shouldThrow<IllegalArgumentException> { DriverName.of(" ") }
            shouldThrow<IllegalArgumentException> { DriverName.of("J".repeat(DriverName.MAX_LENGTH + 1)) }
        }
    })
