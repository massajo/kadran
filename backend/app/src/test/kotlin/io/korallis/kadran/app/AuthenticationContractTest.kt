package io.korallis.kadran.app

import io.korallis.kadran.platform.security.AccountCredentials
import io.korallis.kadran.platform.security.AccountId
import io.korallis.kadran.platform.security.CredentialsFinder
import io.korallis.kadran.platform.security.MembershipRole
import io.korallis.kadran.platform.tenancy.TenantContext
import io.korallis.kadran.platform.tenancy.TenantId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.GetMapping
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
 * L'aller-retour complet, sur l'application réellement démarrée : connexion, appel protégé,
 * refus sans jeton, rotation du rafraîchissement, déconnexion.
 *
 * Ces propriétés n'ont **aucun équivalent unitaire**. Elles ne naissent que du câblage : une
 * chaîne de filtres dans le bon ordre, un `TenantIdResolver` publié qui remplace effectivement
 * le repli, un décodeur partagé entre le filtre de tenant et Spring Security, un gestionnaire
 * d'erreur qui répond en `problem+json` plutôt qu'en page de connexion. Chacune se casse
 * silencieusement, et aucune ne se voit dans un test de service.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["kadran.security.password.bcrypt-strength=4"],
)
@Testcontainers
class AuthenticationContractTest {
    @LocalServerPort
    private var port: Int = 0

    @Test
    fun `valid credentials return an access token and a refresh token`() {
        val response = login()

        response.statusCode() shouldBe HTTP_OK
        response.body() shouldContain "\"accessToken\""
        response.body() shouldContain "\"refreshToken\""
        response.body() shouldContain "\"tenantId\":\"$TENANT_ID\""
        response.body() shouldContain "\"role\":\"OWNER\""
        // Le mot de passe ne repart jamais, sous aucune forme.
        response.body() shouldNotContain PASSWORD
    }

    @Test
    fun `wrong credentials are refused the same way an unknown login is`() {
        val unknown = login(login = "personne@example.test")
        val wrongPassword = login(password = "pas le bon")

        unknown.statusCode() shouldBe HTTP_UNAUTHORIZED
        wrongPassword.statusCode() shouldBe HTTP_UNAUTHORIZED
        // Un client qui compare les deux réponses n'apprend pas quels comptes existent.
        wrongPassword.body() shouldBe unknown.body()
    }

    @Test
    fun `a protected endpoint answers a bearer token and refuses everything else`() {
        val token = tokenOf(login())

        get(WHOAMI, token).statusCode() shouldBe HTTP_OK
        get(WHOAMI).statusCode() shouldBe HTTP_UNAUTHORIZED
        get(WHOAMI, "un.jeton.invente").statusCode() shouldBe HTTP_UNAUTHORIZED
    }

    @Test
    fun `a refusal is problem json, never a login page`() {
        val response = get(WHOAMI)

        response.statusCode() shouldBe HTTP_UNAUTHORIZED
        response.headers().firstValue("Content-Type").orElse("") shouldContain "application/problem+json"
        // Une API qui repond `WWW-Authenticate: Basic` fait surgir une fenetre du navigateur.
        response.headers().firstValue("WWW-Authenticate").isPresent shouldBe false
    }

    @Test
    fun `the tenant of a request comes from the token, and a header cannot override it`() {
        val token = tokenOf(login())

        val honest = get(WHOAMI, token)
        val usurper =
            send(
                HttpRequest
                    .newBuilder(uri(WHOAMI))
                    .header("Authorization", "Bearer $token")
                    .header("X-Tenant-Id", OTHER_TENANT_ID)
                    .GET()
                    .build(),
            )

        // Le contrôleur renvoie le tenant que `TenantContext` a établi : c'est celui du jeton
        // dans les deux cas. Un en-tête serait une usurpation triviale (spec §9.2).
        honest.body() shouldContain TENANT_ID
        usurper.body() shouldContain TENANT_ID
        usurper.body() shouldNotContain OTHER_TENANT_ID
    }

    @Test
    fun `a refresh token can be spent once, and its replay is refused`() {
        val first = login()
        val firstRefresh = refreshTokenOf(first)

        val rotated = refresh(firstRefresh)
        rotated.statusCode() shouldBe HTTP_OK
        refreshTokenOf(rotated) shouldNotBe firstRefresh
        // Le jeton d'acces neuf ouvre bien la porte.
        get(WHOAMI, tokenOf(rotated)).statusCode() shouldBe HTTP_OK

        // Rejeu du jeton deja consomme.
        refresh(firstRefresh).statusCode() shouldBe HTTP_UNAUTHORIZED
        // Et la famille entiere tombe avec lui : le porteur legitime doit se reconnecter.
        refresh(refreshTokenOf(rotated)).statusCode() shouldBe HTTP_UNAUTHORIZED
    }

    @Test
    fun `logging out revokes the refresh token`() {
        val session = login()

        val loggedOut =
            send(
                HttpRequest
                    .newBuilder(uri("/api/auth/logout"))
                    .header("Authorization", "Bearer ${tokenOf(session)}")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build(),
            )

        loggedOut.statusCode() shouldBe HTTP_NO_CONTENT
        refresh(refreshTokenOf(session)).statusCode() shouldBe HTTP_UNAUTHORIZED
    }

    private fun login(
        login: String = LOGIN,
        password: String = PASSWORD,
    ): HttpResponse<String> = post("/api/auth/login", """{"login":"$login","password":"$password"}""")

    private fun refresh(refreshToken: String): HttpResponse<String> =
        post("/api/auth/refresh", """{"refreshToken":"$refreshToken"}""")

    private fun post(
        path: String,
        body: String,
    ): HttpResponse<String> =
        send(
            HttpRequest
                .newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
        )

    private fun get(
        path: String,
        bearerToken: String? = null,
    ): HttpResponse<String> =
        send(
            HttpRequest
                .newBuilder(uri(path))
                .apply { bearerToken?.let { header("Authorization", "Bearer $it") } }
                .GET()
                .build(),
        )

    private fun send(request: HttpRequest): HttpResponse<String> =
        CLIENT.send(request, HttpResponse.BodyHandlers.ofString())

    private fun uri(path: String): URI = URI.create("http://127.0.0.1:$port$path")

    private fun tokenOf(response: HttpResponse<String>): String = claim(response, "accessToken")

    private fun refreshTokenOf(response: HttpResponse<String>): String = claim(response, "refreshToken")

    private fun claim(
        response: HttpResponse<String>,
        name: String,
    ): String =
        Regex("\"$name\":\"([^\"]+)\"").find(response.body())?.groupValues?.get(1)
            ?: error("la reponse ne porte pas '$name' : ${response.body()}")

    /** Le compte que KDN-27 remplacera par les agrégats `Tenant`, `Membership` et `Driver`. */
    @TestConfiguration(proxyBeanMethods = false)
    class AuthenticationFixtures {
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
        fun whoAmIController(): WhoAmIController = WhoAmIController()
    }

    /**
     * Renvoie le tenant que `TenantContext` a établi — donc ce qu'un repository verrait.
     * C'est la seule façon de prouver que la revendication du jeton arrive bien jusqu'au
     * ThreadLocal sur lequel repose toute l'isolation (ADR-001).
     */
    @RestController
    class WhoAmIController {
        @GetMapping(WHOAMI)
        fun whoAmI(): Map<String, String> = mapOf("tenantId" to TenantContext.requireTenantId().toString())
    }

    companion object {
        const val WHOAMI = "/api/whoami"

        private const val HTTP_OK = 200
        private const val HTTP_NO_CONTENT = 204
        private const val HTTP_UNAUTHORIZED = 401

        private const val ACCOUNT_ID = "7c8d9e0f-1a2b-4c3d-8e4f-5a6b7c8d9e0f"
        private const val TENANT_ID = "1d2e3f4a-5b6c-4d7e-8f90-a1b2c3d4e5f6"
        private const val OTHER_TENANT_ID = "0a0b0c0d-0e0f-4a1b-8c2d-3e4f5a6b7c8d"
        private const val LOGIN = "chauffeur@example.test"
        private const val PASSWORD = "correct horse battery staple"

        private val CLIENT: HttpClient = HttpClient.newHttpClient()

        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:18-alpine")
    }
}
