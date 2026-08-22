package io.korallis.kadran.platform.security.token

import io.korallis.kadran.platform.security.AuthenticatedSubject
import io.korallis.kadran.platform.security.MembershipRole
import io.korallis.kadran.platform.security.SecurityTestFixtures
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwtException
import java.time.Instant
import java.util.Base64

/**
 * Le décodeur valide contre l'horloge **réelle** : une date d'émission figée dans le passé
 * ferait échouer tous les tests le lendemain de leur écriture. Tout est donc relatif à
 * maintenant, ce qui reste parfaitement déterministe.
 */
private val NOW: Instant = Instant.now()

private const val EXPIRED_BY_SECONDS = 3_600L
private const val SHORT_TTL_SECONDS = 600L

private fun subject() =
    AuthenticatedSubject(
        accountId = SecurityTestFixtures.ACCOUNT_A,
        tenantId = SecurityTestFixtures.TENANT_A,
        role = MembershipRole.DRIVER,
    )

/**
 * Réécrit la charge utile d'un JWT **sans toucher à sa signature** — ce que ferait un client
 * décidant d'être d'un autre tenant. C'est le dernier critère d'acceptation de l'issue.
 */
private fun tamperTenant(
    token: String,
    tenantId: String,
): String {
    val parts = token.split(".")
    val payload = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
    val altered = payload.replace(SecurityTestFixtures.TENANT_A.toString(), tenantId)
    val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(altered.toByteArray(Charsets.UTF_8))
    return listOf(parts[0], encoded, parts[2]).joinToString(".")
}

/** Un jeton parfaitement signé par ce codec, mais dépourvu des revendications Kadran. */
private fun issueWithoutClaims(codec: JwtCodec): String =
    codec.encoder
        .encode(
            JwtEncoderParameters.from(
                JwtClaimsSet
                    .builder()
                    .issuer(SecurityTestFixtures.properties().issuer)
                    .subject(SecurityTestFixtures.ACCOUNT_A.toString())
                    .issuedAt(NOW)
                    .expiresAt(NOW.plusSeconds(SHORT_TTL_SECONDS))
                    .build(),
            ),
        ).tokenValue

class JwtCodecTest :
    StringSpec({
        "a freshly issued token decodes back to the subject it was issued for" {
            val codec = SecurityTestFixtures.codec()
            val issuer = AccessTokenIssuer(codec.encoder, SecurityTestFixtures.properties())

            val issued = issuer.issue(subject(), NOW)
            val decoded = authenticatedSubjectOf(codec.decoder.decode(issued.value)).shouldNotBeNull()

            decoded shouldBe subject()
            issued.expiresAt shouldBe NOW.plus(SecurityTestFixtures.properties().accessTokenTtl)
        }

        "a token whose tenant claim was rewritten no longer verifies" {
            val codec = SecurityTestFixtures.codec()
            val issued = AccessTokenIssuer(codec.encoder, SecurityTestFixtures.properties()).issue(subject(), NOW)

            val forged = tamperTenant(issued.value, SecurityTestFixtures.TENANT_B.toString())

            // La revendication est couverte par la signature : la réécrire la casse.
            shouldThrow<JwtException> { codec.decoder.decode(forged) }
        }

        "a token signed with another key is rejected" {
            val mine = SecurityTestFixtures.codec()
            val theirs = SecurityTestFixtures.codec(secret = "une-autre-cle-de-trente-deux-octets-au-moins")
            val issued = AccessTokenIssuer(theirs.encoder, SecurityTestFixtures.properties()).issue(subject(), NOW)

            shouldThrow<JwtException> { mine.decoder.decode(issued.value) }
        }

        "an expired token is rejected" {
            val codec = SecurityTestFixtures.codec()
            val issuer = AccessTokenIssuer(codec.encoder, SecurityTestFixtures.properties())

            val stale = issuer.issue(subject(), NOW.minusSeconds(EXPIRED_BY_SECONDS))

            shouldThrow<JwtException> { codec.decoder.decode(stale.value) }
        }

        "a token from another issuer is rejected" {
            val codec = SecurityTestFixtures.codec()
            val foreign = SecurityTestFixtures.properties().copy(issuer = "quelqu-un-d-autre")

            val issued = AccessTokenIssuer(codec.encoder, foreign).issue(subject(), NOW)

            shouldThrow<JwtException> { codec.decoder.decode(issued.value) }
        }

        "a correctly signed token carrying no tenant is rejected, not merely tenant-less" {
            val codec = SecurityTestFixtures.codec()

            // Sans KadranClaimsValidator, ce jeton serait *authentifié* — sa signature est
            // bonne — puis exploserait plus loin sur un requireTenantId(), en 500.
            shouldThrow<JwtException> { codec.decoder.decode(issueWithoutClaims(codec)) }
        }
    })
