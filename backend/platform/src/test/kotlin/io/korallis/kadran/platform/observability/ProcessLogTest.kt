package io.korallis.kadran.platform.observability

import io.korallis.kadran.platform.tenancy.TenantId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.slf4j.MDC
import org.slf4j.event.Level
import java.util.UUID

private class ImportFailure : RuntimeException("lot rejete : 12 rue des Lilas, jean.dupont@example.test")

class ProcessLogTest :
    StringSpec({
        "a successful process emits one entry line and one exit line" {
            val logger = RecordingLogger()

            ProcessLog(logger).around("ingestion.import.batch") { 42 } shouldBe 42

            logger.events shouldHaveSize 2
            logger.events[0].level shouldBe Level.INFO
            logger.keyValuesOf(0)[ProcessLog.PROCESS_KEY] shouldBe "ingestion.import.batch"
            logger.keyValuesOf(1)[ProcessLog.OUTCOME_KEY] shouldBe "success"
        }

        "the duration is published in milliseconds, under a name that says so" {
            val logger = RecordingLogger()

            ProcessLog(logger).around("performance.recompute") { }

            logger.keyValuesOf(1).keys shouldBe
                setOf(ProcessLog.PROCESS_KEY, ProcessLog.OUTCOME_KEY, ProcessLog.DURATION_KEY)
        }

        "a failure is published at warn, records the exception type and rethrows" {
            val logger = RecordingLogger()

            shouldThrow<ImportFailure> {
                ProcessLog(logger).around("ingestion.import.batch") { throw ImportFailure() }
            }

            logger.events shouldHaveSize 2
            logger.events[1].level shouldBe Level.WARN
            logger.keyValuesOf(1)[ProcessLog.OUTCOME_KEY] shouldBe "failure"
            logger.keyValuesOf(1)[ProcessLog.ERROR_TYPE_KEY] shouldBe ImportFailure::class.java.name
        }

        "an exception message never reaches the logs" {
            // Il porte trop souvent une adresse ou un e-mail (spec §10.7.1) ; un log survit a
            // l'effacement du compte qui l'a produit, la ou §8.3 promet le contraire.
            val logger = RecordingLogger()

            shouldThrow<ImportFailure> {
                ProcessLog(logger).around("ingestion.import.batch") { throw ImportFailure() }
            }

            logger.events[1].message.orEmpty() shouldNotContain "rue des Lilas"
            logger.keyValuesOf(1).values shouldNotContain "12 rue des Lilas, jean.dupont@example.test"
        }

        "both lines are published under the correlation_id of the trigger" {
            val logger = RecordingLogger()
            val correlationId = CorrelationId.generate()
            val tenantId = TenantId(UUID.fromString("11111111-1111-1111-1111-111111111111"))

            var seenDuringProcess: String? = null
            DiagnosticContext.within(correlationId, tenantId) {
                ProcessLog(logger).around("ingestion.import.batch") {
                    seenDuringProcess = MDC.get(DiagnosticContext.CORRELATION_ID_KEY)
                }
            }

            // Le MDC est ambiant : c'est lui que le format structure joint a chaque ligne.
            seenDuringProcess shouldBe correlationId.value
            MDC.get(DiagnosticContext.CORRELATION_ID_KEY) shouldBe null
        }
    })
