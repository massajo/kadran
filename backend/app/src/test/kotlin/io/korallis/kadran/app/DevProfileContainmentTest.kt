package io.korallis.kadran.app

import io.korallis.kadran.platform.security.CredentialsFinder
import io.korallis.kadran.platform.security.NoAccountsCredentialsFinder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Confinement du compte de développement (KDN-139) : le profil `dev` n'est **pas** actif ici
 * — exactement la situation d'un déploiement réel, ou de tout autre test de ce module — et
 * les deux effets de bord de `DevCredentialsFinder`/`DevTenantSeeder` doivent rester absents.
 *
 * Deux preuves, l'une de câblage, l'autre de comportement :
 *
 * 1. Aucun bean `CredentialsFinder` n'est publié dans le contexte — c'est ce qui fait que
 *    `SecurityConfiguration` retombe sur `NoAccountsCredentialsFinder`, et pas une
 *    coïncidence de configuration. Une future implémentation additionnelle, activée par
 *    erreur hors du profil `dev`, ferait échouer cette assertion même si le comportement de
 *    connexion restait, par accident, correct.
 * 2. Le login de développement, qui fonctionnerait si `DevCredentialsFinder` était actif, est
 *    refusé comme n'importe quel identifiant inconnu (spec §9.2 : refus uniforme).
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["kadran.security.password.bcrypt-strength=4"],
)
@Testcontainers
class DevProfileContainmentTest {
    @Autowired
    private lateinit var credentialsFinders: ObjectProvider<CredentialsFinder>

    @LocalServerPort
    private var port: Int = 0

    @Test
    fun `without the dev profile, no credentials finder bean is published`() {
        // Reproduit exactement la resolution de SecurityConfiguration : si un bean existait,
        // getIfAvailable le rendrait au lieu de retomber sur la valeur par defaut fournie ici.
        credentialsFinders.getIfAvailable { NoAccountsCredentialsFinder } shouldBe NoAccountsCredentialsFinder
    }

    @Test
    fun `the seeded dev credentials are refused like any unknown account`() {
        val body = """{"login":"dev@kadran.local","password":"kadran-dev-only"}"""
        val request =
            HttpRequest
                .newBuilder(URI.create("http://127.0.0.1:$port/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()

        val response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString())

        response.statusCode() shouldBe HTTP_UNAUTHORIZED
    }

    companion object {
        private const val HTTP_UNAUTHORIZED = 401

        private val CLIENT: HttpClient = HttpClient.newHttpClient()

        @Container
        @ServiceConnection
        @JvmStatic
        val postgres =
            PostgreSQLContainer("postgres:18-alpine")
                .withInitScript("db/bootstrap/create-schemas.sql")
    }
}
