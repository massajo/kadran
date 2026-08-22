package io.korallis.kadran.identity.domain.model

import io.korallis.kadran.core.TenantId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Instant

/**
 * Cycle de vie de l'exploitant — sans le moindre mock : le domaine est pur (`CLAUDE.md` §2.4).
 * Tout ce qui vient du dehors, l'identifiant et l'horodatage, est passé en paramètre.
 */
class TenantTest :
    StringSpec({
        val now = Instant.parse("2026-08-22T09:00:00Z")
        val id = TenantId.of("11111111-1111-1111-1111-111111111111")

        fun register(): Transition<Tenant> =
            Tenant.register(id, LegalName.of("Kadran SASU"), Siren.of("732829320"), now)

        "registering a tenant opens the onboarding at its first step" {
            val (tenant, event) = register()

            tenant.id shouldBe id
            tenant.onboardingStatus shouldBe OnboardingStatus.IDENTITY
            tenant.isOnboarded shouldBe false
            tenant.isClosed shouldBe false
            tenant.closedAt.shouldBeNull()
            tenant.createdAt shouldBe now
            event shouldBe TenantRegistered(id, Siren.of("732829320"), now)
        }

        "the onboarding step is persisted so the wizard can be resumed" {
            val tenant = register().state.resumeOnboardingAt(OnboardingStatus.COST_MODEL)

            tenant.onboardingStatus shouldBe OnboardingStatus.COST_MODEL
            tenant.isOnboarded shouldBe false
        }

        "the wizard may step backwards, since a draft is editable" {
            val tenant =
                register()
                    .state
                    .resumeOnboardingAt(OnboardingStatus.COST_MODEL)
                    .resumeOnboardingAt(OnboardingStatus.VEHICLE)

            tenant.onboardingStatus shouldBe OnboardingStatus.VEHICLE
        }

        "a completed onboarding is readable as such" {
            register().state.resumeOnboardingAt(OnboardingStatus.COMPLETED).isOnboarded shouldBe true
        }

        "closing a tenant dates it and reports the event" {
            val closedAt = now.plusSeconds(3_600)

            val (tenant, event) = register().state.close(closedAt)

            tenant.isClosed shouldBe true
            tenant.closedAt shouldBe closedAt
            event.shouldBeInstanceOf<TenantClosed>()
            event shouldBe TenantClosed(id, closedAt)
        }

        "closing twice is refused, so the audit trail never records the same fact twice" {
            val closed = register().state.close(now).state

            shouldThrow<IllegalStateException> { closed.close(now.plusSeconds(1)) }
        }

        // Ex-`ck_tenant_closed_after_creation`, retiree en KDN-137 : le domaine tient seul
        // l'invariant desormais, `createdAt` etant porte par l'agregat.
        "a tenant cannot close before it was created" {
            val tenant = register().state

            shouldThrow<IllegalArgumentException> { tenant.close(now.minusSeconds(1)) }
        }

        "closing exactly at creation is allowed, the boundary is inclusive" {
            val (tenant, event) = register().state.close(now)

            tenant.closedAt shouldBe now
            event shouldBe TenantClosed(id, now)
        }

        "a closed tenant cannot resume its onboarding" {
            val closed = register().state.close(now).state

            shouldThrow<IllegalStateException> { closed.resumeOnboardingAt(OnboardingStatus.VEHICLE) }
        }

        "two tenants sharing every attribute but their identifier are distinct" {
            val other = register().state.copy(id = TenantId.of("22222222-2222-2222-2222-222222222222"))

            (other == register().state) shouldBe false
            other.toString().contains("22222222") shouldBe true
        }
    })
