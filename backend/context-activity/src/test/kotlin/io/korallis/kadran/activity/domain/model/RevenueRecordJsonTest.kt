package io.korallis.kadran.activity.domain.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

/**
 * [RevenueRecordJson] à part de l'agrégat : les six variantes du type somme, exercées
 * directement, sans passer par `platformExtras` d'un `RevenueRecord` réel. Les valeurs
 * choisies illustrent le cas d'usage réel de la spec §7.6 — les champs Bolt sans équivalent
 * Uber (`Niveau`, `Score chauffeur`, `Courses en espèces activées`) — sans que ce module
 * implémente le profil Bolt lui-même (ADR-008 : hors périmètre v1).
 */
class RevenueRecordJsonTest :
    StringSpec({
        "empty() is the empty object, the default platformExtras of a platform without specifics" {
            RevenueRecordJson.empty() shouldBe RevenueRecordJson.Obj(emptyMap())
        }

        "a boolean field round-trips as constructed" {
            val eligible = RevenueRecordJson.Bool(true)

            eligible.value shouldBe true
        }

        "a numeric field carries an exact BigDecimal, never a Double" {
            val driverScore = RevenueRecordJson.Num(BigDecimal("87.5"))

            driverScore.value shouldBe BigDecimal("87.5")
        }

        "an object nests every JSON variant behind stable, platform-prefixed keys (spec §7.6)" {
            val boltExtras =
                RevenueRecordJson.Obj(
                    mapOf(
                        "bolt.level" to RevenueRecordJson.Str("Gold"),
                        "bolt.driverScore" to RevenueRecordJson.Num(BigDecimal("87.5")),
                        "bolt.cashTripsEnabled" to RevenueRecordJson.Bool(true),
                        "bolt.activeCategories" to
                            RevenueRecordJson.Arr(listOf(RevenueRecordJson.Str("Bolt"), RevenueRecordJson.Str("XL"))),
                        "bolt.comment" to RevenueRecordJson.Null,
                    ),
                )

            boltExtras.fields["bolt.level"] shouldBe RevenueRecordJson.Str("Gold")
            boltExtras.fields["bolt.comment"] shouldBe RevenueRecordJson.Null
        }
    })
