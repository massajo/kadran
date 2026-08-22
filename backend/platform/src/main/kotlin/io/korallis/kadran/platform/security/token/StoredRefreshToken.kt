package io.korallis.kadran.platform.security.token

import io.korallis.kadran.platform.security.AccountId
import io.korallis.kadran.platform.security.MembershipRole
import io.korallis.kadran.platform.tenancy.TenantId
import java.time.Instant
import java.util.UUID

/**
 * Identifiant de **famille** : tous les jetons issus les uns des autres par rotation depuis
 * une même connexion le partagent.
 *
 * Il existe pour une seule raison : le rejeu. Voir [RefreshTokenState.ALREADY_CONSUMED].
 */
@JvmInline
value class RefreshTokenFamilyId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()
}

/** État d'un jeton de rafraîchissement à un instant donné. Un seul cas autorise l'échange. */
enum class RefreshTokenState {
    USABLE,
    EXPIRED,

    /**
     * Déjà échangé. **Le refus ne suffit pas : toute la famille est révoquée.**
     *
     * Un jeton à usage unique qui revient une seconde fois signifie que deux porteurs le
     * détiennent — le client légitime et quelqu'un d'autre. Il est impossible de savoir
     * lequel des deux se présente ; couper la famille entière ferme la session dans les deux
     * cas, ce qui coûte une reconnexion au légitime et met fin à l'accès de l'autre. Le
     * calcul est le bon sens : une reconnexion contre une session volée.
     */
    ALREADY_CONSUMED,
    REVOKED,
}

/**
 * Ce que le serveur conserve d'un jeton de rafraîchissement.
 *
 * Le secret n'y figure **pas** : seule son empreinte. Une base exfiltrée ne livre alors aucun
 * jeton utilisable.
 */
data class StoredRefreshToken(
    val digest: RefreshTokenDigest,
    val familyId: RefreshTokenFamilyId,
    val accountId: AccountId,
    val tenantId: TenantId,
    val role: MembershipRole,
    val issuedAt: Instant,
    val expiresAt: Instant,
    val consumedAt: Instant? = null,
    val revokedAt: Instant? = null,
) {
    /**
     * L'ordre des cas est significatif : une révocation prime sur tout le reste, et un jeton
     * déjà consommé doit être signalé comme tel même après son expiration — sans quoi un
     * rejeu tardif passerait pour une simple péremption et n'alerterait personne.
     */
    fun stateAt(now: Instant): RefreshTokenState =
        when {
            revokedAt != null -> RefreshTokenState.REVOKED
            consumedAt != null -> RefreshTokenState.ALREADY_CONSUMED
            !now.isBefore(expiresAt) -> RefreshTokenState.EXPIRED
            else -> RefreshTokenState.USABLE
        }
}
