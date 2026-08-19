package io.korallis.kadran.platform.web

import io.korallis.kadran.platform.observability.RecordingLogger
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.server.observation.ServerRequestObservationContext
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.filter.ServerHttpObservationFilter

private class Emitted(
    val logger: RecordingLogger,
) {
    val fields: Map<String, Any?> get() = logger.keyValuesOf(0)
}

/**
 * Ce filtre est la seule chose qui décide de ce que voit un agrégateur de logs pour chaque
 * requête. Deux propriétés s'y jouent, et les deux se perdent en silence : la ligne doit
 * exister même quand la requête a échoué, et elle ne doit **jamais** contenir l'URI brute.
 */
class AccessLogFilterTest :
    StringSpec({
        fun run(
            uri: String,
            route: String? = null,
            status: Int = 200,
            contentLength: String? = null,
            inChain: (ServletResponse) -> Unit = {},
        ): Emitted {
            val request = MockHttpServletRequest("GET", uri)
            val response = MockHttpServletResponse()
            response.status = status
            contentLength?.let { response.setHeader(HttpHeaders.CONTENT_LENGTH, it) }

            route?.let {
                val context = ServerRequestObservationContext(request, response)
                context.pathPattern = it
                request.setAttribute(ServerHttpObservationFilter.CURRENT_OBSERVATION_CONTEXT_ATTRIBUTE, context)
            }

            val logger = RecordingLogger()
            val chain = FilterChain { _, servletResponse -> inChain(servletResponse) }
            runCatching { AccessLogFilter(logger).doFilter(request, response, chain) }
            return Emitted(logger)
        }

        "a completed request emits exactly one access line" {
            val emis = run("/hello")

            emis.logger.events shouldHaveSize 1
            emis.logger.events[0].message shouldBe AccessLogFilter.MESSAGE
        }

        "the line carries the templated route, never the raw URI" {
            val emis = run("/api/outings/8f3c0b12-0000-0000-0000-000000000000", route = "/api/outings/{id}")

            emis.fields[AccessLogFilter.ROUTE_KEY] shouldBe "/api/outings/{id}"
            emis.fields.values.forEach { valeur ->
                (valeur.toString().contains("8f3c0b12")) shouldBe false
            }
        }

        "a request with no handler does not fall back to the URI" {
            // Un 404 sur `/api/outings/8f3c…` reste la fuite qu'on veut eviter : sans route
            // appariee, on n'ecrit rien plutot que d'ecrire l'identifiant.
            val emis = run("/api/outings/8f3c0b12-0000-0000-0000-000000000000", status = 404)

            emis.fields[AccessLogFilter.ROUTE_KEY] shouldBe AccessLogFilter.UNMATCHED
        }

        "the line is emitted even when the chain throws" {
            val emis = run("/hello", route = "/hello") { throw IllegalStateException("echec provoque") }

            emis.logger.events shouldHaveSize 1
            emis.fields[AccessLogFilter.ROUTE_KEY] shouldBe "/hello"
        }

        "method, status and duration accompany the route" {
            val emis = run("/hello", route = "/hello", status = 201)

            emis.fields[AccessLogFilter.METHOD_KEY] shouldBe "GET"
            emis.fields[AccessLogFilter.STATUS_KEY] shouldBe 201
            (emis.fields[AccessLogFilter.DURATION_KEY] as Long >= 0L) shouldBe true
        }

        "the published size is the bytes actually written" {
            // Et non le `Content-Length` annonce : c'est ce qui part sur le fil qui interesse
            // l'exploitation, pas ce que l'application avait promis d'ecrire.
            val emis =
                run("/hello", route = "/hello", contentLength = "512") { response ->
                    response.outputStream.write(ByteArray(7))
                }

            emis.fields[AccessLogFilter.BYTES_KEY] shouldBe 7L
        }

        "with no byte stream, the size falls back to the declared Content-Length" {
            val emis = run("/hello", route = "/hello", contentLength = "512")

            emis.fields[AccessLogFilter.BYTES_KEY] shouldBe 512L
        }

        "the response size is omitted rather than invented" {
            // Ni octets ecrits ni `Content-Length` : aucune taille connue. Un zero serait une
            // donnee fabriquee, ce qu'interdit CLAUDE.md §2.1.
            val emis = run("/hello", route = "/hello")

            emis.fields.keys shouldNotContain AccessLogFilter.BYTES_KEY
        }
    })
