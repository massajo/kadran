package io.korallis.kadran.activity.domain.model

import io.korallis.kadran.core.Grain
import io.korallis.kadran.core.PlatformId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class PlatformProfileTest :
    StringSpec({
        "the Uber profile targets the Uber platform" {
            PlatformProfile.UBER.platform shouldBe PlatformId.UBER
        }

        "the Uber profile's finest grain is the trip, per the weekly statement and invoices" {
            PlatformProfile.UBER.finestGrain shouldBe Grain.TRIP
        }

        "the Uber profile carries exactly the capabilities available per spec section 3.7" {
            PlatformProfile.UBER.capabilities shouldContainExactlyInAnyOrder
                listOf(
                    SourceCapability.GROSS_REVENUE,
                    SourceCapability.NET_REVENUE,
                    SourceCapability.COMMISSION,
                    SourceCapability.VAT_BREAKDOWN,
                    SourceCapability.TRIP_COUNT,
                    SourceCapability.DISTANCE,
                    SourceCapability.TIP,
                    SourceCapability.INCENTIVE,
                    SourceCapability.PER_TRIP_TIMESTAMP,
                )
        }

        // §3.7 : « Uber ne fournit aucun temps de connexion » — la source de tout ce dossier.
        "the Uber profile does not carry ONLINE_TIME" {
            (SourceCapability.ONLINE_TIME in PlatformProfile.UBER.capabilities) shouldBe false
        }

        // §3.6 : la ligne « Moyen de paiement » du tableau comparatif vaut ❌ pour Uber.
        "the Uber profile does not carry PAYMENT_METHOD" {
            (SourceCapability.PAYMENT_METHOD in PlatformProfile.UBER.capabilities) shouldBe false
        }

        // §7.7/§8.1 : NomUtilisateur et AdresseUtilisateur ne sont jamais persistés, seul un
        // pseudonyme HMAC l'est — le système n'a donc pas la capacité de rendre l'identité de
        // la contrepartie, même si le fichier source la porte.
        "the Uber profile does not carry COUNTERPARTY_IDENTITY" {
            (SourceCapability.COUNTERPARTY_IDENTITY in PlatformProfile.UBER.capabilities) shouldBe false
        }
    })
