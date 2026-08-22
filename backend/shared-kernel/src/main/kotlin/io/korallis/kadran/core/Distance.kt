package io.korallis.kadran.core

/**
 * Distance en mètres — l'unité entière la plus fine que les sources fournissent, pour la même
 * raison qu'un `Money` compte en centimes : éviter tout arrondi flottant sur une grandeur qui
 * entre directement dans un coût au kilomètre (`C1`, `C3`).
 *
 * @throws IllegalArgumentException si [meters] est négatif — aucune sortie ne parcourt une
 *   distance négative ; une valeur négative signale une erreur de saisie ou de conversion en
 *   amont, pas une donnée à modéliser.
 */
data class Distance(
    val meters: Long,
) {
    init {
        require(meters >= 0) { "une distance ne peut pas etre negative : $meters" }
    }

    operator fun plus(other: Distance): Distance = Distance(meters + other.meters)

    companion object {
        fun zero(): Distance = Distance(0)
    }
}
