package io.korallis.kadran.activity.domain.model

/**
 * Ce qu'une source de données sait dire d'elle-même (spec §4.2, tableau comparatif §3.6).
 *
 * Les capacités sont disjointes d'une plateforme à l'autre : Uber ne fournit aucun temps de
 * connexion, Heetch ne fournit ni temps en ligne ni distance, Bolt ne ventile la TVA que
 * partiellement. Elles doivent être déclaratives et vérifiées avant tout calcul de métrique —
 * voir [PlatformProfile] et [checkCapabilities].
 *
 * Le moteur de métriques doit refuser proprement plutôt que produire un zéro (CLAUDE.md §2.1) :
 * quand une métrique exige une capacité absente du profil de la plateforme concernée, elle
 * renvoie un refus nommé, jamais `0`, jamais `null` silencieux.
 */
enum class SourceCapability {
    /** Chiffre d'affaires brut, TTC, avant prélèvement de la commission de la plateforme. */
    GROSS_REVENUE,

    /** Revenu net, après prélèvement de la commission de la plateforme. */
    NET_REVENUE,

    /** Commission effective prélevée par la plateforme. */
    COMMISSION,

    /** Ventilation de la TVA collectée (base HT, taux, montant de taxe). */
    VAT_BREAKDOWN,

    /** Nombre de courses réalisées. */
    TRIP_COUNT,

    /** Temps passé connecté à la plateforme, disponible pour une course. */
    ONLINE_TIME,

    /** Distance parcourue. */
    DISTANCE,

    /** Moyen d'encaissement d'une course (carte, espèces...). */
    PAYMENT_METHOD,

    /** Pourboire versé par le passager. */
    TIP,

    /** Revenu d'incitation (campagne, quête, garantie) versé par la plateforme. */
    INCENTIVE,

    /** Horodatage individuel de chaque course. */
    PER_TRIP_TIMESTAMP,

    /** Identité de la contrepartie d'une course (passager). */
    COUNTERPARTY_IDENTITY,
}
