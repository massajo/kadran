package io.korallis.kadran.activity.domain.model

import io.korallis.kadran.core.TenantId
import java.time.Instant
import java.time.LocalDate

/**
 * Ce qui, dans le cycle de vie d'une sortie, doit laisser une trace (spec §7.3, `CLAUDE.md`
 * §2.5).
 *
 * ### Même patron que `IdentityEvent` (KDN-27)
 *
 * `domain/model` ne peut dépendre ni de `domain/api` ni de `domain/spi` (règle ArchUnit de
 * KDN-16) : un agrégat qui rend l'événement justifiant son nouvel état ne peut donc pas le
 * typer ailleurs qu'ici. `@Audited` (KDN-22) et la table `audit_event` (KDN-21) n'existent pas
 * encore ; l'agrégat **rend** l'événement avec son nouvel état plutôt que d'attendre — le cas
 * d'usage qui l'appellera n'aura plus qu'à le remettre à l'aspect.
 *
 * ### Scopé à `Outing`, pas à l'ensemble du contexte `activity`
 *
 * La spec §7.3 énumère plusieurs événements du contexte (`RevenueRecorded`, `OutingRecorded`,
 * `WorkDayClosed`…), qui pourraient à terme partager une seule hiérarchie `ActivityEvent`.
 * Mais `RevenueRecord` (KDN-35) est construit en parallèle, sur une autre branche, dans le
 * même contexte borné : une hiérarchie commune supposerait de coordonner deux lanes sur le
 * même fichier, ce que ni l'une ni l'autre issue ne demande. `OutingEvent` reste donc scopé à
 * ce que `Outing` émet ; une éventuelle réunion sous un type commun est une intégration
 * ultérieure, pas une décision à trancher ici.
 */
sealed interface OutingEvent {
    /** L'exploitant concerné — la clé de partition de tout journal d'audit (spec §8.4). */
    val tenantId: TenantId

    /** Horodatage **fourni par l'appelant**, jamais `Instant.now()` pris dans le domaine. */
    val occurredAt: Instant
}

/** Une sortie est enregistrée, avec la journée d'exploitation qui en résulte (spec §4.3). */
data class OutingRecorded(
    override val tenantId: TenantId,
    val outingId: OutingId,
    val businessDay: LocalDate,
    override val occurredAt: Instant,
) : OutingEvent
