package io.korallis.kadran.platform.security

import java.util.UUID

/**
 * Identifiant du compte qui s'authentifie — l'`actor_id` des journaux d'audit (spec §8.4).
 *
 * Distinct de `DriverId` : un compte est un moyen de se connecter, un chauffeur est une
 * personne exploitée dans le modèle métier. Les deux coïncident souvent chez l'indépendant
 * seul, jamais par construction — un gestionnaire de flotte a un compte sans être chauffeur
 * (spec §9.3).
 *
 * Comme [io.korallis.kadran.platform.tenancy.TenantId], l'UUID est opaque : aucune PII, donc
 * admissible dans un log et dans le sujet d'un jeton.
 */
@JvmInline
value class AccountId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()

    companion object {
        /** Retourne `null` plutôt que de lever : la source est un jeton, donc non fiable. */
        fun parse(raw: String?): AccountId? =
            raw?.takeIf { it.isNotBlank() }?.let {
                runCatching { AccountId(UUID.fromString(it)) }.getOrNull()
            }
    }
}
