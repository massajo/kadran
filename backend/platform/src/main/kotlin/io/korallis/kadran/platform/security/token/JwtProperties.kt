package io.korallis.kadran.platform.security.token

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Réglages du jeton d'accès. Tous surchargeables par variable d'environnement (spec §10.6).
 *
 * @property secret clé HMAC, en clair ou en base64. **Vide par défaut, et c'est délibéré** :
 *   il n'y a aucun secret en dur dans ce dépôt. Une valeur vide fait tirer une clé aléatoire
 *   au démarrage (voir [JwtSecretKeySource]) — le développement fonctionne sans rien
 *   configurer, et un déploiement qui aurait oublié `KADRAN_JWT_SECRET` se signale de
 *   lui-même en invalidant tous les jetons à chaque redémarrage, plutôt que d'accepter en
 *   silence une clé publiquement connue.
 * @property accessTokenTtl court par construction : le jeton d'accès n'est pas révocable, sa
 *   fenêtre d'abus est donc exactement sa durée de vie. C'est le jeton de rafraîchissement,
 *   lui révocable, qui porte la durée de la session.
 * @property refreshTokenTtl durée maximale d'une session sans ressaisie du mot de passe.
 */
@ConfigurationProperties(prefix = "kadran.security.jwt")
data class JwtProperties(
    val secret: String = "",
    val issuer: String = "kadran",
    val accessTokenTtl: Duration = Duration.ofMinutes(DEFAULT_ACCESS_TTL_MINUTES),
    val refreshTokenTtl: Duration = Duration.ofDays(DEFAULT_REFRESH_TTL_DAYS),
) {
    private companion object {
        const val DEFAULT_ACCESS_TTL_MINUTES = 15L
        const val DEFAULT_REFRESH_TTL_DAYS = 30L
    }
}
