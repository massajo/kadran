package io.korallis.kadran.platform.observability

/**
 * Nom de métrique métier conforme à la spec §10.7.2 : `kadran.<contexte borné>.<sujet>`.
 *
 * Le nom est **celui donné à Micrometer**, en notation pointée ; c'est le registre Prometheus
 * qui le rend en `snake_case` et lui ajoute son suffixe d'exposition — `_total` pour un
 * compteur, `_seconds` pour un `Timer`, `_bytes` pour un `DistributionSummary` en octets.
 * D'où le refus, ici, d'un dernier segment `total`, `count`, `sum`, `seconds` ou `bytes` :
 * écrit à la main, il ressortirait doublé (`kadran_..._total_total`) et casserait les règles
 * d'agrégation en aval.
 *
 * Les millisecondes sont refusées partout : Prometheus travaille en unités de base, et une
 * métrique nommée en millisecondes se compare silencieusement de travers à une métrique en
 * secondes. C'est la même exigence qu'un `Money` en centimes — l'unité fait partie du type,
 * pas du commentaire.
 *
 * Ce type ne dit rien des dimensions : voir [MetricDimensions], qui porte l'interdiction de
 * `tenant_id` posée par ADR-011.
 */
@JvmInline
value class MetricName private constructor(
    val value: String,
) {
    override fun toString(): String = value

    /**
     * Nom de base tel que le registre Prometheus l'expose, **sans** le suffixe d'exposition
     * que l'instrument lui ajoutera. Sert à écrire des assertions et des tableaux de bord
     * sans recopier la règle de conversion à la main.
     */
    val exposedBaseName: String get() = value.replace('.', '_')

    companion object {
        /** Préfixe commun à toute métrique produite par Kadran (spec §10.7.2). */
        const val NAMESPACE: String = "kadran"

        private val SEGMENT = Regex("^[a-z][a-z0-9]*$")

        /** Suffixes que le registre pose lui-même à l'exposition. */
        private val EXPOSITION_SUFFIXES = setOf("total", "count", "sum", "max", "seconds", "bytes")

        /** Toute graphie de la milliseconde : jamais dans un nom de métrique. */
        private val MILLISECOND_WORDS = setOf("ms", "milli", "millis", "millisecond", "milliseconds")

        /**
         * @param boundedContext contexte borné au sens de §10.1 — `ingestion`, `activity`…
         * @param subject sujet mesuré, en un ou plusieurs segments : `parse`, `failures`.
         */
        fun of(
            boundedContext: String,
            vararg subject: String,
        ): MetricName {
            require(subject.isNotEmpty()) {
                "un nom de metrique nomme un sujet, pas seulement un contexte borne"
            }
            val segments = listOf(boundedContext) + subject
            segments.forEach(::requireUsableSegment)
            require(segments.last() !in EXPOSITION_SUFFIXES) {
                "'${segments.last()}' est pose par le registre a l'exposition : ne pas l'ecrire dans le nom"
            }
            return MetricName((listOf(NAMESPACE) + segments).joinToString("."))
        }

        private fun requireUsableSegment(segment: String) {
            require(SEGMENT.matches(segment)) {
                "segment de nom de metrique hors gabarit (minuscules et chiffres) : '$segment'"
            }
            require(segment !in MILLISECOND_WORDS) {
                "unite de base exigee : '$segment' nomme des millisecondes (spec §10.7.2)"
            }
        }
    }
}
