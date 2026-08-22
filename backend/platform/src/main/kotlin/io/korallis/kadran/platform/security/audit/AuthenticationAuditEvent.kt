package io.korallis.kadran.platform.security.audit

import io.korallis.kadran.platform.observability.CorrelationId
import io.korallis.kadran.platform.security.AccountId
import io.korallis.kadran.platform.tenancy.TenantId

/** Type d'acteur — colonne `actor_type` d'`audit_event` (spec §8.4.2). */
enum class ActorType {
    USER,
    SYSTEM,
    JOB,

    /** Une tentative de connexion échouée n'a pas d'acteur connu : personne n'a prouvé être qui que ce soit. */
    ANONYMOUS,
}

/** Colonne `outcome` d'`audit_event`. */
enum class AuditOutcome {
    SUCCESS,

    /** L'opération a échoué sans qu'une règle d'accès soit en cause — identifiants faux, jeton périmé. */
    FAILURE,

    /** Une règle d'accès a refusé. Spec §9.1, contrôle 4 : c'est ce qu'on surveille. */
    DENIED,
}

/**
 * Colonne `action` d'`audit_event`, restreinte aux actions d'authentification et
 * d'autorisation que la spec §8.4.3 range dans ce journal.
 *
 * `PASSWORD_CHANGED` figure dans la liste de la spec ; le changement de mot de passe
 * lui-même n'est pas livré par KDN-18 et arrivera avec la gestion de compte.
 */
enum class AuthenticationAction {
    LOGIN,
    LOGOUT,
    TOKEN_REFRESHED,
    ACCESS_DENIED,
}

/**
 * Un événement d'authentification, dans la forme exacte des colonnes d'`audit_event`
 * (spec §8.4.2) — pour que l'adaptateur de KDN-21 n'ait qu'à recopier, sans traduire.
 *
 * **Aucune PII n'entre ici** (spec §8.1). En particulier, ni l'identifiant de connexion saisi
 * ni le mot de passe : l'échec se raconte par [reason], un code stable et énumérable. Une
 * adresse IP et un `user-agent` y figurent en revanche — ils sont exigés par le journal
 * réglementaire, qui est chiffré et cloisonné, et ne partent jamais vers l'agrégateur de logs.
 *
 * @property tenantId `null` quand l'événement précède l'identification du tenant — une
 *   connexion refusée sur un identifiant inconnu, par exemple. La colonne est nullable pour
 *   cette raison précise, et c'est le seul journal des deux où elle l'est.
 */
data class AuthenticationAuditEvent(
    val action: AuthenticationAction,
    val outcome: AuditOutcome,
    val actorType: ActorType,
    val correlationId: CorrelationId,
    val actorId: AccountId? = null,
    val tenantId: TenantId? = null,
    val reason: AuthenticationFailureReason? = null,
    val ipAddress: String? = null,
    val userAgent: String? = null,
)

/**
 * Pourquoi une opération d'authentification a échoué — pour le journal, **jamais pour le
 * client**.
 *
 * L'API répond toujours la même chose à une connexion refusée : distinguer « compte inconnu »
 * de « mot de passe faux » livre gratuitement la liste des comptes existants. Le journal, lui,
 * a besoin de la distinction — c'est elle qui sépare une faute de frappe d'une énumération.
 */
enum class AuthenticationFailureReason {
    UNKNOWN_ACCOUNT,
    BAD_PASSWORD,
    ACCOUNT_DISABLED,
    REFRESH_TOKEN_UNKNOWN,
    REFRESH_TOKEN_EXPIRED,

    /** Rejeu d'un jeton déjà échangé : la famille entière vient d'être révoquée. */
    REFRESH_TOKEN_REPLAYED,
    REFRESH_TOKEN_REVOKED,

    /** Jeton d'accès absent, mal signé, périmé, ou dont une revendication a été altérée. */
    ACCESS_TOKEN_REJECTED,

    /** Jeton valide, mais autorité insuffisante pour la ressource demandée. */
    INSUFFICIENT_ROLE,
}
