package io.korallis.kadran.activity.domain.model

import io.korallis.kadran.core.PlatformId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class CapabilityCheckTest :
    StringSpec({
        "a metric requiring only capabilities the profile has is allowed" {
            PlatformProfile.UBER.checkCapabilities(
                setOf(SourceCapability.NET_REVENUE, SourceCapability.TRIP_COUNT),
            ) shouldBe CapabilityCheck.Allowed
        }

        "a metric requiring no capability at all is allowed" {
            PlatformProfile.UBER.checkCapabilities(emptySet()) shouldBe CapabilityCheck.Allowed
        }

        "a metric requiring a capability the profile lacks is refused, naming it" {
            val result = PlatformProfile.UBER.checkCapabilities(setOf(SourceCapability.ONLINE_TIME))

            val refusal = result.shouldBeInstanceOf<CapabilityCheck.Refused>()
            refusal.platform shouldBe PlatformId.UBER
            refusal.missing shouldContainExactlyInAnyOrder listOf(SourceCapability.ONLINE_TIME)
        }

        "a refusal names every missing capability, not just the first one" {
            val required =
                setOf(SourceCapability.NET_REVENUE, SourceCapability.ONLINE_TIME, SourceCapability.PAYMENT_METHOD)

            val refusal = PlatformProfile.UBER.checkCapabilities(required).shouldBeInstanceOf<CapabilityCheck.Refused>()

            refusal.missing shouldContainExactlyInAnyOrder
                listOf(SourceCapability.ONLINE_TIME, SourceCapability.PAYMENT_METHOD)
        }

        "a Refused cannot be built without naming at least one missing capability" {
            shouldThrow<IllegalArgumentException> {
                CapabilityCheck.Refused(platform = PlatformId.UBER, missing = emptySet())
            }
        }
    })
