package io.korallis.kadran.app

import io.korallis.kadran.platform.security.AccountCredentials
import io.korallis.kadran.platform.security.AccountId
import io.korallis.kadran.platform.security.CredentialsFinder
import io.korallis.kadran.platform.security.MembershipRole
import io.korallis.kadran.platform.tenancy.TenantId
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
import org.springframework.security.crypto.password.PasswordEncoder
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
    properties = [
        "management.server.port=0",
        // Le cout de production ferait durer chaque connexion de ce test une centaine de
        // millisecondes pour ne rien prouver de plus (voir `PasswordProperties`).
        "kadran.security.password.bcrypt-strength=4",
    ],
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
        // Toujours 404 depuis KDN-18, et pas 401 : la chaine de securite *permet* le chemin —
        // il le faut, elle s'applique aussi au port de management — mais Actuator n'est pas
        // cartographie ici. C'est bien l'absence d'exposition qui est verifiee, pas un refus
        // d'authentification qui la masquerait.
        val response = get(applicationPort, "/actuator/prometheus")

        response.statusCode() shouldBe HTTP_NOT_FOUND
        response.body() shouldNotContain "jvm_memory_used_bytes"
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
        get(applicationPort, "/api/outings/$OUTING_ID", accessToken())

        val ligne = jsonLineContaining(output, ACCESS_MESSAGE)
        ligne shouldContain "\"correlation_id\""
        ligne shouldContain "\"tenant_id\":\"$TENANT_ID\""
    }

    @Test
    fun `the access line carries the templated route and never the identifier`(output: CapturedOutput) {
        get(applicationPort, "/api/outings/$OUTING_ID", accessToken())

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
        bearerToken: String? = null,
    ): HttpResponse<String> {
        val request =
            HttpRequest
                .newBuilder(URI.create("http://127.0.0.1:$port$path"))
                .apply { bearerToken?.let { header(AUTHORIZATION, "Bearer $it") } }
                .GET()
                .build()
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString())
    }

    /**
     * Un jeton réel, obtenu par le vrai endpoint de connexion.
     *
     * C'est ce qui rend le contrôle du `tenant_id` en MDC plus fort qu'avant KDN-18 : le
     * tenant n'est plus posé par un résolveur de test, il traverse la revendication du jeton,
     * puis le filtre de tenant, puis le format de log structuré.
     */
    private fun accessToken(): String {
        val response =
            CLIENT.send(
                HttpRequest
                    .newBuilder(URI.create("http://127.0.0.1:$applicationPort/api/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("""{"login":"$LOGIN","password":"$PASSWORD"}"""))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        response.statusCode() shouldBe HTTP_OK
        return TOKEN_PATTERN.find(response.body())?.groupValues?.get(1)
            ?: error("la reponse de connexion ne porte pas de jeton : ${response.body()}")
    }

    /**
     * Un compte réel et une route à variable de chemin : le premier prouve que le tenant du
     * jeton ressort en MDC dans le JSON, la seconde qu'une URI porteuse d'identifiant se
     * journalise templatisée. Aucun contrôleur de production n'a encore de variable de chemin.
     *
     * **Le résolveur de tenant n'est plus remplacé.** Depuis KDN-18, le vrai lit la
     * revendication du jeton, ce qui rend ce test plus proche de la production qu'un tenant
     * posé de force — et le `@Primary` qui contournait le repli conditionnel de KDN-15 n'a
     * plus d'objet, ce repli n'étant plus un bean.
     */
    @TestConfiguration(proxyBeanMethods = false)
    class ObservabilityFixtures {
        /** Tiendra jusqu'à ce que KDN-27 publie l'adaptateur adossé aux agrégats `identity`. */
        @Bean
        fun credentialsFinder(passwordEncoder: PasswordEncoder): CredentialsFinder {
            val account =
                AccountCredentials(
                    accountId = AccountId(UUID.fromString(ACCOUNT_ID)),
                    tenantId = TenantId(UUID.fromString(TENANT_ID)),
                    role = MembershipRole.OWNER,
                    passwordHash = checkNotNull(passwordEncoder.encode(PASSWORD)),
                )
            return CredentialsFinder { login -> account.takeIf { login == LOGIN } }
        }

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
        const val ACCOUNT_ID = "5b6c7d8e-9f01-4234-8567-89abcdef0123"
        const val LOGIN = "chauffeur@example.test"
        const val PASSWORD = "correct horse battery staple"
        const val ACCESS_MESSAGE = "http access"
        const val TENANT_ID = "9a1f2b3c-4d5e-4f60-8172-a3b4c5d6e7f8"
        const val OUTING_ID = "0c1d2e3f-4a5b-4c6d-8e7f-90a1b2c3d4e5"
        const val AUTHORIZATION = "Authorization"

        val TOKEN_PATTERN = Regex("\"accessToken\":\"([^\"]+)\"")

        val CLIENT: HttpClient = HttpClient.newHttpClient()

        @Container
        @ServiceConnection
        @JvmStatic
        val postgres =
            // Crée kadran/audit et épingle le search_path du rôle avant la première
            // connexion (KDN-136) — sans volume persistant ici, rejoué à chaque conteneur.
            PostgreSQLContainer("postgres:18-alpine")
                .withInitScript("db/bootstrap/create-schemas.sql")
    }
}
