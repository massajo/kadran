package io.korallis.kadran.activity.domain.model

import io.korallis.kadran.core.PlatformId

/**
 * Résultat de la vérification des capacités qu'une métrique exige contre un [PlatformProfile].
 *
 * Cette vérification s'exécute avant tout calcul — c'est elle qui permet au moteur de métriques
 * de refuser proprement plutôt que de produire un zéro (spec §4.2, CLAUDE.md §2.1) : quand une
 * capacité requise manque, le refus la nomme précisément, pour qu'un message tel que
 * « non disponible pour Uber : cette plateforme ne fournit pas de temps en ligne » puisse être
 * construit sans deviner ce qui a échoué.
 */
sealed interface CapabilityCheck {
    /** Toutes les capacités requises sont couvertes par le profil interrogé : le calcul peut avoir lieu. */
    data object Allowed : CapabilityCheck

    /**
     * Au moins une capacité requise manque au profil interrogé : le calcul doit être refusé.
     *
     * @property platform la plateforme dont le profil a été interrogé.
     * @property missing les capacités requises absentes du profil, jamais vide.
     */
    data class Refused(
        val platform: PlatformId,
        val missing: Set<SourceCapability>,
    ) : CapabilityCheck {
        init {
            require(missing.isNotEmpty()) { "un refus doit nommer au moins une capacite manquante" }
        }
    }
}

/**
 * Vérifie que ce profil couvre [required] avant tout calcul de métrique.
 *
 * @return [CapabilityCheck.Allowed] si toutes les capacités de [required] sont présentes dans
 *   [PlatformProfile.capabilities], [CapabilityCheck.Refused] nommant précisément celles qui
 *   manquent sinon.
 */
fun PlatformProfile.checkCapabilities(required: Set<SourceCapability>): CapabilityCheck {
    val missing = required - capabilities
    return if (missing.isEmpty()) {
        CapabilityCheck.Allowed
    } else {
        CapabilityCheck.Refused(platform = platform, missing = missing)
    }
}
