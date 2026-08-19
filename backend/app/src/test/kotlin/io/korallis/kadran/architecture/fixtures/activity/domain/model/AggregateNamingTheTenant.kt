package io.korallis.kadran.architecture.fixtures.activity.domain.model

import io.korallis.kadran.platform.tenancy.TenantId

/**
 * Violation délibérée de `KadranArchRules.domainDoesNotDependOnPlatform`, écrite pour être
 * rejetée (voir `UnscopedRepositories` pour le pourquoi de ces classes).
 *
 * C'est exactement l'agrégat qu'on ne veut pas : un modèle qui porte son tenant. L'isolation
 * s'applique à la frontière de persistance ; un `TenantId` dans le domaine, c'est un champ de
 * plus à ne pas oublier de comparer, et Spring qui entre dans le domaine par la porte de
 * `platform`.
 */
internal data class AggregateNamingTheTenant(
    val tenantId: TenantId,
)
