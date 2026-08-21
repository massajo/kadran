package io.korallis.kadran.app

import io.korallis.kadran.platform.tenancy.TenantId
import io.korallis.kadran.platform.web.TenantIdResolver
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.boot.test.web.server.LocalManagementPort
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID

/**
 * Contrôle de bout en bout du contrat d'observabilité de la spec §10.7 et d'ADR-011.
 *
 * Les quatre propriétés vérifiées ici n'ont aucun équivalent unitaire : elles ne naissent que
 * du câblage réel de Spring Boot — un contexte enfant sur le port de management, un registre
 * Prometheus branché à Micrometer, des groupes de santé validés au démarrage, un format de
 * log structuré appliqué à la sortie standard. Chacune se casse silencieusement : Actuator
 * qui revient sur le port applicatif, un libellé `tenant_id` ajouté par mégarde, une sonde
 * `liveness` qui se met à dépendre de la base, une URI brute qui repart dans les logs.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    // Port de management **distinct** du port applicatif, comme en production ; à zéro pour
    // ne pas dépendre d'un 8081 libre sur la machine qui exécute les tests.
    properties = ["management.server.port=0"],
)
@Testcontainers
@ExtendWith(OutputCaptureExtension::class)
class ObservabilityContractTest {
    @LocalServerPort
    private var applicationPort: Int = 0

    @LocalManagementPort
    private var managementPort: Int = 0

    @Test
    fun `prometheus is not served on the application port`() {
        get(applicationPort, "/actuator/prometheus").statusCode() shouldBe HTTP_NOT_FOUND
    }

    @Test
    fun `prometheus is served on the management port`() {
        managementPort shouldNotBe applicationPort

        val response = get(managementPort, "/actuator/prometheus")

        response.statusCode() shouldBe HTTP_OK
        response.body() shouldContain "jvm_memory_used_bytes"
    }

    @Test
    fun `no metric carries a dimension identifying a tenant or an aggregate`() {
        // ADR-011 : cardinalité et confidentialité. Le test lit la sortie réelle de
        // l'endpoint plutôt que le registre en mémoire — c'est cette sortie-là qui part.
        val exposition = get(managementPort, "/actuator/prometheus").body()

        listOf("tenant_id", "driver_id", "outing_id", "account_id").forEach { interdit ->
            exposition shouldNotContain interdit
        }
    }

    @Test
    fun `readiness depends on the database, liveness does not`() {
        // Une base indisponible doit retirer l'instance du trafic, jamais déclencher une
        // boucle de redémarrages (spec §10.7.4).
        get(managementPort, "/actuator/health/readiness").body() shouldContain "\"db\""
        get(managementPort, "/actuator/health/liveness").body() shouldNotContain "\"db\""
    }

    @Test
    fun `every log line is JSON carrying correlation_id and tenant_id`(output: CapturedOutput) {
        get(applicationPort, "/api/outings/$OUTING_ID")

        val ligne = jsonLineContaining(output, ACCESS_MESSAGE)
        ligne shouldContain "\"correlation_id\""
        ligne shouldContain "\"tenant_id\":\"$TENANT_ID\""
    }

    @Test
    fun `the access line carries the templated route and never the identifier`(output: CapturedOutput) {
        get(applicationPort, "/api/outings/$OUTING_ID")

        val ligne = jsonLineContaining(output, ACCESS_MESSAGE)
        ligne shouldContain "\"http_route\":\"/api/outings/{id}\""
        ligne shouldNotContain OUTING_ID
        ligne shouldContain "\"http_status\":200"
    }

    private fun jsonLineContaining(
        output: CapturedOutput,
        message: String,
    ): String =
        output
            .all
            .lineSequence()
            .filter { it.startsWith("{") && it.contains("\"$message\"") }
            .lastOrNull()
            ?: error("aucune ligne JSON ne porte le message '$message' :\n${output.all}")

    private fun get(
        port: Int,
        path: String,
    ): HttpResponse<String> =
        CLIENT.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port$path")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    /**
     * Un tenant fixe et une route à variable de chemin : le premier prouve que le MDC de
     * KDN-15 ressort dans le JSON, la seconde qu'une URI porteuse d'identifiant se journalise
     * templatisée. Aucun contrôleur de production n'a encore de variable de chemin.
     */
    @TestConfiguration(proxyBeanMethods = false)
    class ObservabilityFixtures {
        // `@Primary` plutot qu'un simple bean : le repli `AbsentTenantIdResolver` de KDN-15
        // est enregistre avant la configuration de test, si bien que son
        // `@ConditionalOnMissingBean` ne le desactive pas.
        @Bean
        @Primary
        fun fixedTenantIdResolver(): TenantIdResolver = TenantIdResolver { TenantId(UUID.fromString(TENANT_ID)) }

        @Bean
        fun outingsController(): OutingsController = OutingsController()
    }

    @RestController
    class OutingsController {
        @GetMapping("/api/outings/{id}")
        fun outing(
            @PathVariable id: String,
        ): Map<String, String> = mapOf("id" to id)
    }

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_NOT_FOUND = 404
        const val ACCESS_MESSAGE = "http access"
        const val TENANT_ID = "9a1f2b3c-4d5e-4f60-8172-a3b4c5d6e7f8"
        const val OUTING_ID = "0c1d2e3f-4a5b-4c6d-8e7f-90a1b2c3d4e5"

        val CLIENT: HttpClient = HttpClient.newHttpClient()

        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:18-alpine")
    }
}
