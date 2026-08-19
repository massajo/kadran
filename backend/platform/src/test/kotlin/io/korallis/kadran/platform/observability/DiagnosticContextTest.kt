package io.korallis.kadran.platform.observability

import io.korallis.kadran.platform.tenancy.TenantId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.slf4j.MDC
import java.util.UUID

private class TestFailure : RuntimeException("echec deliberement provoque")

/**
 * Le MDC est un état de thread, donc partagé avec la requête suivante servie par ce thread :
 * ces cas vérifient qu'il est restauré dans tous les chemins de sortie (spec §8.4).
 */
class DiagnosticContextTest :
    StringSpec({
        val tenantA = TenantId(UUID.fromString("44444444-4444-4444-4444-444444444444"))

        beforeTest { MDC.clear() }
        afterTest { MDC.clear() }

        "les cles sont figees en snake_case" {
            // Ces noms deviendront des champs de logs structures : les renommer casserait
            // toutes les recherches deja ecrites.
            DiagnosticContext.CORRELATION_ID_KEY shouldBe "correlation_id"
            DiagnosticContext.TENANT_ID_KEY shouldBe "tenant_id"
        }

        "les cles sont posees pendant le bloc et retirees apres" {
            val correlationId = CorrelationId.generate()

            DiagnosticContext.within(correlationId, tenantA) {
                MDC.get(DiagnosticContext.CORRELATION_ID_KEY) shouldBe correlationId.value
                MDC.get(DiagnosticContext.TENANT_ID_KEY) shouldBe tenantA.value.toString()
            }

            MDC.get(DiagnosticContext.CORRELATION_ID_KEY).shouldBeNull()
            MDC.get(DiagnosticContext.TENANT_ID_KEY).shouldBeNull()
        }

        "sans tenant, la cle est retiree et non laissee a sa valeur precedente" {
            MDC.put(DiagnosticContext.TENANT_ID_KEY, "residu-d-un-traitement-precedent")

            DiagnosticContext.within(CorrelationId.generate(), null) {
                MDC.get(DiagnosticContext.TENANT_ID_KEY).shouldBeNull()
            }
        }

        "le MDC est restaure meme si le bloc leve" {
            shouldThrow<TestFailure> {
                DiagnosticContext.within(CorrelationId.generate(), tenantA) { throw TestFailure() }
            }

            MDC.get(DiagnosticContext.CORRELATION_ID_KEY).shouldBeNull()
            MDC.get(DiagnosticContext.TENANT_ID_KEY).shouldBeNull()
        }

        "un bloc imbrique rend au thread l'integralite du MDC de son englobant" {
            val englobant = CorrelationId.generate()
            MDC.put("cle_hors_perimetre", "valeur")

            DiagnosticContext.within(englobant, tenantA) {
                DiagnosticContext.within(CorrelationId.generate(), null) { }

                MDC.get(DiagnosticContext.CORRELATION_ID_KEY) shouldBe englobant.value
                MDC.get(DiagnosticContext.TENANT_ID_KEY) shouldBe tenantA.value.toString()
            }

            MDC.get("cle_hors_perimetre") shouldBe "valeur"
        }

        "le MDC survit a un changement de dispatcher grace a son element" {
            val correlationId = CorrelationId.generate()

            DiagnosticContext.within(correlationId, tenantA) {
                runBlocking(currentDiagnosticContext()) {
                    withContext(Dispatchers.IO) {
                        MDC.get(DiagnosticContext.CORRELATION_ID_KEY) shouldBe correlationId.value
                        MDC.get(DiagnosticContext.TENANT_ID_KEY) shouldBe tenantA.value.toString()
                    }
                }
            }
        }
    })
