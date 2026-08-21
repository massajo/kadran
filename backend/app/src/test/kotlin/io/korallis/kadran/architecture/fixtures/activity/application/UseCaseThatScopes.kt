package io.korallis.kadran.architecture.fixtures.activity.application

import io.korallis.kadran.platform.persistence.TenantScopedTable

/**
 * Violation délibérée de
 * `KadranArchRules.tenantScopingStaysOutOfApplication`, écrite pour être rejetée
 * (voir `UnscopedRepositories` pour le pourquoi de ces classes).
 *
 * Un cas d'usage qui déclare lui-même la table qu'il interroge s'est mis à écrire des
 * requêtes : le port de `domain/spi` est mal découpé, et il existe désormais un endroit de
 * plus où vérifier qu'un prédicat n'a pas été oublié.
 */
internal class UseCaseThatScopes {
    fun table(): TenantScopedTable<*> = TenantScopedTable.named("outing")
}
