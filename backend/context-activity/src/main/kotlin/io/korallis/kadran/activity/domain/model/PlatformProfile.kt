package io.korallis.kadran.activity.domain.model

import io.korallis.kadran.core.Grain
import io.korallis.kadran.core.PlatformId

/**
 * Ce qu'une plateforme source sait dire d'elle-même : le grain le plus fin auquel elle expose
 * ses données et l'ensemble des capacités qu'elle fournit réellement (spec §4.2).
 *
 * Ce profil est un fait structurel sur les exports d'une plateforme — ce que son relevé, son
 * récapitulatif fiscal ou son CSV contiennent réellement — et non une donnée métier
 * configurable par l'exploitant. Il ne varie ni par tenant ni dans le temps, contrairement à un
 * taux fiscal (CLAUDE.md §9, qui range explicitement les taux comme « donnée en base, jamais du
 * code »). Il vit donc en code : un changement de profil de plateforme est un changement de
 * comportement de l'intégration elle-même, qui appelle une revue et un déploiement, pas une
 * bascule de configuration à chaud. Aucun changeset Liquibase n'est donc attendu pour KDN-36.
 */
data class PlatformProfile(
    val platform: PlatformId,
    val finestGrain: Grain,
    val capabilities: Set<SourceCapability>,
) {
    companion object {
        /**
         * Profil Uber, capacité par capacité (spec §3.7, tableau comparatif §3.6) :
         *
         * - [SourceCapability.GROSS_REVENUE] — `MontantBrut` des factures CSV (§3.3), le TTC
         *   payé par le passager.
         * - [SourceCapability.NET_REVENUE] — « Vos revenus », net de frais, sur le relevé
         *   hebdomadaire (§3.1).
         * - [SourceCapability.COMMISSION] — « Frais de service Uber » du récapitulatif fiscal
         *   mensuel, croisé avec le brut des factures (§3.2 ; §3.7 : « `R3`/`R4` commission
         *   effective... récapitulatif fiscal + factures »). Disponible au mois seulement.
         * - [SourceCapability.VAT_BREAKDOWN] — ventilation HT/TVA/TTC du récapitulatif fiscal
         *   (§3.2) et de chaque facture (`MontantNet`/`MontantTaxe`/`MontantBrut`, §3.3).
         * - [SourceCapability.TRIP_COUNT] — « Nombre de courses » du récapitulatif fiscal (§3.2),
         *   et une ligne de facture par course (§3.3).
         * - [SourceCapability.DISTANCE] — « Kilométrage total » du récapitulatif fiscal (§3.2).
         *   Disponible au mois seulement, jamais par course (pas de colonne distance sur le
         *   relevé ni sur les factures, §3.1, §3.3) — c'est précisément ce qui rend `M4`/`M5`
         *   indisponibles (ADR-004, §3.7).
         * - [SourceCapability.TIP] — ligne « Pourboire » isolée du détail du relevé
         *   hebdomadaire (§3.1).
         * - [SourceCapability.INCENTIVE] — revenus d'incitation, disponibles au mois via le
         *   relevé hebdomadaire et les factures (§3.7 : « revenus, pourboires, incitations,
         *   suppléments »).
         * - [SourceCapability.PER_TRIP_TIMESTAMP] — horodatage de chaque événement du relevé
         *   hebdomadaire (colonne `Événement`, §3.1). Les factures, elles, ne portent qu'une
         *   date sans heure (§3.3) : c'est le relevé qui porte cette capacité pour Uber.
         *
         * Absentes, explicitement :
         *
         * - [SourceCapability.ONLINE_TIME] — « Uber ne fournit aucun temps de connexion »
         *   (§3.7). Confirmé par le tableau comparatif §3.6 (`Temps en ligne | ❌`).
         * - [SourceCapability.PAYMENT_METHOD] — absent des trois documents Uber (§3.1–§3.3) ;
         *   le tableau §3.6 le confirme (`Moyen de paiement | ❌`), à la différence de Bolt et
         *   Heetch qui l'exposent tous deux.
         * - [SourceCapability.COUNTERPARTY_IDENTITY] — les factures portent bien
         *   `NomUtilisateur`/`AdresseUtilisateur`, la PII du passager (§3.3), mais cette donnée
         *   n'est jamais persistée : seul un pseudonyme HMAC stable en est tiré, sans que
         *   l'identité elle-même ne survive à l'import (§7.7, §8.1). Le système n'a donc pas la
         *   capacité d'exposer une métrique fondée sur l'identité de la contrepartie, quelle
         *   que soit la richesse du fichier source.
         *
         * Grain le plus fin : [Grain.TRIP] — la course, portée par le relevé hebdomadaire et
         * les factures (§3.6 : « Grain le plus fin | course »).
         */
        val UBER =
            PlatformProfile(
                platform = PlatformId.UBER,
                finestGrain = Grain.TRIP,
                capabilities =
                    setOf(
                        SourceCapability.GROSS_REVENUE,
                        SourceCapability.NET_REVENUE,
                        SourceCapability.COMMISSION,
                        SourceCapability.VAT_BREAKDOWN,
                        SourceCapability.TRIP_COUNT,
                        SourceCapability.DISTANCE,
                        SourceCapability.TIP,
                        SourceCapability.INCENTIVE,
                        SourceCapability.PER_TRIP_TIMESTAMP,
                    ),
            )
    }
}
