package io.korallis.kadran.identity.domain.model

import io.korallis.kadran.core.DriverId
import io.korallis.kadran.core.TenantId
import java.time.Instant

/**
 * Ce qui, dans le cycle de vie d'un exploitant, doit laisser une trace : création,
 * invitation, changement de rôle, révocation, clôture (périmètre de KDN-27, spec §8.4).
 *
 * ### Pourquoi ces événements vivent dans `domain/model`
 *
 * `domain/model` ne peut dépendre ni de `domain/api` ni de `domain/spi` — la règle ArchUnit
 * de KDN-16 le vérifie. Un agrégat qui rend l'événement justifiant son nouvel état ne peut
 * donc pas le typer ailleurs qu'ici. Ce n'est pas un pis-aller : l'événement *est* le modèle.
 * Ce qui relèvera de `domain/api`, c'est sa publication vers les autres contextes.
 *
 * ### Pourquoi le domaine les produit, alors que `@Audited` n'existe pas
 *
 * `CLAUDE.md` §2.5 exige un événement d'audit pour toute mutation, via `@Audited` en couche
 * `application` — annotation livrée par KDN-22, sur la table `audit_event` de KDN-21. Aucune
 * des deux n'existe encore. Plutôt que d'attendre, les agrégats **rendent** l'événement avec
 * leur nouvel état : le cas d'usage qui les appellera n'aura plus qu'à le remettre à
 * l'aspect. Un état muté sans événement à produire serait, lui, irrattrapable après coup.
 */
sealed interface IdentityEvent {
    /** L'exploitant concerné — la clé de partition de tout journal d'audit (spec §8.4). */
    val tenantId: TenantId

    /** Horodatage **fourni par l'appelant**, jamais `Instant.now()` pris dans le domaine. */
    val occurredAt: Instant
}

/** L'exploitant existe : l'onboarding de la spec §9.4 peut commencer. */
data class TenantRegistered(
    override val tenantId: TenantId,
    val siren: Siren,
    override val occurredAt: Instant,
) : IdentityEvent

/**
 * L'exploitant cesse son activité.
 *
 * Clôture et non suppression : effacer les lignes emporterait l'historique que la spec §8.4
 * conserve cinq ans. L'effacement des données personnelles, lui, est une opération distincte
 * du contexte `privacy`, qui ne consiste pas à supprimer l'exploitant.
 */
data class TenantClosed(
    override val tenantId: TenantId,
    override val occurredAt: Instant,
) : IdentityEvent

/** Un chauffeur est rattaché à l'exploitant, avec un rôle et une date de prise d'effet. */
data class MemberInvited(
    override val tenantId: TenantId,
    val membershipId: MembershipId,
    val driverId: DriverId,
    val role: MembershipRole,
    override val occurredAt: Instant,
) : IdentityEvent

/** Le rôle change. L'ancien rôle fait partie de l'événement : sans lui, il n'informe rien. */
data class MemberRoleChanged(
    override val tenantId: TenantId,
    val membershipId: MembershipId,
    val previousRole: MembershipRole,
    val newRole: MembershipRole,
    override val occurredAt: Instant,
) : IdentityEvent

/** L'appartenance est fermée à cette date. La ligne demeure — voir `MembershipPeriod`. */
data class MembershipRevoked(
    override val tenantId: TenantId,
    val membershipId: MembershipId,
    override val occurredAt: Instant,
) : IdentityEvent

/**
 * Le nouvel état d'un agrégat et l'événement qui le justifie, rendus ensemble.
 *
 * Rendre l'un sans l'autre laisserait à l'appelant le soin de ne pas les dissocier — ce
 * qu'il finirait par faire, et l'oubli serait silencieux.
 */
data class Transition<out T>(
    val state: T,
    val event: IdentityEvent,
)
