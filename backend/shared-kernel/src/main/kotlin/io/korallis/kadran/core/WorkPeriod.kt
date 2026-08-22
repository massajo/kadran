package io.korallis.kadran.core

import java.time.Duration
import java.time.Instant

/**
 * Fenêtre temporelle bornée — la fenêtre d'une sortie, la couverture d'un `RevenueRecord`, une
 * session en ligne. `to` inclus, `from` inclus : un instant unique se modélise par `from == to`
 * (durée nulle), pas par un type séparé.
 *
 * @throws IllegalArgumentException si [to] précède [from] — une fenêtre qui finit avant de
 *   commencer n'a pas de sens et ne doit jamais atteindre le domaine.
 */
data class WorkPeriod(
    val from: Instant,
    val to: Instant,
) {
    init {
        require(!to.isBefore(from)) { "la fenetre finit ($to) avant de commencer ($from)" }
    }

    fun duration(): Duration = Duration.between(from, to)

    /** Vrai si cette fenêtre chevauche `to` avec [other] démarrant après [from] elle-même. */
    fun overlaps(other: WorkPeriod): Boolean = from.isBefore(other.to) && other.from.isBefore(to)
}
