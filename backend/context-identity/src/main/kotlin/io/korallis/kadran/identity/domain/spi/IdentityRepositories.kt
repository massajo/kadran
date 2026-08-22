package io.korallis.kadran.identity.domain.spi

import io.korallis.kadran.core.DriverId
import io.korallis.kadran.identity.domain.model.AccountId
import io.korallis.kadran.identity.domain.model.Driver
import io.korallis.kadran.identity.domain.model.Membership
import io.korallis.kadran.identity.domain.model.MembershipId
import io.korallis.kadran.identity.domain.model.Tenant
import io.korallis.kadran.identity.domain.model.Vehicle
import io.korallis.kadran.identity.domain.model.VehicleId

/**
 * Ports pilotés du contexte `identity` — ce que le domaine **exige** d'un fournisseur de
 * persistance (spec §10.2, ADR-005).
 *
 * ### Aucune méthode ne prend de `TenantId`
 *
 * C'est délibéré et c'est le cœur du dispositif de l'ADR-001. L'exploitant n'est pas un
 * paramètre — il est fixé à la construction de l'adaptateur, par le `TenantScopedQuery` qui
 * l'exige lui-même à la sienne. Un port qui accepterait un `TenantId` en argument rendrait
 * l'oubli possible, et rien en base ne le rattraperait.
 *
 * La conséquence se lit dans les signatures : `findAll()` veut dire « tous ceux de
 * l'exploitant courant », `findById` ne peut pas atteindre la ligne d'un autre. Il n'existe
 * ici aucune opération inter-exploitants, et ce n'est pas un manque : la seule qu'on pourrait
 * vouloir — l'historique d'une personne d'une flotte à l'autre — serait une capacité
 * d'administration hors du périmètre v1, à instruire par un ADR et non par une méthode de
 * plus sur ce port.
 */
interface TenantRepository {
    /**
     * L'exploitant courant, ou `null` si la requête est scopée sur un identifiant inconnu.
     *
     * Il n'y a pas de `findById` : la requête étant scopée, « par identifiant » et « le
     * courant » désignent la même et unique ligne.
     */
    fun findCurrent(): Tenant?

    /**
     * Insère ou met à jour l'exploitant.
     *
     * À la création, l'adaptateur doit être construit sur un `TenantScopedQuery` ouvert avec
     * l'identifiant **du nouvel exploitant** : c'est ce qui fait poser le `tenant_id` par la
     * requête plutôt que par l'appelant.
     */
    fun save(tenant: Tenant)
}

/** Chauffeurs de l'exploitant courant. */
interface DriverRepository {
    fun findById(id: DriverId): Driver?

    fun findAll(): List<Driver>

    fun save(driver: Driver)

    /** @return vrai si une ligne a été supprimée — faux si elle appartient à un autre exploitant. */
    fun deleteById(id: DriverId): Boolean
}

/** Appartenances de l'exploitant courant. */
interface MembershipRepository {
    fun findById(id: MembershipId): Membership?

    /** Toutes les appartenances du chauffeur, closes comprises, de la plus récente à la plus ancienne. */
    fun findByDriver(driverId: DriverId): List<Membership>

    /** Appartenances rattachées à ce compte — la lecture que le port d'authentification de KDN-18 attend. */
    fun findByAccount(accountId: AccountId): List<Membership>

    /** Les seules appartenances en vigueur : `valid_until IS NULL`. */
    fun findOpen(): List<Membership>

    fun save(membership: Membership)
}

/** Véhicules de l'exploitant courant. */
interface VehicleRepository {
    fun findById(id: VehicleId): Vehicle?

    fun findAll(): List<Vehicle>

    fun save(vehicle: Vehicle)

    /** @return vrai si une ligne a été supprimée. */
    fun deleteById(id: VehicleId): Boolean
}
