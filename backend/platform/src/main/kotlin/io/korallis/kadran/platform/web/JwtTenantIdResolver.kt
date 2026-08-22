package io.korallis.kadran.platform.web

import io.korallis.kadran.platform.security.token.authenticatedSubjectOf
import io.korallis.kadran.platform.tenancy.TenantId
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver

/**
 * Établit le tenant de la requête **depuis la revendication du jeton, et de nulle part
 * ailleurs** (spec §9.2).
 *
 * Aucun en-tête `X-Tenant-Id` n'est lu, ni ici ni ailleurs. Un en-tête est une valeur que le
 * client choisit : l'accepter reviendrait à laisser n'importe quel utilisateur authentifié
 * lire les données de n'importe quel exploitant, avec les quatre contrôles compensatoires de
 * l'ADR-001 travaillant consciencieusement sur le mauvais tenant. La revendication, elle, est
 * couverte par la signature — la modifier invalide le jeton.
 *
 * **Le jeton est décodé, donc revérifié, et pas simplement lu.** Ce filtre s'exécute en
 * `HIGHEST_PRECEDENCE`, avant la chaîne Spring Security, parce que le `correlation_id` doit
 * être posé avant la moindre ligne de log — y compris celles d'un refus d'authentification.
 * À cet instant, aucun `SecurityContext` n'existe encore. Lire la charge utile sans vérifier
 * la signature serait accepter un tenant forgé ; le seul coût de la vérification est une
 * seconde passe de HMAC-SHA-256, de l'ordre de la microseconde, et [JwtDecoder] est la même
 * instance que celle dont Spring Security se sert ensuite. Un jeton qui passe ici passe
 * là-bas, et réciproquement.
 *
 * Un jeton absent, périmé, mal signé ou incomplet donne `null` : la requête s'exécute sans
 * tenant et `requireTenantId()` lèvera si quoi que ce soit tente d'atteindre des données.
 * C'est Spring Security, ensuite, qui décide du statut renvoyé.
 */
class JwtTenantIdResolver(
    private val decoder: JwtDecoder,
    private val bearerTokenResolver: BearerTokenResolver = DefaultBearerTokenResolver(),
) : TenantIdResolver {
    override fun resolve(request: HttpServletRequest): TenantId? =
        // L'extraction lève, elle aussi, quand la requête présente deux jetons à la fois.
        // Une requête malformée n'a pas de tenant : elle n'a pas à faire échouer le filtre.
        runCatching { bearerTokenResolver.resolve(request)?.let(decoder::decode) }
            .getOrNull()
            ?.let(::authenticatedSubjectOf)
            ?.tenantId
}
