package io.korallis.kadran.identity.infrastructure.spi.persistence

import io.korallis.kadran.core.TenantId
import io.korallis.kadran.identity.domain.model.LegalName
import io.korallis.kadran.identity.domain.model.OnboardingStatus
import io.korallis.kadran.identity.domain.model.Siren
import io.korallis.kadran.identity.domain.model.Tenant
import io.korallis.kadran.identity.domain.spi.TenantRepository
import io.korallis.kadran.identity.infrastructure.spi.persistence.IdentityTables.TENANT
import io.korallis.kadran.identity.infrastructure.spi.persistence.IdentityTables.TenantColumns
import io.korallis.kadran.platform.persistence.TenantScopedQuery
import org.jooq.Field
import org.jooq.Record

/**
 * Adaptateur `tenant` (spec §9.3).
 *
 * L'exploitant est passé au constructeur **par le `TenantScopedQuery`**, jamais en argument de
 * méthode : il n'existe aucune instance de cet adaptateur qui ne sache pas déjà de quel
 * exploitant elle parle (`CLAUDE.md` §2.3, ADR-001).
 *
 * À la création d'un exploitant, l'appelant ouvre la requête sur l'identifiant **du nouvel
 * exploitant** — `TenantScopedQuery.forTenant(newId, dsl)`. C'est le seul moment où le tenant
 * scopé n'est pas celui de la session en cours, et c'est sans risque : la requête ne peut
 * toujours écrire que sur lui.
 */
class JooqTenantRepository(
    private val query: TenantScopedQuery,
) : TenantRepository {
    override fun findCurrent(): Tenant? = query.selectFrom(TENANT).fetchOne()?.toTenant()

    override fun save(tenant: Tenant) {
        // Le `tenant_id` est passé volontairement : `TenantScopedQuery.insertInto` refuse
        // alors une valeur qui ne serait pas celle du scope. Le taire ferait perdre ce
        // contrôle, et l'écriture croisée redeviendrait silencieuse (spec §9.1 contrôle 4).
        query
            .insertInto(TENANT, insertValues(tenant))
            .onConflict(TENANT.tenantId)
            .doUpdate()
            .set(mutableValues(tenant))
            .execute()
    }

    private fun insertValues(tenant: Tenant): Map<Field<*>, Any?> =
        mutableValues(tenant) +
            mapOf<Field<*>, Any?>(
                TENANT.tenantId to tenant.id.value,
                // Ecrite une seule fois, comme le tenant_id : `created_at` n'a pas de méthode
                // de mutation côté domaine, elle ne doit donc jamais figurer dans un `SET`.
                TenantColumns.CREATED_AT to tenant.createdAt.toColumnValue(),
            )

    private fun mutableValues(tenant: Tenant): Map<Field<*>, Any?> =
        mapOf(
            TenantColumns.LEGAL_NAME to tenant.legalName.value,
            TenantColumns.SIREN to tenant.siren.value,
            TenantColumns.ONBOARDING_STATUS to tenant.onboardingStatus.name,
            TenantColumns.CLOSED_AT to tenant.closedAt?.toColumnValue(),
        )

    private fun Record.toTenant(): Tenant =
        Tenant(
            id = TenantId(read(TENANT.tenantId)),
            legalName = LegalName.of(read(TenantColumns.LEGAL_NAME)),
            siren = Siren.of(read(TenantColumns.SIREN)),
            onboardingStatus = OnboardingStatus.valueOf(read(TenantColumns.ONBOARDING_STATUS)),
            closedAt = readOrNull(TenantColumns.CLOSED_AT)?.toInstant(),
            createdAt = read(TenantColumns.CREATED_AT).toInstant(),
        )
}
