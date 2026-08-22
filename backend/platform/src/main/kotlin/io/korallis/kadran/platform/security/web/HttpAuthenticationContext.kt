package io.korallis.kadran.platform.security.web

import io.korallis.kadran.platform.observability.CorrelationId
import io.korallis.kadran.platform.observability.DiagnosticContext
import io.korallis.kadran.platform.security.AuthenticationRequestContext
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.MDC
import org.springframework.http.HttpHeaders

/**
 * Assemble le contexte que l'audit réclame (spec §8.4.2) à partir de la requête en cours.
 *
 * Le `correlation_id` est **repris du MDC**, où
 * [io.korallis.kadran.platform.web.TenantContextFilter] l'a posé après validation, et n'est
 * jamais relu de l'en-tête entrant : la valeur validée et la valeur brute peuvent différer —
 * c'est tout l'objet de [CorrelationId.fromIncoming] — et le journal doit porter celle que
 * les logs portent, sans quoi la corrélation de bout en bout se casse là où elle sert le plus.
 *
 * L'adresse retenue est celle de la connexion TCP, **pas** `X-Forwarded-For`. Cet en-tête
 * n'est digne de foi que derrière un mandataire de confiance qui l'écrase ; tant que la
 * terminaison n'est pas décidée, l'honorer laisserait n'importe quel client écrire l'adresse
 * de son choix dans un journal réglementaire.
 */
internal fun HttpServletRequest.authenticationContext(): AuthenticationRequestContext =
    AuthenticationRequestContext(
        correlationId = CorrelationId.fromIncoming(MDC.get(DiagnosticContext.CORRELATION_ID_KEY)),
        ipAddress = remoteAddr,
        userAgent = getHeader(HttpHeaders.USER_AGENT),
    )
