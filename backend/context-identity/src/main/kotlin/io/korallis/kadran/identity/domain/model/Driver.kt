package io.korallis.kadran.identity.domain.model

import io.korallis.kadran.core.DriverId
import io.korallis.kadran.core.TenantId

/**
 * Le chauffeur **tel qu'un exploitant le connaît** (spec §9.3).
 *
 * ### Un chauffeur par exploitant, et la personne physique par-dessus
 *
 * La spec dit qu'un `Driver` « peut appartenir à plusieurs `Tenant` dans le temps ». Une
 * lecture naïve en ferait une ligne partagée entre exploitants — impossible ici : la table
 * porte `tenant_id` en tête de sa clé primaire (`CLAUDE.md` §2.3), et l'ADR-001 interdit
 * toute lecture qui traverserait cette frontière. Une ligne partagée serait donc une ligne
 * que personne ne peut lire sans casser l'isolation.
 *
 * Le modèle retenu sépare les deux idées :
 *
 * - **`driver`** est le dossier que *cet* exploitant tient sur *ce* chauffeur. Il ne quitte
 *   jamais son exploitant.
 * - **`Membership.accountId`** porte la personne physique, d'un exploitant à l'autre. Le même
 *   compte peut avoir une appartenance close chez A et une appartenance ouverte chez B ; les
 *   deux lignes subsistent et sont datées, ce qu'exige le critère d'acceptation de l'issue.
 *
 * Le passage d'un chauffeur d'une flotte à une autre est ainsi un fait *daté*, et non un
 * déplacement de ligne qui réécrirait le passé de l'exploitant quitté.
 */
data class Driver(
    val id: DriverId,
    val tenantId: TenantId,
    val displayName: DriverName,
) {
    /** Corrige une saisie. Le renommage n'est pas un événement du cycle de vie de KDN-27. */
    fun renameTo(newName: DriverName): Driver = copy(displayName = newName)

    companion object {
        /** Enregistre un chauffeur dans les livres de [tenantId]. */
        fun register(
            id: DriverId,
            tenantId: TenantId,
            displayName: DriverName,
        ): Driver = Driver(id, tenantId, displayName)
    }
}
