package io.korallis.kadran.platform.security.web

import io.korallis.kadran.platform.security.AuthenticationResult

/** Corps de `POST /api/auth/login`. */
data class LoginRequest(
    val login: String,
    val password: String,
)

/** Corps de `POST /api/auth/refresh`. */
data class RefreshRequest(
    val refreshToken: String,
)

/**
 * Couple de jetons remis au client.
 *
 * `expiresIn` est une **durée en secondes**, pas une date : le client n'a pas besoin d'avoir
 * l'horloge à l'heure pour savoir quand renouveler, et l'écart entre son horloge et celle du
 * serveur cesse d'être un problème d'authentification.
 *
 * Le tenant et le rôle figurent dans la réponse alors qu'ils sont déjà dans le jeton : c'est
 * ce qui évite au front de décoder un JWT pour afficher un menu. La valeur qui fait autorité
 * reste celle du jeton — le serveur ne relit jamais ces deux champs-là.
 */
data class TokenPairResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val tenantId: String,
    val role: String,
    val tokenType: String = "Bearer",
)

internal fun AuthenticationResult.Granted.toResponse(): TokenPairResponse =
    TokenPairResponse(
        accessToken = accessToken,
        refreshToken = refreshToken.value,
        expiresIn = accessTokenExpiresIn.seconds,
        tenantId = tenantId.toString(),
        role = role.name,
    )
