package io.korallis.kadran.platform.security

import io.korallis.kadran.platform.observability.CorrelationId
import io.korallis.kadran.platform.security.token.RefreshTokenSecret
import io.korallis.kadran.platform.tenancy.TenantId
import java.time.Duration

/**
 * Ce que le client apprend d'une tentative d'authentification, et rien de plus.
 *
 * [Refused] ne porte **aucun motif**, et c'est la seule chose que ce type a à défendre :
 * distinguer « compte inconnu » de « mot de passe faux », ou « jeton périmé » de « jeton
 * rejoué », livre au client une information dont seul un attaquant a l'usage. Le motif existe,
 * mais il part dans le journal d'audit (spec §8.4).
 */
sealed interface AuthenticationResult {
    /**
     * @property accessTokenExpiresIn durée restante, pas date d'expiration : le client la
     *   compare à sa propre horloge sans avoir à la synchroniser sur celle du serveur.
     */
    data class Granted(
        val accessToken: String,
        val accessTokenExpiresIn: Duration,
        val refreshToken: RefreshTokenSecret,
        val accountId: AccountId,
        val tenantId: TenantId,
        val role: MembershipRole,
    ) : AuthenticationResult

    data object Refused : AuthenticationResult
}

/**
 * Ce que la couche web sait de la requête et que l'audit réclame (spec §8.4.2).
 *
 * Passé en paramètre plutôt que lu depuis un `ThreadLocal` : un rejeu, un job ou un test
 * doivent pouvoir authentifier sans requête HTTP en cours.
 */
data class AuthenticationRequestContext(
    val correlationId: CorrelationId,
    val ipAddress: String? = null,
    val userAgent: String? = null,
)
