package io.korallis.kadran.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Journée d'exploitation — spec §4.3, ADR-006. Sans mock : ce sont deux fonctions pures sur
 * des types de la bibliothèque standard.
 *
 * Le cas du passage à l'heure d'été (`nuit à 23 heures`) n'est pas là par précaution
 * théorique : une première version, qui retranchait le seuil par `ZonedDateTime.minusHours`,
 * lui échouait réellement — vérifié par balayage exhaustif de l'année, 120 minutes fautives,
 * exactement la plage `04:00`–`06:00` CEST du 29 mars 2026. `minusHours` retranche des heures
 * **réelles** (arithmétique sur l'instant), pas des heures **murales** ; la nuit la plus
 * courte de l'année suffit à les faire diverger. La comparaison directe de l'heure murale au
 * seuil n'a pas ce défaut.
 */
class BusinessDayPolicyTest :
    StringSpec({
        val paris = ZoneId.of("Europe/Paris")
        val defaultPolicy = BusinessDayPolicy()

        "an instant before the cutoff belongs to the previous calendar date" {
            // 2026-06-02T03:59:59 heure de Paris
            Instant.parse("2026-06-02T01:59:59Z").toBusinessDay(defaultPolicy, paris) shouldBe
                LocalDate.of(2026, 6, 1)
        }

        "an instant at or after the cutoff belongs to the same calendar date" {
            // 2026-06-02T04:00:00 heure de Paris, pile le seuil
            Instant.parse("2026-06-02T02:00:00Z").toBusinessDay(defaultPolicy, paris) shouldBe
                LocalDate.of(2026, 6, 2)
        }

        "a night shift crossing midnight stays on the day it started" {
            // L'échantillon Driversnote de la spec : une vacation du 02/06 au 03/06.
            // Le début, 20:00 le 2, et la fin, 02:00 le 3, doivent tomber sur la même
            // journée d'exploitation — celle du 2.
            val start = Instant.parse("2026-06-02T18:00:00Z") // 20:00 heure de Paris
            val end = Instant.parse("2026-06-03T00:00:00Z") // 02:00 heure de Paris, le lendemain

            start.toBusinessDay(defaultPolicy, paris) shouldBe LocalDate.of(2026, 6, 2)
            end.toBusinessDay(defaultPolicy, paris) shouldBe LocalDate.of(2026, 6, 2)
        }

        "the 23-hour spring night does not shift the boundary by two hours" {
            // 2026-03-29 : 02:00 CET saute directement à 03:00 CEST (spring forward).
            // C'est le cas qui a fait échouer la première version de cette fonction.
            Instant.parse("2026-03-29T01:59:59Z").toBusinessDay(defaultPolicy, paris) shouldBe
                LocalDate.of(2026, 3, 28) // 03:59:59 CET, avant le seuil
            Instant.parse("2026-03-29T02:00:00Z").toBusinessDay(defaultPolicy, paris) shouldBe
                LocalDate.of(2026, 3, 29) // 04:00:00 CEST, pile le seuil
        }

        "the 25-hour autumn night resolves the repeated local hour consistently" {
            // 2026-10-25 : 03:00 CEST redevient 02:00 CET (fall back). L'heure murale
            // 02:30 existe deux fois ; les deux occurrences doivent tomber sur la même
            // journée d'exploitation, puisque toutes deux précèdent le seuil de 04:00.
            val firstOccurrence = Instant.parse("2026-10-25T00:30:00Z") // 02:30 CEST
            val secondOccurrence = Instant.parse("2026-10-25T01:30:00Z") // 02:30 CET, une heure reelle plus tard

            firstOccurrence.toBusinessDay(defaultPolicy, paris) shouldBe LocalDate.of(2026, 10, 24)
            secondOccurrence.toBusinessDay(defaultPolicy, paris) shouldBe LocalDate.of(2026, 10, 24)

            // Et après le seuil, sans ambiguïté possible (l'heure locale ne se répète plus) :
            Instant.parse("2026-10-25T03:00:00Z").toBusinessDay(defaultPolicy, paris) shouldBe
                LocalDate.of(2026, 10, 25)
        }

        "a custom cutoff of midnight matches the plain calendar date" {
            val midnight = BusinessDayPolicy(cutoff = LocalTime.MIDNIGHT)

            Instant.parse("2026-06-02T21:59:59Z").toBusinessDay(midnight, paris) shouldBe
                LocalDate.of(2026, 6, 2) // 23:59:59 heure de Paris
            Instant.parse("2026-06-02T22:00:01Z").toBusinessDay(midnight, paris) shouldBe
                LocalDate.of(2026, 6, 3) // 00:00:01 heure de Paris, le lendemain
        }
    })
