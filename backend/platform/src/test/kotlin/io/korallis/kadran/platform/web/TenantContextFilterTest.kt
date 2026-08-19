package io.korallis.kadran.platform.web

import io.korallis.kadran.platform.observability.CorrelationId
import io.korallis.kadran.platform.observability.DiagnosticContext
import io.korallis.kadran.platform.tenancy.MissingTenantContextException
import io.korallis.kadran.platform.tenancy.TenantContext
import io.korallis.kadran.platform.tenancy.TenantId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import jakarta.servlet.FilterChain
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors

private class TestFailure : RuntimeException("echec deliberement provoque")

/** Ce que le thread a vu pendant la chaîne, et ce qu'il en restait juste après le filtre. */
private class Observation(
    val thread: Thread,
    val tenantInChain: TenantId?,
    val mdcInChain: Map<String, String>?,
    val tenantAfter: TenantId?,
    val mdcAfter: Map<String, String>?,
    val response: MockHttpServletResponse,
    val failure: Throwable?,
)

/**
 * Exécute les requêtes sur **un seul et même thread**, comme le fait un pool de conteneur.
 * C'est la seule façon de mettre en évidence une fuite de `ThreadLocal` d'une requête vers
 * la suivante : sur un thread neuf à chaque fois, le défaut est invisible.
 */
private class SingleThreadHarness : AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor()

    fun run(
        resolver: TenantIdResolver = AbsentTenantIdResolver,
        incomingCorrelationId: String? = null,
        inChain: () -> Unit = {},
    ): Observation =
        executor
            .submit(
                Callable {
                    val request = MockHttpServletRequest()
                    incomingCorrelationId?.let { request.addHeader(CorrelationId.HEADER_NAME, it) }
                    val response = MockHttpServletResponse()

                    var tenantInChain: TenantId? = null
                    var mdcInChain: Map<String, String>? = null
                    val chain =
                        FilterChain { _, _ ->
                            tenantInChain = TenantContext.currentOrNull()
                            mdcInChain = MDC.getCopyOfContextMap()
                            inChain()
                        }

                    val failure =
                        runCatching {
                            TenantContextFilter(resolver).doFilter(request, response, chain)
                        }.exceptionOrNull()

                    Observation(
                        thread = Thread.currentThread(),
                        tenantInChain = tenantInChain,
                        mdcInChain = mdcInChain,
                        tenantAfter = TenantContext.currentOrNull(),
                        mdcAfter = MDC.getCopyOfContextMap(),
                        response = response,
                        failure = failure,
                    )
                },
            ).get()

    override fun close() {
        executor.shutdownNow()
    }
}

private fun resolverOf(tenantId: TenantId?) = TenantIdResolver { tenantId }

class TenantContextFilterTest :
    StringSpec({
        val tenantA = TenantId(UUID.fromString("55555555-5555-5555-5555-555555555555"))
        val tenantB = TenantId(UUID.fromString("66666666-6666-6666-6666-666666666666"))

        "le tenant d'une requete ne fuit pas vers la suivante sur le meme thread" {
            SingleThreadHarness().use { harness ->
                val premiere = harness.run(resolverOf(tenantA))
                val deuxieme = harness.run(resolverOf(tenantB))
                val anonyme = harness.run(resolverOf(null))

                // Sans cette egalite, le test ne demontre rien : c'est la reutilisation du
                // thread par le conteneur qui rend la fuite possible.
                deuxieme.thread shouldBe premiere.thread
                anonyme.thread shouldBe premiere.thread

                premiere.tenantInChain shouldBe tenantA
                deuxieme.tenantInChain shouldBe tenantB
                anonyme.tenantInChain.shouldBeNull()

                premiere.tenantAfter.shouldBeNull()
                deuxieme.tenantAfter.shouldBeNull()
                anonyme.tenantAfter.shouldBeNull()
            }
        }

        "une requete anonyme fait echouer requireTenantId au lieu d'heriter du tenant precedent" {
            SingleThreadHarness().use { harness ->
                harness.run(resolverOf(tenantA))

                var leve: Throwable? = null
                harness.run(resolverOf(null)) {
                    leve = runCatching { TenantContext.requireTenantId() }.exceptionOrNull()
                }

                leve.shouldBeInstanceOf<MissingTenantContextException>()
            }
        }

        "le contexte est nettoye meme quand la chaine leve" {
            SingleThreadHarness().use { harness ->
                val enEchec = harness.run(resolverOf(tenantA)) { throw TestFailure() }

                enEchec.failure.shouldNotBeNull()
                enEchec.tenantAfter.shouldBeNull()
                enEchec.mdcAfter.shouldBeNull()

                // Le thread reste utilisable pour la requete suivante, sans residu.
                val suivante = harness.run(resolverOf(null))
                suivante.thread shouldBe enEchec.thread
                suivante.tenantInChain.shouldBeNull()

                val mdc = suivante.mdcInChain.shouldNotBeNull()
                mdc shouldContainKey DiagnosticContext.CORRELATION_ID_KEY
                mdc[DiagnosticContext.TENANT_ID_KEY].shouldBeNull()
            }
        }

        "le MDC porte correlation_id et tenant_id pendant la requete, et rien apres" {
            SingleThreadHarness().use { harness ->
                val observation = harness.run(resolverOf(tenantA))

                val mdc = observation.mdcInChain.shouldNotBeNull()
                mdc[DiagnosticContext.TENANT_ID_KEY] shouldBe tenantA.value.toString()
                mdc[DiagnosticContext.CORRELATION_ID_KEY].shouldNotBeNull()

                observation.mdcAfter.shouldBeNull()
            }
        }

        "un correlation_id entrant valide est repris et renvoye" {
            val entrant = "7f0e1d2c-3b4a-4596-8778-99aabbccddee"

            SingleThreadHarness().use { harness ->
                val observation = harness.run(resolverOf(tenantA), incomingCorrelationId = entrant)

                observation.mdcInChain?.get(DiagnosticContext.CORRELATION_ID_KEY) shouldBe entrant
                observation.response.getHeader(CorrelationId.HEADER_NAME) shouldBe entrant
            }
        }

        "un correlation_id absent est genere et renvoye" {
            SingleThreadHarness().use { harness ->
                val observation = harness.run(resolverOf(tenantA))

                val renvoye = observation.response.getHeader(CorrelationId.HEADER_NAME).shouldNotBeNull()
                observation.mdcInChain?.get(DiagnosticContext.CORRELATION_ID_KEY) shouldBe renvoye
            }
        }

        "un correlation_id forge est remplace, jamais recopie dans les logs" {
            val forge = "abcdefgh\nERROR ligne de log fabriquee par le client"

            SingleThreadHarness().use { harness ->
                val observation = harness.run(resolverOf(tenantA), incomingCorrelationId = forge)

                val retenu = observation.mdcInChain?.get(DiagnosticContext.CORRELATION_ID_KEY)
                retenu.shouldNotBeNull()
                retenu shouldNotBe forge
                observation.response.getHeader(CorrelationId.HEADER_NAME) shouldBe retenu
            }
        }

        "le resolveur par defaut n'etablit aucun tenant" {
            SingleThreadHarness().use { harness ->
                harness.run().tenantInChain.shouldBeNull()
            }
        }
    })
