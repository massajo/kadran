package io.korallis.kadran.platform.security.token

import io.korallis.kadran.platform.security.AuthenticatedSubject
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import java.time.Instant
import java.util.UUID

/** Un jeton d'accès signé, et l'instant où il cesse d'être accepté. */
data class IssuedAccessToken(
    val value: String,
    val expiresAt: Instant,
)

/**
 * Émet le jeton d'accès.
 *
 * Signature symétrique HS256 : un seul service émet et vérifie, aucun tiers n'a besoin de
 * vérifier sans pouvoir signer. Le jour où un second service devra valider les jetons sans
 * pouvoir en forger, ce sera RS256 et une clé publique — c'est le seul motif de changer.
 */
class AccessTokenIssuer(
    private val encoder: JwtEncoder,
    private val properties: JwtProperties,
) {
    fun issue(
        subject: AuthenticatedSubject,
        issuedAt: Instant,
    ): IssuedAccessToken {
        val expiresAt = issuedAt.plus(properties.accessTokenTtl)
        val claims =
            JwtClaimsSet
                .builder()
                .issuer(properties.issuer)
                .subject(subject.accountId.toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                // Un identifiant unique par jeton : il n'est utile à personne aujourd'hui,
                // et il est la seule façon de rendre un jeton d'accès traçable — donc, plus
                // tard, révocable — sans réémettre tous ceux en circulation.
                .id(UUID.randomUUID().toString())
                .claim(AccessTokenClaims.TENANT_ID, subject.tenantId.toString())
                .claim(AccessTokenClaims.ROLE, subject.role.name)
                .build()

        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        return IssuedAccessToken(
            value = encoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue,
            expiresAt = expiresAt,
        )
    }
}
