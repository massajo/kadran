package io.korallis.kadran.platform.observability

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * L'identifiant de corrélation vient du client et finit dans les logs : ce qui l'accepte est
 * une frontière de confiance, pas un simple utilitaire de format.
 */
class CorrelationIdTest :
    StringSpec({
        "un identifiant genere est unique" {
            CorrelationId.generate() shouldNotBe CorrelationId.generate()
        }

        "un identifiant entrant conforme est repris tel quel" {
            val entrant = "9f1c0d2e-4b6a-4c8d-9e0f-1a2b3c4d5e6f"

            CorrelationId.fromIncoming(entrant)?.value shouldBe entrant
        }

        "un identifiant absent en fait naitre un nouveau" {
            CorrelationId.parse(null).shouldBeNull()
            CorrelationId.fromIncoming(null).shouldNotBeNull()
        }

        "un identifiant porteur d'un saut de ligne est rejete" {
            // Sans ce refus, un client forge une fausse ligne de log chez l'agregateur.
            CorrelationId.parse("abcdefgh\nERROR faux message").shouldBeNull()
            CorrelationId.parse("abcdefgh\r\nERROR faux message").shouldBeNull()
        }

        "un identifiant hors gabarit est rejete" {
            CorrelationId.parse("").shouldBeNull()
            CorrelationId.parse("court").shouldBeNull()
            CorrelationId.parse("a".repeat(65)).shouldBeNull()
            CorrelationId.parse("avec espace ici").shouldBeNull()
            CorrelationId.parse("point;virgule;interdit").shouldBeNull()
        }

        "un identifiant rejete est remplace, jamais propage" {
            val forge = "abcdefgh\nERROR faux message"

            CorrelationId.fromIncoming(forge).value shouldNotBe forge
        }

        "les alphabets acceptes couvrent uuid et trace w3c" {
            CorrelationId.parse("4bf92f3577b34da6a3ce929d0e0e4736").shouldNotBeNull()
            CorrelationId.parse("import_batch-2026.08.19").shouldNotBeNull()
        }
    })
