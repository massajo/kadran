package io.korallis.kadran.platform.persistence

import io.korallis.kadran.platform.tenancy.TenantId
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.springframework.stereotype.Component
import javax.sql.DataSource

/**
 * Point d'entrée pour obtenir une [TenantScopedQuery] en dehors du fil d'une requête HTTP
 * (KDN-139).
 *
 * ### Pourquoi ce type existe
 *
 * `TenantIsolationRulesTest` (KDN-16) confisque `DSLContext`/`DataSource`/`Connection` à tout
 * code hors de `io.korallis.kadran.platform..` (spec §9.1 contrôle 2) : aucun contexte borné,
 * ni le module `app`, ne peut légitimement les détenir. Jusqu'ici, seul
 * `TenantScopedQuery.forCurrentTenant`/`forTenant` en avait besoin, et seulement à la
 * frontière `infrastructure/spi/persistence` de chaque contexte, où le `DSLContext` arrive
 * déjà encapsulé dans une instance de [TenantScopedQuery] construite ailleurs — mais nulle
 * part, jusqu'à présent, ce « ailleurs » n'était câblé en dehors des tests Testcontainers :
 * aucune classe de production ne construisait de [TenantScopedQuery] pour un traitement
 * asynchrone (le cas que `forTenant` anticipait). `DevTenantSeeder` (module `app`, KDN-139)
 * est le premier appelant réel : il doit écrire une ligne `tenant` au démarrage, hors de tout
 * `TenantContext` de requête, donc via `forTenant`.
 *
 * Ce bean est cette seule porte légitime : il détient le `DSLContext` — construit lui-même à
 * partir du seul `DataSource` que Spring Boot expose déjà (`spring-boot-starter-jdbc`) — et
 * ne rend jamais que des [TenantScopedQuery], jamais le `DSLContext` sous-jacent. Un
 * `DataSource`/`DSLContext` exposé comme bean autonome aurait laissé n'importe quel module
 * appelant tenter de l'injecter directement ; `TenantIsolationRulesTest` l'aurait rejeté à la
 * compilation des tests, mais autant ne jamais publier la tentation.
 *
 * Générique et non confiné au profil `dev` : c'est une capacité de `platform`, pas un
 * artefact de développement — seul son premier appelant l'est.
 */
@Component
class TenantScopedQueryFactory(
    dataSource: DataSource,
) {
    private val delegate = DSL.using(dataSource, SQLDialect.POSTGRES)

    /** Voir [TenantScopedQuery.forTenant] — le tenant est explicite, jamais lu d'un contexte de requête. */
    fun forTenant(tenantId: TenantId): TenantScopedQuery = TenantScopedQuery.forTenant(tenantId, delegate)
}
