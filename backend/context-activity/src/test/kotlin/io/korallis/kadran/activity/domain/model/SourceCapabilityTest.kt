package io.korallis.kadran.activity.domain.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder

class SourceCapabilityTest :
    StringSpec({
        "SourceCapability names every capability from spec section 4.2" {
            SourceCapability.entries.map { it.name } shouldContainExactlyInAnyOrder
                listOf(
                    "GROSS_REVENUE",
                    "NET_REVENUE",
                    "COMMISSION",
                    "VAT_BREAKDOWN",
                    "TRIP_COUNT",
                    "ONLINE_TIME",
                    "DISTANCE",
                    "PAYMENT_METHOD",
                    "TIP",
                    "INCENTIVE",
                    "PER_TRIP_TIMESTAMP",
                    "COUNTERPARTY_IDENTITY",
                )
        }
    })
