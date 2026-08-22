package io.korallis.kadran.platform.security.token

import io.korallis.kadran.platform.security.AccountId
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Implémentation en mémoire, **repli explicite tant que la table `refresh_token` n'existe
 * pas** (elle relève de KDN-27, qui livre le schéma du contexte `identity`).
 *
 * Ses deux limites sont assumées et doivent être levées avant tout déploiement à plus d'une
 * instance :
 *
 * - un redémarrage vide le magasin, donc déconnecte tout le monde ;
 * - deux instances ne partagent pas leurs familles, si bien qu'un rejeu détecté sur l'une ne
 *   révoque rien sur l'autre.
 *
 * Elle est en revanche exacte sur la sémantique — rotation, consommation, révocation de
 * famille — ce qui est précisément ce que l'adaptateur persistant devra reproduire.
 */
class InMemoryRefreshTokenStore : RefreshTokenStore {
    private val byDigest = ConcurrentHashMap<RefreshTokenDigest, StoredRefreshToken>()

    override fun save(token: StoredRefreshToken) {
        byDigest[token.digest] = token
    }

    override fun find(digest: RefreshTokenDigest): StoredRefreshToken? = byDigest[digest]

    override fun markConsumed(
        digest: RefreshTokenDigest,
        at: Instant,
    ) {
        byDigest.computeIfPresent(digest) { _, token ->
            if (token.consumedAt == null) token.copy(consumedAt = at) else token
        }
    }

    override fun revokeFamily(
        familyId: RefreshTokenFamilyId,
        at: Instant,
    ) = revokeWhere(at) { it.familyId == familyId }

    override fun revokeAllOf(
        accountId: AccountId,
        at: Instant,
    ) = revokeWhere(at) { it.accountId == accountId }

    private fun revokeWhere(
        at: Instant,
        matches: (StoredRefreshToken) -> Boolean,
    ) {
        byDigest.replaceAll { _, token ->
            if (matches(token) && token.revokedAt == null) token.copy(revokedAt = at) else token
        }
    }
}
