package io.korallis.kadran.platform.tenancy

import java.util.UUID

/**
 * Identifiant d'un tenant — l'entité juridique exploitante (spec §9.3).
 *
 * Typé plutôt que `UUID` nu : le prédicat d'isolation est une propriété du code depuis
 * l'abandon du RLS (ADR-001, spec §9.1), et un `UUID` anonyme se confond avec n'importe
 * quel autre identifiant du modèle. Le compilateur doit refuser ce qu'aucune policy
 * PostgreSQL ne rattrapera.
 *
 * L'UUID est opaque : il ne porte aucune PII et peut donc figurer en clair dans les logs
 * et le MDC (spec §8.1).
 */
@JvmInline
value class TenantId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()

    companion object {
        /**
         * @throws IllegalArgumentException si [raw] n'est pas un UUID — un identifiant de
         *   tenant illisible n'a pas de repli raisonnable, il doit interrompre le traitement.
         */
        fun of(raw: String): TenantId = TenantId(UUID.fromString(raw))

        /** Retourne `null` plutôt que de lever, pour les sources non fiables (en-tête, JWT). */
        fun parse(raw: String?): TenantId? =
            raw?.takeIf { it.isNotBlank() }?.let {
                runCatching { of(it) }.getOrNull()
            }
    }
}
