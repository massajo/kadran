package io.korallis.kadran.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * La journée d'exploitation n'est pas la journée calendaire (spec §4.3, ADR-006).
 *
 * Une vacation de nuit — l'échantillon Driversnote va du 02/06 au 03/06 — est le cas nominal
 * en VTC, pas l'exception. Découper sur minuit couperait chaque nuit de travail en deux :
 * amplitude tronquée, coût fixe compté deux fois. Toutes les projections journalières (`T1`,
 * `C4`, `M2`, `M8`) doivent donc utiliser cette politique, jamais `LocalDate.from(instant)`.
 *
 * `cutoff` est paramétrable par tenant (§4.3) : un chauffeur de jour peut préférer `00:00`.
 * Le défaut `04:00` est cohérent avec la frontière hebdomadaire d'Uber (lundi 4 h → lundi
 * 4 h), ce qui simplifie le rapprochement entre les deux documents.
 */
data class BusinessDayPolicy(
    val cutoff: LocalTime = DEFAULT_CUTOFF,
) {
    private companion object {
        val DEFAULT_CUTOFF: LocalTime = LocalTime.of(4, 0)
    }
}

/**
 * Journée d'exploitation d'un instant, selon [policy] et [zone].
 *
 * Compare l'heure murale locale au seuil — pas de soustraction de durée. C'est un choix
 * délibéré, pas une préférence de style : `ZonedDateTime.minusHours(n)` retranche `n` heures
 * **réelles** (arithmétique sur l'instant, pas sur l'heure affichée), ce qui déplace la
 * frontière de journée de deux heures durant les deux heures qui suivent le passage à l'heure
 * d'été — vérifié empiriquement (`2026-03-29T04:00` à `06:00` CEST retombaient sur la veille
 * avec cette approche, la plage exacte de la « nuit à 23 heures »). La comparaison directe de
 * l'heure murale au seuil n'a pas ce défaut : elle ne dépend que de ce que l'horloge affiche,
 * jamais de la durée réelle écoulée depuis minuit — correcte aussi bien pour la nuit à 23
 * heures que pour celle à 25 heures.
 */
fun Instant.toBusinessDay(
    policy: BusinessDayPolicy,
    zone: ZoneId,
): LocalDate {
    val zoned = atZone(zone)
    return if (zoned.toLocalTime().isBefore(policy.cutoff)) {
        zoned.toLocalDate().minusDays(1)
    } else {
        zoned.toLocalDate()
    }
}
