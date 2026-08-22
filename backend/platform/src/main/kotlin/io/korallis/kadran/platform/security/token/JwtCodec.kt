package io.korallis.kadran.platform.security.token

import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.nimbusds.jose.proc.SecurityContext
import io.korallis.kadran.platform.security.AccountId
import io.korallis.kadran.platform.security.AuthenticatedSubject
import io.korallis.kadran.platform.security.MembershipRole
import io.korallis.kadran.platform.tenancy.TenantId
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtIssuerValidator
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder

/**
 * Émission et vérification des jetons d'accès, **dos à dos et sur la même clé**.
 *
 * Les deux vivent dans le même objet pour qu'il soit impossible de les configurer
 * séparément : un décodeur qui accepterait un émetteur ou un algorithme que l'encodeur
 * n'utilise pas est un trou de sécurité qui ne se voit dans aucun test fonctionnel.
 */
class JwtCodec(
    properties: JwtProperties,
) {
    private val key = JwtSecretKeySource.keyFrom(properties)

    val encoder: JwtEncoder = NimbusJwtEncoder(ImmutableSecret<SecurityContext>(key))

    /**
     * Trois validations, dans cet ordre : la fenêtre temporelle, l'émetteur, puis les
     * revendications propres à Kadran.
     *
     * Les validateurs sont assemblés à la main plutôt que repris de `JwtValidators` : c'est
     * la seule façon de savoir, en lisant, ce qui est vérifié. Un défaut de bibliothèque qui
     * changerait de contenu au fil des versions déciderait sinon de la sécurité du système.
     */
    val decoder: JwtDecoder =
        NimbusJwtDecoder
            .withSecretKey(key)
            // Épinglé : sans cela, l'algorithme accepté se déduit du jeton présenté, ce qui
            // est le point de départ de toute la famille d'attaques par substitution.
            .macAlgorithm(MacAlgorithm.HS256)
            .build()
            .apply {
                setJwtValidator(
                    DelegatingOAuth2TokenValidator(
                        JwtTimestampValidator(),
                        JwtIssuerValidator(properties.issuer),
                        KadranClaimsValidator,
                    ),
                )
            }
}

/**
 * Refuse un jeton dont le sujet, le tenant ou le rôle manque ou est illisible.
 *
 * Sans ce contrôle, un tel jeton serait *authentifié* — sa signature est bonne — puis
 * échouerait plus loin sur un `requireTenantId()`, en 500 et sans trace exploitable. Un jeton
 * incomplet est un jeton invalide, et se refuse là où les jetons se refusent.
 */
object KadranClaimsValidator : OAuth2TokenValidator<Jwt> {
    private val incomplete =
        OAuth2Error(
            "invalid_token",
            "le jeton ne porte pas un sujet, un tenant et un role exploitables",
            null,
        )

    override fun validate(token: Jwt): OAuth2TokenValidatorResult =
        if (authenticatedSubjectOf(token) == null) {
            OAuth2TokenValidatorResult.failure(incomplete)
        } else {
            OAuth2TokenValidatorResult.success()
        }
}

/**
 * Traduit un jeton vérifié en [AuthenticatedSubject], ou `null` s'il n'en porte pas un
 * complet. **Seul point de lecture des revendications Kadran** : ni le filtre de tenant ni le
 * contrôleur ne relisent une revendication à la main.
 */
fun authenticatedSubjectOf(token: Jwt): AuthenticatedSubject? {
    val accountId = AccountId.parse(token.subject)
    val tenantId = TenantId.parse(token.getClaimAsString(AccessTokenClaims.TENANT_ID))
    val role = MembershipRole.parse(token.getClaimAsString(AccessTokenClaims.ROLE))
    return if (accountId != null && tenantId != null && role != null) {
        AuthenticatedSubject(accountId, tenantId, role)
    } else {
        null
    }
}
