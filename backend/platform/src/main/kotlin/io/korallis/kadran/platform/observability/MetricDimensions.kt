package io.korallis.kadran.platform.observability

/**
 * Dimensions d'une métrique, filtrées par l'interdiction d'ADR-011 (spec §10.7.2).
 *
 * **Aucune métrique ne porte `tenant_id`, `driver_id` ni aucun identifiant d'agrégat.** Deux
 * motifs, chacun suffisant :
 *
 * - la **cardinalité** — une série temporelle naît de chaque combinaison de libellés ; un
 *   libellé par tenant multiplie la charge du collecteur par le nombre de clients, et c'est
 *   la première cause d'effondrement d'une instance Prometheus ;
 * - la **confidentialité** — les métriques partent vers un système d'exploitation qui n'a ni
 *   le chiffrement de §8.2, ni les restrictions d'accès de §9, ni la purge de §8.3.
 *
 * Ce qui doit être lu par tenant se lit **dans l'application**, jamais dans Grafana. Le refus
 * est une exception au montage, pas un avertissement : une dimension interdite ne se découvre
 * jamais en production, elle s'y installe.
 */
object MetricDimensions {
    private val KEY = Regex("^[a-z][a-z0-9_]*$")

    /** Racines qui désignent une personne ou un compte, quel que soit le suffixe. */
    private val FORBIDDEN_KEYS = setOf("id", "tenant", "driver", "user", "account", "uuid")

    /**
     * Construit les dimensions d'une métrique, ou échoue.
     *
     * L'appelant les convertit ensuite en `Tag` Micrometer ; ce module n'en dépend pas.
     */
    fun of(vararg dimensions: Pair<String, String>): Map<String, String> {
        dimensions.forEach { (key, _) -> requirePublishableKey(key) }
        return dimensions.toMap()
    }

    /** Vrai si [key] désigne un agrégat ou une personne, et ne peut donc pas être publiée. */
    fun isForbidden(key: String): Boolean = key in FORBIDDEN_KEYS || key.endsWith("_id")

    private fun requirePublishableKey(key: String) {
        require(KEY.matches(key)) {
            "dimension de metrique hors gabarit (minuscules, chiffres, underscore) : '$key'"
        }
        require(!isForbidden(key)) {
            "'$key' identifie un agregat ou une personne : interdit en dimension de metrique (ADR-011). " +
                "Ce qui se lit par tenant se lit dans l'application, pas dans Grafana"
        }
    }
}
