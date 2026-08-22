package io.korallis.kadran.platform.security

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Réglage du hachage des mots de passe.
 *
 * @property bcryptStrength exposant du coût bcrypt : chaque unité **double** le temps de
 *   calcul. 12 vise une centaine de millisecondes sur du matériel de 2026 — assez pour rendre
 *   une attaque par dictionnaire hors de prix, assez peu pour qu'une connexion reste
 *   instantanée. À relever, jamais à abaisser : les empreintes existantes portent leur propre
 *   coût et restent vérifiables, si bien que monter la valeur ne casse rien.
 *
 *   Il est configurable pour une seule raison : un test d'intégration qui se connecte
 *   plusieurs fois n'a pas à payer le coût de production, et un coût abaissé en dur dans le
 *   code de production serait la pire des façons d'y arriver.
 */
@ConfigurationProperties(prefix = "kadran.security.password")
data class PasswordProperties(
    val bcryptStrength: Int = DEFAULT_BCRYPT_STRENGTH,
) {
    private companion object {
        const val DEFAULT_BCRYPT_STRENGTH = 12
    }
}
