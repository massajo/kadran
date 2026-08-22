package io.korallis.kadran.activity.domain.spi

import io.korallis.kadran.activity.domain.model.Outing
import io.korallis.kadran.activity.domain.model.OutingId
import java.time.LocalDate

/**
 * Port piloté du contexte `activity` pour l'agrégat `Outing` (spec §10.2, ADR-005).
 *
 * Comme les ports d'`identity` (KDN-27), aucune méthode ne prend de `TenantId` : l'exploitant
 * est fixé à la construction de l'adaptateur, par le `TenantScopedQuery` qui l'exige lui-même
 * à la sienne (`CLAUDE.md` §2.3, ADR-001).
 */
interface OutingRepository {
    fun findById(id: OutingId): Outing?

    /**
     * Sorties d'une journée d'exploitation donnée — pas une date calendaire (spec §4.3).
     * C'est la lecture qu'attendra `WorkDay` (spec §7.3) pour composer son amplitude.
     */
    fun findByBusinessDay(businessDay: LocalDate): List<Outing>

    fun findAll(): List<Outing>

    fun save(outing: Outing)

    /** @return vrai si une ligne a été supprimée — faux si elle appartient à un autre exploitant. */
    fun deleteById(id: OutingId): Boolean
}
