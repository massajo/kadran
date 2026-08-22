package io.korallis.kadran.platform.security.token

import io.korallis.kadran.platform.security.MembershipRole
import io.korallis.kadran.platform.security.SecurityTestFixtures
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

private val NOW: Instant = Instant.parse("2026-08-19T10:15:00Z")
private const val ONE_HOUR = 3_600L

private fun tokenOf(
    secret: RefreshTokenSecret,
    familyId: RefreshTokenFamilyId,
    expiresAt: Instant = NOW.plusSeconds(ONE_HOUR),
) = StoredRefreshToken(
    digest = secret.digest(),
    familyId = familyId,
    accountId = SecurityTestFixtures.ACCOUNT_A,
    tenantId = SecurityTestFixtures.TENANT_A,
    role = MembershipRole.OWNER,
    issuedAt = NOW,
    expiresAt = expiresAt,
)

class RefreshTokenRotationTest :
    StringSpec({
        val random = SecureRandom()

        "two generated secrets never collide, and neither leaks through toString" {
            val first = RefreshTokenSecret.generate(random)
            val second = RefreshTokenSecret.generate(random)

            first shouldNotBe second
            first.digest() shouldNotBe second.digest()
            // Un jeton recopié dans une trace de pile ou une ligne de log reste utilisable
            // tant qu'il n'est pas expiré : il ne doit pas s'échapper par un toString().
            first.toString() shouldNotContain first.value
            first.digest().toString() shouldNotContain first.digest().value
        }

        "the same secret always produces the same digest" {
            val secret = RefreshTokenSecret.generate(random)

            RefreshTokenSecret.of(secret.value).digest() shouldBe secret.digest()
        }

        "a usable token stays usable until the instant it expires" {
            val token = tokenOf(RefreshTokenSecret.generate(random), RefreshTokenFamilyId(UUID.randomUUID()))

            token.stateAt(NOW) shouldBe RefreshTokenState.USABLE
            token.stateAt(token.expiresAt.minusSeconds(1)) shouldBe RefreshTokenState.USABLE
            // Borne exclusive : à l'instant exact de l'expiration, le jeton est périmé.
            token.stateAt(token.expiresAt) shouldBe RefreshTokenState.EXPIRED
        }

        "a consumed token reports the replay even after it would have expired" {
            val token =
                tokenOf(RefreshTokenSecret.generate(random), RefreshTokenFamilyId(UUID.randomUUID()))
                    .copy(consumedAt = NOW)

            // Si l'expiration primait, un rejeu tardif passerait pour une simple péremption
            // et n'alerterait personne.
            token.stateAt(token.expiresAt.plusSeconds(ONE_HOUR)) shouldBe RefreshTokenState.ALREADY_CONSUMED
        }

        "revocation wins over every other state" {
            val token =
                tokenOf(RefreshTokenSecret.generate(random), RefreshTokenFamilyId(UUID.randomUUID()))
                    .copy(consumedAt = NOW, revokedAt = NOW)

            token.stateAt(NOW) shouldBe RefreshTokenState.REVOKED
        }

        "the store finds a token by its digest, never by its secret" {
            val store = InMemoryRefreshTokenStore()
            val secret = RefreshTokenSecret.generate(random)
            store.save(tokenOf(secret, RefreshTokenFamilyId(UUID.randomUUID())))

            store.find(secret.digest()).shouldNotBeNull().tenantId shouldBe SecurityTestFixtures.TENANT_A
            store.find(RefreshTokenSecret.generate(random).digest()).shouldBeNull()
        }

        "consuming twice keeps the first timestamp" {
            val store = InMemoryRefreshTokenStore()
            val secret = RefreshTokenSecret.generate(random)
            store.save(tokenOf(secret, RefreshTokenFamilyId(UUID.randomUUID())))

            store.markConsumed(secret.digest(), NOW)
            store.markConsumed(secret.digest(), NOW.plusSeconds(ONE_HOUR))

            store.find(secret.digest()).shouldNotBeNull().consumedAt shouldBe NOW
        }

        "revoking a family spares the tokens of every other family" {
            val store = InMemoryRefreshTokenStore()
            val doomed = RefreshTokenFamilyId(UUID.randomUUID())
            val spared = RefreshTokenFamilyId(UUID.randomUUID())
            val inDoomed = RefreshTokenSecret.generate(random)
            val inSpared = RefreshTokenSecret.generate(random)
            store.save(tokenOf(inDoomed, doomed))
            store.save(tokenOf(inSpared, spared))

            store.revokeFamily(doomed, NOW)

            store.find(inDoomed.digest()).shouldNotBeNull().stateAt(NOW) shouldBe RefreshTokenState.REVOKED
            store.find(inSpared.digest()).shouldNotBeNull().stateAt(NOW) shouldBe RefreshTokenState.USABLE
        }

        "revoking an account reaches every family it opened" {
            val store = InMemoryRefreshTokenStore()
            val first = RefreshTokenSecret.generate(random)
            val second = RefreshTokenSecret.generate(random)
            store.save(tokenOf(first, RefreshTokenFamilyId(UUID.randomUUID())))
            store.save(tokenOf(second, RefreshTokenFamilyId(UUID.randomUUID())))

            store.revokeAllOf(SecurityTestFixtures.ACCOUNT_A, NOW)

            store.find(first.digest()).shouldNotBeNull().stateAt(NOW) shouldBe RefreshTokenState.REVOKED
            store.find(second.digest()).shouldNotBeNull().stateAt(NOW) shouldBe RefreshTokenState.REVOKED
        }
    })
