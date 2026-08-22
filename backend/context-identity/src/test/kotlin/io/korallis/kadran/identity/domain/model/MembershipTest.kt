package io.korallis.kadran.identity.domain.model

import io.korallis.kadran.core.DriverId
import io.korallis.kadran.core.TenantId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.UUID

/**
 * L'appartenance porte à elle seule l'anticipation du modèle flotte : le rôle, la date de
 * prise d'effet, la date de fin, et le compte d'authentification que KDN-18 y rattachera.
 */
class MembershipTest :
    StringSpec({
        val tenantId = TenantId.of("11111111-1111-1111-1111-111111111111")
        val driverId = DriverId.of("33333333-3333-3333-3333-333333333333")
        val membershipId = MembershipId(UUID.fromString("44444444-4444-4444-4444-444444444444"))
        val account = AccountId(UUID.fromString("55555555-5555-5555-5555-555555555555"))
        val hiredAt = Instant.parse("2026-01-05T08:00:00Z")

        fun invite(role: MembershipRole = MembershipRole.DRIVER): Transition<Membership> =
            Membership.invite(membershipId, tenantId, driverId, role, hiredAt)

        "an invitation opens a period and reports the event" {
            val (membership, event) = invite(MembershipRole.OWNER)

            membership.role shouldBe MembershipRole.OWNER
            membership.isOpen shouldBe true
            membership.period.validFrom shouldBe hiredAt
            membership.period.validUntil.shouldBeNull()
            membership.accountId.shouldBeNull()
            event shouldBe MemberInvited(tenantId, membershipId, driverId, MembershipRole.OWNER, hiredAt)
        }

        "an invitation may already carry its authentication account" {
            val membership =
                Membership
                    .invite(membershipId, tenantId, driverId, MembershipRole.DRIVER, hiredAt, account)
                    .state

            membership.accountId shouldBe account
        }

        "a membership is active from its start date, and not before" {
            val membership = invite().state

            membership.isActiveAt(hiredAt) shouldBe true
            membership.isActiveAt(hiredAt.plusSeconds(1)) shouldBe true
            membership.isActiveAt(hiredAt.minusSeconds(1)) shouldBe false
        }

        "changing the role reports both the previous and the new one" {
            val (membership, event) = invite().state.changeRoleTo(MembershipRole.MANAGER, hiredAt)

            membership.role shouldBe MembershipRole.MANAGER
            event shouldBe
                MemberRoleChanged(tenantId, membershipId, MembershipRole.DRIVER, MembershipRole.MANAGER, hiredAt)
        }

        "changing to the very same role is refused, so the journal stays informative" {
            shouldThrow<IllegalArgumentException> { invite().state.changeRoleTo(MembershipRole.DRIVER, hiredAt) }
        }

        "a revoked membership no longer changes role" {
            val revoked = invite().state.revokeAt(hiredAt.plusSeconds(60)).state

            shouldThrow<IllegalStateException> { revoked.changeRoleTo(MembershipRole.OWNER, hiredAt) }
        }

        "revoking closes the period instead of erasing the membership" {
            val leftAt = Instant.parse("2026-06-30T22:00:00Z")

            val (membership, event) = invite().state.revokeAt(leftAt)

            membership.isOpen shouldBe false
            membership.period.validUntil shouldBe leftAt
            membership.isActiveAt(leftAt.minusSeconds(1)) shouldBe true
            membership.isActiveAt(leftAt) shouldBe false
            event shouldBe MembershipRevoked(tenantId, membershipId, leftAt)
        }

        "revoking twice is refused" {
            val revoked = invite().state.revokeAt(hiredAt.plusSeconds(60)).state

            shouldThrow<IllegalStateException> { revoked.revokeAt(hiredAt.plusSeconds(120)) }
        }

        "a period cannot close before it opens" {
            shouldThrow<IllegalArgumentException> { MembershipPeriod(hiredAt, hiredAt.minusSeconds(1)) }
            shouldThrow<IllegalArgumentException> { MembershipPeriod(hiredAt, hiredAt) }
            shouldThrow<IllegalArgumentException> { invite().state.revokeAt(hiredAt.minusSeconds(1)) }
        }

        "attaching an account is how KDN-18 will bind a login to a tenant and a role" {
            val attached = invite().state.attachTo(account)

            attached.accountId shouldBe account
            attached.attachTo(account).accountId shouldBe account
        }

        "attaching a second, different account is refused" {
            val attached = invite().state.attachTo(account)
            val other = AccountId(UUID.fromString("66666666-6666-6666-6666-666666666666"))

            shouldThrow<IllegalStateException> { attached.attachTo(other) }
        }

        "identifiers and roles render legibly for the audit journal" {
            membershipId.toString() shouldBe "44444444-4444-4444-4444-444444444444"
            account.toString() shouldBe "55555555-5555-5555-5555-555555555555"
            (MembershipId.next() == MembershipId.next()) shouldBe false
            MembershipRole.entries.map { it.name } shouldBe listOf("OWNER", "MANAGER", "DRIVER")
        }
    })
