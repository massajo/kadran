package io.korallis.kadran.platform.security.web

import io.korallis.kadran.platform.security.AuthenticatedSubject
import io.korallis.kadran.platform.security.audit.ActorType
import io.korallis.kadran.platform.security.audit.AuditOutcome
import io.korallis.kadran.platform.security.audit.AuthenticationAction
import io.korallis.kadran.platform.security.audit.AuthenticationAuditEvent
import io.korallis.kadran.platform.security.audit.AuthenticationAuditor
import io.korallis.kadran.platform.security.audit.AuthenticationFailureReason
import io.korallis.kadran.platform.security.token.authenticatedSubjectOf
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler

/**
 * **401 — personne ne s'est présenté, ou pas valablement.**
 *
 * Déclenché sur une requête protégée sans jeton, ou avec un jeton expiré, mal signé ou
 * incomplet. L'événement part en `FAILURE` et non en `DENIED` : aucune règle d'accès n'a
 * tranché, il n'y a simplement pas d'identité établie.
 */
class AuditingAuthenticationEntryPoint(
    private val auditor: AuthenticationAuditor,
) : AuthenticationEntryPoint {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        val context = request.authenticationContext()
        auditor.record(
            AuthenticationAuditEvent(
                action = AuthenticationAction.ACCESS_DENIED,
                outcome = AuditOutcome.FAILURE,
                actorType = ActorType.ANONYMOUS,
                correlationId = context.correlationId,
                reason = AuthenticationFailureReason.ACCESS_TOKEN_REJECTED,
                ipAddress = context.ipAddress,
                userAgent = context.userAgent,
            ),
        )
        ProblemResponse.write(response, HttpStatus.UNAUTHORIZED)
    }
}

/**
 * **403 — quelqu'un d'identifié a demandé ce à quoi il n'a pas droit.**
 *
 * L'événement part en `DENIED`, ce que la spec §9.1 contrôle 4 désigne comme le contrôle
 * détectif qui compense l'abandon du RLS : c'est cette valeur-là qu'une alerte surveille, et
 * la confondre avec un `FAILURE` d'authentification noierait le signal dans le bruit des
 * jetons périmés.
 */
class AuditingAccessDeniedHandler(
    private val auditor: AuthenticationAuditor,
) : AccessDeniedHandler {
    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException,
    ) {
        val context = request.authenticationContext()
        val subject = currentSubject()
        auditor.record(
            AuthenticationAuditEvent(
                action = AuthenticationAction.ACCESS_DENIED,
                outcome = AuditOutcome.DENIED,
                actorType = if (subject == null) ActorType.ANONYMOUS else ActorType.USER,
                correlationId = context.correlationId,
                actorId = subject?.accountId,
                tenantId = subject?.tenantId,
                reason = AuthenticationFailureReason.INSUFFICIENT_ROLE,
                ipAddress = context.ipAddress,
                userAgent = context.userAgent,
            ),
        )
        ProblemResponse.write(response, HttpStatus.FORBIDDEN)
    }
}

/**
 * Acteur et tenant de la requête refusée, lus du `SecurityContext`.
 *
 * `null` quand l'authentification est anonyme — un 403 peut suivre un accès anonyme à une
 * ressource dont la règle exige un rôle.
 */
private fun currentSubject(): AuthenticatedSubject? =
    (SecurityContextHolder.getContext().authentication?.principal as? Jwt)?.let(::authenticatedSubjectOf)
