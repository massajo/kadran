package io.korallis.kadran.platform.observability

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Le nom d'une métrique se corrige mal après coup : il est repris dans des alertes, des
 * tableaux de bord et des règles d'enregistrement qui vivent hors du dépôt. Le refuser au
 * montage coûte une exception ; le découvrir en production coûte une migration de pile.
 */
class MetricNameTest :
    StringSpec({
        "a conforming name carries the kadran prefix and its bounded context" {
            MetricName.of("ingestion", "parse", "failures").value shouldBe "kadran.ingestion.parse.failures"
        }

        "the exposed name replaces dots with underscores" {
            // Le suffixe d'exposition — `_total`, `_seconds`, `_bytes` — reste a la charge
            // du registre : il depend de l'instrument, pas du nom.
            MetricName.of("ingestion", "parse", "failures").exposedBaseName shouldBe
                "kadran_ingestion_parse_failures"
        }

        "a suffix the registry appends itself is rejected" {
            listOf("total", "count", "sum", "max", "seconds", "bytes").forEach { suffix ->
                shouldThrow<IllegalArgumentException> { MetricName.of("ingestion", "parse", suffix) }
            }
        }

        "an exposition suffix stays allowed anywhere but last" {
            MetricName.of("outbox", "total", "depth").value shouldBe "kadran.outbox.total.depth"
        }

        "every spelling of the millisecond is rejected" {
            listOf("ms", "millis", "milliseconds").forEach { unit ->
                shouldThrow<IllegalArgumentException> { MetricName.of("performance", unit, "duration") }
            }
        }

        "a segment outside the template is rejected" {
            shouldThrow<IllegalArgumentException> { MetricName.of("Ingestion", "parse") }
            shouldThrow<IllegalArgumentException> { MetricName.of("ingestion", "parse_failures") }
            shouldThrow<IllegalArgumentException> { MetricName.of("ingestion", "parse.failures") }
            shouldThrow<IllegalArgumentException> { MetricName.of("ingestion", "") }
        }

        "a bounded context without a subject is not a metric" {
            shouldThrow<IllegalArgumentException> { MetricName.of("ingestion") }
        }
    })
