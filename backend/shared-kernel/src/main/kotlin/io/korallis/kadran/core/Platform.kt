package io.korallis.kadran.core

/**
 * Plateforme source d'une donnée d'activité (spec §7.2) — partagé par `RevenueRecord`
 * (KDN-35), `PlatformProfile` (KDN-36) et le futur moteur de métriques.
 */
enum class PlatformId {
    UBER,
    BOLT,
    HEETCH,
    FREENOW,
    ALLOCAB,
    DIRECT,
    OTHER,
}

/**
 * Granularité temporelle la plus fine à laquelle une source expose ses données (spec §4, §7.2).
 *
 * Une métrique déclare le grain minimal qu'elle exige. Si les données disponibles sont d'un
 * grain plus grossier, la métrique n'est pas calculée — elle n'est jamais estimée par
 * répartition (ADR-004).
 *
 * L'ordre de déclaration va du plus fin au plus grossier et suffit à comparer deux grains via
 * [Enum.ordinal] : `TRIP` (Uber relevé hebdomadaire et factures) · `OUTING` (Driversnote — une
 * sortie) · `DAY` · `PERIOD` (export flotte Bolt, récapitulatif fiscal Uber).
 */
enum class Grain {
    TRIP,
    OUTING,
    DAY,
    PERIOD,
}
