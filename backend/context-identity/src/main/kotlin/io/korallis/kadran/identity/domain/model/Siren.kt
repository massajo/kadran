package io.korallis.kadran.identity.domain.model

/**
 * SIREN — les neuf chiffres qui identifient une entreprise française (spec §9.4, étape 1).
 *
 * La spec demande explicitement la « validation de clé » : les neuf chiffres portent une clé
 * de Luhn, et un SIREN saisi de travers se détecte donc **sans appeler personne**. C'est ce
 * qui permet à l'étape 1 de l'onboarding d'échouer tout de suite plutôt qu'au retour de l'API
 * Recherche d'entreprises (KDN-29), et surtout de ne jamais persister un identifiant faux.
 *
 * Limite connue et assumée : quelques SIREN historiques ne vérifient pas la clé de Luhn — le
 * plus cité est celui de La Poste. On ne les liste pas ici : une exception codée en dur est
 * une donnée métier déguisée en code, et aucune ne concerne le périmètre du produit. Le jour
 * où le cas se présente, il relève d'une table de dérogations, pas d'un `if`.
 */
@JvmInline
value class Siren private constructor(
    val value: String,
) {
    /** Les neuf chiffres, sans séparateur — la forme qui va en base. */
    override fun toString(): String = value

    /** Forme lisible `123 456 789`, telle qu'on l'écrit sur un document. */
    fun formatted(): String = value.chunked(GROUP_SIZE).joinToString(" ")

    companion object {
        private const val LENGTH = 9
        private const val GROUP_SIZE = 3
        private const val DOUBLING_THRESHOLD = 9
        private const val CHECKSUM_MODULUS = 10

        /**
         * Analyse [raw] en ignorant espaces et points, que les documents officiels utilisent
         * librement.
         *
         * @throws IllegalArgumentException si la longueur, la composition ou la clé de
         *   contrôle sont fausses. Un SIREN invalide n'a pas de repli : le refuser à la
         *   frontière est la seule issue qui n'écrit rien de faux en base.
         */
        fun of(raw: String): Siren {
            val digits = raw.filterNot { it.isWhitespace() || it == '.' }
            require(digits.length == LENGTH && digits.all(Char::isDigit)) {
                "un SIREN compte exactement $LENGTH chiffres, recu : $raw"
            }
            require(hasValidChecksum(digits)) { "cle de controle du SIREN invalide : $raw" }
            return Siren(digits)
        }

        /** Retourne `null` au lieu de lever — pour une source non fiable, un formulaire. */
        fun parse(raw: String?): Siren? = raw?.let { runCatching { of(it) }.getOrNull() }

        /**
         * Luhn : en partant de la gauche, un chiffre sur deux — les positions paires — est
         * doublé, et un résultat au-delà de 9 voit ses deux chiffres additionnés (ce que
         * `- 9` fait pour un nombre de deux chiffres dont le premier vaut 1). La somme des
         * neuf valeurs doit être un multiple de 10.
         */
        private fun hasValidChecksum(digits: String): Boolean {
            val total =
                digits.mapIndexed { index, char ->
                    val digit = char.digitToInt()
                    if (index % 2 == 1) doubled(digit) else digit
                }
            return total.sum() % CHECKSUM_MODULUS == 0
        }

        private fun doubled(digit: Int): Int {
            val product = digit * 2
            return if (product > DOUBLING_THRESHOLD) product - DOUBLING_THRESHOLD else product
        }
    }
}
