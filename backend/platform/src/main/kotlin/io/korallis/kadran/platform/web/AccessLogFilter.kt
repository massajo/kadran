package io.korallis.kadran.platform.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.filter.ServerHttpObservationFilter

/**
 * Émet **une ligne d'accès par requête HTTP terminée** (spec §10.7.1) : méthode, route
 * templatisée, statut, durée, taille de la réponse.
 *
 * **La route est templatisée, jamais l'URI brute.** `/api/outings/{id}` et non
 * `/api/outings/8f3c…`. Une URI brute en clair de log est deux problèmes à la fois : une
 * fuite d'identifiants vers un agrégateur externe, qui échappe au chiffrement de §8.2 comme
 * à la purge de §8.3 ; et une explosion de cardinalité qui rend tout regroupement impossible.
 * La route vient du contexte d'observation que `RequestMappingInfoHandlerMapping` renseigne
 * au moment où il apparie la requête — donc après la chaîne, ce qui impose de journaliser
 * dans le `finally` et non avant.
 *
 * Les champs sortent en **paires clé/valeur SLF4J**, pas concaténés dans le message : la
 * journalisation structurée les publie tels quels, et une recherche en aval porte alors sur
 * un champ typé plutôt que sur une expression rationnelle appliquée à une phrase.
 *
 * Le `correlation_id` et le `tenant_id` ne sont pas ajoutés ici : [TenantContextFilter] les a
 * posés en MDC avant, et le format structuré les joint à chaque ligne.
 */
class AccessLogFilter(
    /**
     * Catégorie dédiée : l'exploitation route et échantillonne la ligne d'accès
     * indépendamment du reste des logs applicatifs. Injectable pour que le test observe la
     * ligne émise sans dépendre d'une implémentation SLF4J particulière.
     */
    private val accessLog: Logger = LoggerFactory.getLogger(CATEGORY),
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val counted = CountingServletResponse(response)
        val startedAt = System.nanoTime()
        try {
            filterChain.doFilter(request, counted)
        } finally {
            // Dans le `finally` : une requête qui échoue est précisément celle qu'on cherche
            // ensuite dans les logs. Une ligne posée après l'appel serait sautée par la levée.
            emit(request, counted, System.nanoTime() - startedAt)
        }
    }

    private fun emit(
        request: HttpServletRequest,
        response: CountingServletResponse,
        elapsedNanos: Long,
    ) {
        accessLog
            .atInfo()
            .addKeyValue(METHOD_KEY, request.method)
            .addKeyValue(ROUTE_KEY, routeOf(request))
            .addKeyValue(STATUS_KEY, response.status)
            .addKeyValue(DURATION_KEY, elapsedNanos / NANOS_PER_MILLI)
            .also { builder -> responseBytesOf(response)?.let { builder.addKeyValue(BYTES_KEY, it) } }
            .setMessage(MESSAGE)
            .log()
    }

    /**
     * Route templatisée de la requête, ou [UNMATCHED] quand aucun gestionnaire ne l'a
     * appariée — un 404, une ressource statique, une sonde égarée. On ne se rabat **jamais**
     * sur `request.requestURI` : c'est exactement la valeur que cette classe existe pour ne
     * pas écrire.
     */
    private fun routeOf(request: HttpServletRequest): String =
        ServerHttpObservationFilter
            .findObservationContext(request)
            .orElse(null)
            ?.pathPattern
            ?: UNMATCHED

    /**
     * Taille du corps : les octets réellement écrits, à défaut le `Content-Length` annoncé,
     * et sinon rien du tout. Aucune taille n'est reconstituée — une valeur estimée serait une
     * donnée inventée (CLAUDE.md §2.1), et un champ absent se lit mieux qu'un champ faux.
     */
    private fun responseBytesOf(response: CountingServletResponse): Long? =
        response.bytesWritten ?: response.getHeader(HttpHeaders.CONTENT_LENGTH)?.toLongOrNull()

    companion object {
        const val CATEGORY = "io.korallis.kadran.access"

        const val METHOD_KEY = "http_method"
        const val ROUTE_KEY = "http_route"
        const val STATUS_KEY = "http_status"
        const val DURATION_KEY = "http_duration_ms"
        const val BYTES_KEY = "http_response_bytes"
        const val MESSAGE = "http access"
        const val UNMATCHED = "UNMATCHED"
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
