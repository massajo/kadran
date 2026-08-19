package io.korallis.kadran.platform.web

import io.korallis.kadran.platform.observability.CorrelationId
import io.korallis.kadran.platform.observability.DiagnosticContext
import io.korallis.kadran.platform.tenancy.TenantContext
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Établit le contexte d'exécution d'une requête HTTP : tenant courant (spec §9.2) et
 * `correlation_id` en MDC (spec §8.4).
 *
 * Ce filtre s'exécute avant tout le reste de la chaîne, pour que la moindre ligne de log
 * émise pendant la requête porte déjà sa corrélation.
 *
 * **Le nettoyage n'est pas une politesse, c'est la garantie centrale.** Tomcat réutilise ses
 * threads : un `ThreadLocal` laissé posé sert le tenant de la requête précédente à la
 * suivante, silencieusement, à un utilisateur parfaitement légitime. Rien ne le rattraperait
 * — il n'y a pas de RLS (ADR-001). Le nettoyage vit donc dans les `finally` de
 * [TenantContext.withOptionalTenant] et [DiagnosticContext.within], et non dans une ligne
 * placée après l'appel à la chaîne, qu'une exception sauterait.
 */
class TenantContextFilter(
    private val tenantIdResolver: TenantIdResolver,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val correlationId = CorrelationId.fromIncoming(request.getHeader(CorrelationId.HEADER_NAME))
        // Posé avant la chaîne : une fois la réponse validée, plus aucun en-tête n'est
        // accepté. Le client peut ainsi citer l'identifiant dans un signalement.
        response.setHeader(CorrelationId.HEADER_NAME, correlationId.value)

        val tenantId = tenantIdResolver.resolve(request)

        DiagnosticContext.within(correlationId, tenantId) {
            TenantContext.withOptionalTenant(tenantId) {
                filterChain.doFilter(request, response)
            }
        }
    }
}
