package io.korallis.kadran.platform.observability

import java.util.UUID

/**
 * Identifiant de corrélation d'une requête, propagé en MDC et repris dans chaque événement
 * d'audit : une requête utilisateur se reconstitue de bout en bout (spec §8.4).
 *
 * La valeur entrante est **validée avant d'être acceptée**, jamais reprise telle quelle :
 * elle vient du client et finira dans les logs, puis chez un agrégateur externe. Un retour
 * chariot dans un en-tête suffirait à y forger une fausse ligne de log, et une chaîne sans
 * borne à faire grossir chaque ligne indéfiniment.
 */
@JvmInline
value class CorrelationId private constructor(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        /** En-tête accepté en entrée et systématiquement renvoyé en sortie. */
        const val HEADER_NAME: String = "X-Correlation-Id"

        /**
         * Alphanumérique, `.`, `_`, `-`, 8 à 64 caractères. Assez large pour accueillir un
         * UUID, un identifiant de trace W3C ou une référence de passerelle ; assez étroit
         * pour exclure séparateurs de ligne, espaces et échappements.
         */
        private val ACCEPTED = Regex("^[A-Za-z0-9._-]{8,64}$")

        fun generate(): CorrelationId = CorrelationId(UUID.randomUUID().toString())

        /** Retourne `null` si [raw] est absent ou non conforme — l'appelant en génère un. */
        fun parse(raw: String?): CorrelationId? = raw?.takeIf(ACCEPTED::matches)?.let(::CorrelationId)

        /** Reprend l'identifiant du client s'il est exploitable, en forge un sinon. */
        fun fromIncoming(raw: String?): CorrelationId = parse(raw) ?: generate()
    }
}
