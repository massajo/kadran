package io.korallis.kadran.identity.domain.model

/**
 * Raison sociale de l'exploitant (spec §9.4, étape 1).
 *
 * Le constructeur est privé et la normalisation passe par [of] : deux exploitants saisis
 * `« Kadran SASU »` et `« Kadran SASU  »` ne doivent pas devenir deux valeurs distinctes en
 * base. Normaliser à la construction, plutôt qu'au moment de comparer, est ce qui garantit
 * qu'aucun chemin n'y échappe.
 */
@JvmInline
value class LegalName private constructor(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH: Int = 200

        /** @throws IllegalArgumentException si la raison sociale est vide ou trop longue. */
        fun of(raw: String): LegalName {
            val normalized = raw.trim().replace(WHITESPACE, " ")
            require(normalized.isNotEmpty()) { "la raison sociale ne peut pas etre vide" }
            require(normalized.length <= MAX_LENGTH) {
                "la raison sociale depasse $MAX_LENGTH caracteres"
            }
            return LegalName(normalized)
        }

        private val WHITESPACE = Regex("\\s+")
    }
}

/**
 * Nom d'affichage d'un chauffeur.
 *
 * **Ce n'est volontairement pas une identité civile.** L'état civil, l'adresse et le numéro
 * de permis sont des données `PII_HIGH` au sens de la spec §8.2, et le chiffrement enveloppe
 * qui doit les protéger n'existe pas encore. Tant qu'il manque, `identity` ne conserve que
 * ce dont l'interface a besoin pour désigner une personne dans une liste — et rien de plus
 * (spec §8.1 : « la donnée absente ne fuit pas »).
 */
@JvmInline
value class DriverName private constructor(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH: Int = 120

        /** @throws IllegalArgumentException si le nom est vide ou trop long. */
        fun of(raw: String): DriverName {
            val normalized = raw.trim().replace(WHITESPACE, " ")
            require(normalized.isNotEmpty()) { "le nom du chauffeur ne peut pas etre vide" }
            require(normalized.length <= MAX_LENGTH) {
                "le nom du chauffeur depasse $MAX_LENGTH caracteres"
            }
            return DriverName(normalized)
        }

        private val WHITESPACE = Regex("\\s+")
    }
}
