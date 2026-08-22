package io.korallis.kadran.core

import java.time.Duration

/**
 * Union d'un ensemble de [WorkPeriod] — la structure qui empêche de compter deux fois le même
 * instant. Être connecté à deux plateformes en même temps ne crée pas deux heures dans une
 * heure : c'est la « source de bug n°1 » de la spec (§7.3), et la seule raison d'être de ce
 * type. `WorkDay.connectedTime()` (§7.3) s'appuiera dessus plutôt que de sommer des durées.
 *
 * [periods] est toujours trié par [WorkPeriod.from] croissant et **fusionné** : deux éléments
 * consécutifs de la liste ne se chevauchent ni ne se touchent jamais. C'est l'invariant que
 * toute méthode de cette classe construit ou préserve — voir [of].
 *
 * ### Bornes : inclusives, et une nuance avec [WorkPeriod.overlaps]
 *
 * [WorkPeriod] documente `from` et `to` comme inclus des deux côtés. [WorkPeriod.overlaps]
 * traite volontairement le simple contact (`a.to == b.from`) comme un **non-chevauchement** :
 * deux sessions consécutives — la seconde démarrant pile quand la première finit — ne doivent
 * pas être signalées comme simultanées, sans quoi toute détection de conflit se déclencherait à
 * tort sur un enchaînement parfaitement normal.
 *
 * La fusion pratiquée ici répond à une question différente : « quels instants sont couverts ? »,
 * pas « y a-t-il chevauchement ? ». Deux périodes qui se touchent exactement à la borne couvrent
 * ensemble un intervalle continu — refuser de les fusionner créerait un trou d'une durée nulle
 * (une nanoseconde, en pratique) au milieu d'une couverture ininterrompue, et c'est très
 * exactement ce que l'énoncé KDN-38 interdit. La règle de fusion est donc volontairement plus
 * permissive que [WorkPeriod.overlaps] : elle fusionne dès que `next.from <= current.to`, là où
 * `overlaps` exige une inégalité stricte. Les deux comportements sont corrects, chacun pour sa
 * propre question ; ce n'est pas une incohérence entre les deux types, c'est le contact qui n'a
 * qu'une seule réponse possible selon ce qu'on lui demande.
 */
class IntervalUnion private constructor(
    val periods: List<WorkPeriod>,
) {
    /** Durée totale couverte — jamais gonflée par un recouvrement, par construction de [periods]. */
    fun totalDuration(): Duration = periods.fold(Duration.ZERO) { acc, period -> acc + period.duration() }

    /** Union de cette couverture avec une autre. Unir avec [EMPTY] ne change rien : élément neutre. */
    fun union(other: IntervalUnion): IntervalUnion = of(periods + other.periods)

    /**
     * Instants couverts par les deux unions à la fois — par exemple connecté à Uber *et* Bolt en
     * même temps. Algorithme classique de fusion à deux pointeurs sur deux listes déjà triées et
     * disjointes ; le résultat repasse par [of] pour re-garantir l'invariant de la classe plutôt
     * que de supposer qu'il tient déjà.
     */
    fun intersect(other: IntervalUnion): IntervalUnion {
        val result = mutableListOf<WorkPeriod>()
        var i = 0
        var j = 0
        val a = periods
        val b = other.periods
        while (i < a.size && j < b.size) {
            val pa = a[i]
            val pb = b[j]
            val from = maxOf(pa.from, pb.from)
            val to = minOf(pa.to, pb.to)
            if (!to.isBefore(from)) {
                result += WorkPeriod(from, to)
            }
            if (pa.to.isBefore(pb.to)) i++ else j++
        }
        return of(result)
    }

    /** Raccourci pour intersecter avec une seule fenêtre plutôt qu'avec une autre union. */
    fun intersect(window: WorkPeriod): IntervalUnion = intersect(of(listOf(window)))

    /**
     * Complément dans [window] : les instants de [window] non couverts par cette union.
     *
     * Cas dégénéré : si [window] est de durée nulle (`from == to`), le résultat n'a de sens
     * qu'en tout-ou-rien — soit cet instant unique est couvert (complément vide), soit il ne
     * l'est pas (complément = [window] lui-même, degenerate). L'algorithme général ci-dessous ne
     * signale un trou que sur une inégalité stricte (`from.isAfter(cursor)`) : il ne peut donc
     * pas distinguer, pour une fenêtre déjà nulle, « couverte » de « non couverte » — les deux
     * cas produiraient la même absence de trou strict. Ce cas est donc traité à part.
     */
    fun complement(window: WorkPeriod): IntervalUnion {
        if (window.duration().isZero) {
            val covered = periods.any { !it.from.isAfter(window.from) && !it.to.isBefore(window.from) }
            return of(if (covered) emptyList() else listOf(window))
        }

        val gaps = mutableListOf<WorkPeriod>()
        var cursor = window.from
        for (period in periods) {
            val from = maxOf(period.from, window.from)
            val to = minOf(period.to, window.to)
            if (to.isBefore(from)) continue // periode hors fenetre, aucun effet sur le complement
            if (from.isAfter(cursor)) gaps += WorkPeriod(cursor, from)
            if (to.isAfter(cursor)) cursor = to
        }
        if (window.to.isAfter(cursor)) gaps += WorkPeriod(cursor, window.to)
        return of(gaps)
    }

    override fun equals(other: Any?): Boolean = other is IntervalUnion && periods == other.periods

    override fun hashCode(): Int = periods.hashCode()

    override fun toString(): String = "IntervalUnion($periods)"

    companion object {
        /** L'union vide — élément neutre de [union], résultat de [of] sur une collection vide. */
        val EMPTY = IntervalUnion(emptyList())

        /**
         * Construit l'union de [periods] : trie par [WorkPeriod.from] (puis [WorkPeriod.to] à
         * égalité), puis fusionne tout couple consécutif qui se chevauche ou se touche
         * (`next.from <= current.to`). Voir la KDoc de classe pour la justification de ce `<=`
         * plutôt qu'un `<` strict. Idempotent et commutatif : l'ordre d'entrée et la présence de
         * doublons ou de périodes déjà unies ne changent pas le résultat.
         */
        fun of(periods: Collection<WorkPeriod>): IntervalUnion {
            if (periods.isEmpty()) return EMPTY

            val sorted = periods.sortedWith(compareBy({ it.from }, { it.to }))
            val merged = mutableListOf<WorkPeriod>()
            var current = sorted.first()
            for (next in sorted.drop(1)) {
                current =
                    if (!next.from.isAfter(current.to)) {
                        WorkPeriod(current.from, maxOf(current.to, next.to))
                    } else {
                        merged += current
                        next
                    }
            }
            merged += current
            return IntervalUnion(merged)
        }

        /** Variante pratique de [of] pour un appel littéral, ex. `IntervalUnion.of(a, b, c)`. */
        fun of(vararg periods: WorkPeriod): IntervalUnion = of(periods.toList())
    }
}
