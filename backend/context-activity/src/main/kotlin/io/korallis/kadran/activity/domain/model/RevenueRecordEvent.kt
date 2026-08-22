package io.korallis.kadran.activity.domain.model

import io.korallis.kadran.core.Grain
import io.korallis.kadran.core.PlatformId
import io.korallis.kadran.core.TenantId
import java.time.Instant

/**
 * Ce qui, dans le cycle de vie d'un `RevenueRecord`, doit laisser une trace (périmètre de
 * KDN-35, spec §8.4). Même patron que `IdentityEvent` (KDN-27) : `@Audited` (KDN-22) et la
 * table `audit_event` (KDN-21) n'existent pas encore, donc l'agrégat **rend** l'événement
 * avec son nouvel état plutôt que d'attendre l'aspect qui le consommera.
 *
 * Portée à cet agrégat plutôt que partagée avec `Outing`, construit en parallèle (KDN-34) :
 * un `ActivityEvent` commun aux deux agrégats se décidera à leur rencontre, pas en anticipant
 * une forme que l'autre lane n'a pas encore écrite.
 */
sealed interface RevenueRecordEvent {
    /** L'exploitant concerné — la clé de partition de tout journal d'audit (spec §8.4). */
    val tenantId: TenantId

    /** Horodatage **fourni par l'appelant**, jamais `Instant.now()` pris dans le domaine. */
    val occurredAt: Instant
}

/** Un revenu est enregistré, à ce grain et pour cette plateforme (spec §7.3). */
data class RevenueRecorded(
    override val tenantId: TenantId,
    val revenueRecordId: RevenueRecordId,
    val platform: PlatformId,
    val grain: Grain,
    override val occurredAt: Instant,
) : RevenueRecordEvent

/**
 * Le nouvel état d'un agrégat et l'événement qui le justifie, rendus ensemble (même patron
 * que `Transition<IdentityEvent>` de KDN-27). Rendre l'un sans l'autre laisserait à
 * l'appelant le soin de ne pas les dissocier — ce qu'il finirait par faire, silencieusement.
 */
data class Transition<out T>(
    val state: T,
    val event: RevenueRecordEvent,
)
