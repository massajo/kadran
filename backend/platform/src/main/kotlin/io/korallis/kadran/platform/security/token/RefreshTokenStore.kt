package io.korallis.kadran.platform.security.token

import io.korallis.kadran.platform.security.AccountId
import java.time.Instant

/**
 * Port sortant de la rotation des jetons de rafraîchissement.
 *
 * **Volontairement non scopé par tenant.** Le tenant est un résultat de l'authentification :
 * au moment où un jeton est présenté, aucun `TenantContext` n'est établi et
 * `TenantScopedQuery` ne peut pas l'être non plus. La clé de recherche est l'empreinte —
 * 256 bits aléatoires, donc non énumérable — et c'est le jeton retrouvé qui *porte* le
 * tenant. L'adaptateur persistant à venir écrira donc son `WHERE` sur `digest`, et la table
 * `refresh_token` restera hors du gabarit `TenantScopedTable` : elle est une table
 * d'infrastructure d'authentification, pas une table métier au sens de la spec §9.1.
 *
 * Aucune méthode ne rend le secret : il n'est jamais conservé.
 */
interface RefreshTokenStore {
    fun save(token: StoredRefreshToken)

    fun find(digest: RefreshTokenDigest): StoredRefreshToken?

    /** Marque le jeton consommé. Idempotent : un jeton déjà consommé garde son horodatage. */
    fun markConsumed(
        digest: RefreshTokenDigest,
        at: Instant,
    )

    /** Révoque tous les jetons non révoqués de la famille — réponse au rejeu. */
    fun revokeFamily(
        familyId: RefreshTokenFamilyId,
        at: Instant,
    )

    /** Révoque tous les jetons non révoqués du compte — c'est ce que fait la déconnexion. */
    fun revokeAllOf(
        accountId: AccountId,
        at: Instant,
    )
}
