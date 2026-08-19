package io.korallis.kadran.platform.observability

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * ADR-011 tient dans une phrase — aucune métrique ne porte `tenant_id` — et se viole en un
 * caractère. Le test cadenasse les deux motifs : la cardinalité, qui fait tomber le
 * collecteur, et la confidentialité, qui fait sortir de la donnée du périmètre de §8.
 */
class MetricDimensionsTest :
    StringSpec({
        "dimensions carrying no identifier are accepted" {
            MetricDimensions.of("profile" to "uber-weekly", "outcome" to "failure") shouldBe
                mapOf("profile" to "uber-weekly", "outcome" to "failure")
        }

        "tenant_id is rejected" {
            shouldThrow<IllegalArgumentException> {
                MetricDimensions.of("tenant_id" to "8f3c0b12-0000-0000-0000-000000000000")
            }
        }

        "every aggregate identifier is rejected, not only those the ADR names" {
            listOf("driver_id", "outing_id", "import_batch_id", "id", "uuid").forEach { key ->
                shouldThrow<IllegalArgumentException> { MetricDimensions.of(key to "peu importe") }
            }
        }

        "a root naming a person or an account is rejected without any suffix" {
            // `tenant` sans `_id` porte la meme valeur et cree la meme serie temporelle :
            // le refus vise la cardinalite, pas l'orthographe.
            listOf("tenant", "driver", "user", "account").forEach { key ->
                shouldThrow<IllegalArgumentException> { MetricDimensions.of(key to "peu importe") }
            }
        }

        "a key outside the template is rejected" {
            shouldThrow<IllegalArgumentException> { MetricDimensions.of("Profile" to "uber") }
            shouldThrow<IllegalArgumentException> { MetricDimensions.of("profile.name" to "uber") }
        }

        "a single forbidden dimension rejects the whole set" {
            shouldThrow<IllegalArgumentException> {
                MetricDimensions.of("outcome" to "failure", "tenant_id" to "peu importe")
            }
        }

        "isForbidden makes the rule queryable without raising" {
            MetricDimensions.isForbidden("tenant_id") shouldBe true
            MetricDimensions.isForbidden("outcome") shouldBe false
        }
    })
