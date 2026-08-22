package io.korallis.kadran.platform.web

import io.korallis.kadran.platform.security.AuthenticatedSubject
import io.korallis.kadran.platform.security.MembershipRole
import io.korallis.kadran.platform.security.SecurityTestFixtures
import io.korallis.kadran.platform.security.token.AccessTokenIssuer
import io.korallis.kadran.platform.security.token.JwtCodec
import io.korallis.kadran.platform.tenancy.TenantId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpServletRequest
import java.time.Instant
import java.util.Base64

private val NOW: Instant = Instant.now()

private fun subjectOf(tenantIdValue: String = SecurityTestFixtures.TENANT_A.toString()) =
    AuthenticatedSubject(
        accountId = SecurityTestFixtures.ACCOUNT_A,
        tenantId = TenantId.of(tenantIdValue),
        role = MembershipRole.MANAGER,
    )

private fun bearer(
    token: String,
    extraHeaders: Map<String, String> = emptyMap(),
): MockHttpServletRequest =
    MockHttpServletRequest().apply {
        addHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
        extraHeaders.forEach(::addHeader)
    }

private fun issue(
    codec: JwtCodec,
    subject: AuthenticatedSubject = subjectOf(),
): String = AccessTokenIssuer(codec.encoder, SecurityTestFixtures.properties()).issue(subject, NOW).value

/** Réécrit la revendication de tenant sans retoucher la signature. */
private fun tamper(
    token: String,
    replacement: String,
): String {
    val parts = token.split(".")
    val payload = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
    val altered = payload.replace(SecurityTestFixtures.TENANT_A.toString(), replacement)
    val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(altered.toByteArray(Charsets.UTF_8))
    return listOf(parts[0], encoded, parts[2]).joinToString(".")
}

class JwtTenantIdResolverTest :
    StringSpec({
        "the tenant of a request is the one signed into its token" {
            val codec = SecurityTestFixtures.codec()
            val resolver = JwtTenantIdResolver(codec.decoder)

            resolver.resolve(bearer(issue(codec))) shouldBe SecurityTestFixtures.TENANT_A
        }

        "a request without a token carries no tenant at all" {
            val resolver = JwtTenantIdResolver(SecurityTestFixtures.codec().decoder)

            resolver.resolve(MockHttpServletRequest()).shouldBeNull()
        }

        "a tenant header is never read, even next to a valid token" {
            val codec = SecurityTestFixtures.codec()
            val resolver = JwtTenantIdResolver(codec.decoder)

            // Le cœur de la spec §9.2 : un en-tête est une valeur choisie par le client.
            // L'honorer laisserait n'importe quel utilisateur authentifié lire les données
            // de n'importe quel exploitant.
            val request =
                bearer(
                    issue(codec),
                    mapOf("X-Tenant-Id" to SecurityTestFixtures.TENANT_B.toString()),
                )

            resolver.resolve(request) shouldBe SecurityTestFixtures.TENANT_A
        }

        "a tenant header alone establishes nothing" {
            val resolver = JwtTenantIdResolver(SecurityTestFixtures.codec().decoder)
            val request =
                MockHttpServletRequest().apply {
                    addHeader("X-Tenant-Id", SecurityTestFixtures.TENANT_B.toString())
                }

            resolver.resolve(request).shouldBeNull()
        }

        "a token whose tenant claim was rewritten establishes no tenant" {
            val codec = SecurityTestFixtures.codec()
            val resolver = JwtTenantIdResolver(codec.decoder)

            val forged = tamper(issue(codec), SecurityTestFixtures.TENANT_B.toString())

            // Ni le tenant réclamé, ni celui d'origine : le jeton est simplement invalide.
            resolver.resolve(bearer(forged)).shouldBeNull()
        }

        "a token signed with another key establishes no tenant" {
            val theirs = SecurityTestFixtures.codec(secret = "une-autre-cle-de-trente-deux-octets-au-moins")
            val resolver = JwtTenantIdResolver(SecurityTestFixtures.codec().decoder)

            resolver.resolve(bearer(issue(theirs))).shouldBeNull()
        }

        "a malformed authorization header is a request without a tenant, not a failure" {
            val resolver = JwtTenantIdResolver(SecurityTestFixtures.codec().decoder)

            resolver.resolve(bearer("ceci-n-est-pas-un-jeton")).shouldBeNull()
        }
    })
