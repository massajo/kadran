package io.korallis.kadran.platform.security

import io.korallis.kadran.platform.tenancy.TenantId

/**
 * Qui agit, pour quel tenant, avec quel rôle — le contenu utile d'un jeton d'accès.
 *
 * Séparé d'[AccountCredentials] pour une raison pratique : une rotation de jeton réémet ces
 * trois valeurs **sans jamais revoir le mot de passe**. Les faire voyager ensemble obligerait
 * à fabriquer un `AccountCredentials` à l'empreinte vide, c'est-à-dire un objet dont un champ
 * ment.
 */
data class AuthenticatedSubject(
    val accountId: AccountId,
    val tenantId: TenantId,
    val role: MembershipRole,
)

/** Ce que le jeton dira du compte. Le mot de passe s'arrête ici. */
fun AccountCredentials.subject(): AuthenticatedSubject = AuthenticatedSubject(accountId, tenantId, role)
