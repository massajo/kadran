package io.korallis.kadran.platform.security

import io.korallis.kadran.platform.security.audit.ActorType
import io.korallis.kadran.platform.security.audit.AuditOutcome
import io.korallis.kadran.platform.security.audit.AuthenticationAction
import io.korallis.kadran.platform.security.audit.AuthenticationFailureReason
import io.korallis.kadran.platform.security.token.AccessTokenIssuer
import io.korallis.kadran.platform.security.token.InMemoryRefreshTokenStore
import io.korallis.kadran.platform.security.token.RefreshTokenSecret
import io.korallis.kadran.platform.security.token.RefreshTokenState
import io.korallis.kadran.platform.security.token.authenticatedSubjectOf
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Instant

/**
 * Le décodeur JWT valide contre l'horloge réelle : une date figée dans le passé ferait
 * expirer les jetons émis par ce test dès le lendemain de son écriture. Tout est donc relatif
 * à maintenant — l'horloge du service, elle, reste pilotée à la main.
 */
private val START: Instant = Instant.now()

/** Tout ce dont un cas a besoin, monté d'un bloc pour que chaque test reparte de zéro. */
private class Harness {
    val clock = SettableClock(START)
    val auditor = RecordingAuditor()
    val encoder = SecurityTestFixtures.passwordEncoder()
    val accounts = InMemoryCredentialsFinder(encoder)
    val refreshTokens = InMemoryRefreshTokenStore()
    val properties = SecurityTestFixtures.properties()
    val codec = SecurityTestFixtures.codec()

    val service =
        AuthenticationService(
            credentialsFinder = accounts,
            passwordEncoder = encoder,
            accessTokenIssuer = AccessTokenIssuer(codec.encoder, properties),
            refreshTokens = refreshTokens,
            auditor = auditor,
            properties = properties,
            clock = clock,
        )

    fun withAccount(
        login: String = "chauffeur@example.test",
        password: String = SecurityTestFixtures.PASSWORD,
        enabled: Boolean = true,
    ): Harness =
        apply {
            accounts.register(
                login = login,
                password = password,
                accountId = SecurityTestFixtures.ACCOUNT_A,
                tenantId = SecurityTestFixtures.TENANT_A,
                enabled = enabled,
            )
        }

    fun login(
        login: String = "chauffeur@example.test",
        password: String = SecurityTestFixtures.PASSWORD,
    ): AuthenticationResult = service.login(login, password, SecurityTestFixtures.requestContext())

    fun granted(): AuthenticationResult.Granted = login().shouldBeInstanceOf<AuthenticationResult.Granted>()

    fun refresh(secret: RefreshTokenSecret): AuthenticationResult =
        service.refresh(secret, SecurityTestFixtures.requestContext())
}

class AuthenticationServiceTest :
    StringSpec({
        "valid credentials yield a token pair and a SUCCESS audit event" {
            val harness = Harness().withAccount()

            val granted = harness.granted()

            granted.accountId shouldBe SecurityTestFixtures.ACCOUNT_A
            granted.tenantId shouldBe SecurityTestFixtures.TENANT_A
            granted.role shouldBe MembershipRole.OWNER
            granted.accessTokenExpiresIn shouldBe harness.properties.accessTokenTtl

            val event = harness.auditor.last()
            event.action shouldBe AuthenticationAction.LOGIN
            event.outcome shouldBe AuditOutcome.SUCCESS
            event.actorType shouldBe ActorType.USER
            event.actorId shouldBe SecurityTestFixtures.ACCOUNT_A
            event.tenantId shouldBe SecurityTestFixtures.TENANT_A
        }

        "the tenant written in the token comes from the account, never from the caller" {
            val harness = Harness().withAccount()

            val granted = harness.granted()
            val subject = authenticatedSubjectOf(harness.codec.decoder.decode(granted.accessToken)).shouldNotBeNull()

            // Rien dans la signature de `login` ne permet de proposer un tenant : c'est la
            // garantie que la spec §9.2 demande, et elle se lit ici en creux.
            subject.tenantId shouldBe SecurityTestFixtures.TENANT_A
            subject.tenantId shouldNotBe SecurityTestFixtures.TENANT_B
            subject.accountId shouldBe SecurityTestFixtures.ACCOUNT_A
            subject.role shouldBe MembershipRole.OWNER
        }

        "an unknown login is refused, and the audit says so without naming the account" {
            val harness = Harness().withAccount()

            harness.login(login = "inconnu@example.test") shouldBe AuthenticationResult.Refused

            val event = harness.auditor.last()
            event.outcome shouldBe AuditOutcome.FAILURE
            event.reason shouldBe AuthenticationFailureReason.UNKNOWN_ACCOUNT
            event.actorType shouldBe ActorType.ANONYMOUS
            event.actorId shouldBe null
            event.tenantId shouldBe null
        }

        "a wrong password is refused with the same answer as an unknown login" {
            val harness = Harness().withAccount()

            val unknown = harness.login(login = "inconnu@example.test")
            val wrongPassword = harness.login(password = "pas le bon")

            // Le client ne peut pas distinguer les deux : c'est ce qui empêche d'énumérer
            // les comptes existants depuis le formulaire de connexion.
            wrongPassword shouldBe unknown
            harness.auditor.last().reason shouldBe AuthenticationFailureReason.BAD_PASSWORD
        }

        "a disabled account is refused even with the right password" {
            val harness = Harness().withAccount(enabled = false)

            harness.login() shouldBe AuthenticationResult.Refused
            harness.auditor.last().reason shouldBe AuthenticationFailureReason.ACCOUNT_DISABLED
        }

        "a refresh token is exchanged for a new pair, and the old one is consumed" {
            val harness = Harness().withAccount()
            val first = harness.granted()

            val second = harness.refresh(first.refreshToken).shouldBeInstanceOf<AuthenticationResult.Granted>()

            second.refreshToken shouldNotBe first.refreshToken
            second.tenantId shouldBe first.tenantId
            second.accountId shouldBe first.accountId
            harness.refreshTokens
                .find(first.refreshToken.digest())
                .shouldNotBeNull()
                .stateAt(harness.clock.now) shouldBe RefreshTokenState.ALREADY_CONSUMED
            harness.auditor.last().action shouldBe AuthenticationAction.TOKEN_REFRESHED
        }

        "replaying a consumed refresh token is refused and revokes the whole family" {
            val harness = Harness().withAccount()
            val first = harness.granted()
            val second = harness.refresh(first.refreshToken).shouldBeInstanceOf<AuthenticationResult.Granted>()

            harness.refresh(first.refreshToken) shouldBe AuthenticationResult.Refused

            harness.auditor.last().reason shouldBe AuthenticationFailureReason.REFRESH_TOKEN_REPLAYED
            // Le point qui compte : le jeton encore neuf du porteur légitime est mort lui
            // aussi. On ne sait pas lequel des deux porteurs vient de se présenter.
            harness.refresh(second.refreshToken) shouldBe AuthenticationResult.Refused
            harness.auditor.last().reason shouldBe AuthenticationFailureReason.REFRESH_TOKEN_REVOKED
        }

        "an expired refresh token is refused" {
            val harness = Harness().withAccount()
            val granted = harness.granted()

            harness.clock.now = START.plus(harness.properties.refreshTokenTtl).plusSeconds(1)

            harness.refresh(granted.refreshToken) shouldBe AuthenticationResult.Refused
            harness.auditor.last().reason shouldBe AuthenticationFailureReason.REFRESH_TOKEN_EXPIRED
        }

        "a refresh token nobody ever issued is refused without a tenant in the audit trail" {
            val harness = Harness().withAccount()

            harness.refresh(RefreshTokenSecret.of("jeton-fabrique-de-toutes-pieces")) shouldBe
                AuthenticationResult.Refused

            val event = harness.auditor.last()
            event.reason shouldBe AuthenticationFailureReason.REFRESH_TOKEN_UNKNOWN
            event.tenantId shouldBe null
        }

        "logging out revokes every refresh token of the account" {
            val harness = Harness().withAccount()
            val first = harness.granted()
            val second = harness.granted()

            harness.service.logout(
                SecurityTestFixtures.ACCOUNT_A,
                SecurityTestFixtures.TENANT_A,
                SecurityTestFixtures.requestContext(),
            )

            harness.refresh(first.refreshToken) shouldBe AuthenticationResult.Refused
            harness.refresh(second.refreshToken) shouldBe AuthenticationResult.Refused
            harness.auditor.events
                .map { it.action }
                .filter { it == AuthenticationAction.LOGOUT }
                .shouldContainExactly(AuthenticationAction.LOGOUT)
        }

        "every path emits exactly one audit event, failures included" {
            val harness = Harness().withAccount()

            harness.login()
            harness.login(password = "faux")
            harness.login(login = "inconnu@example.test")

            harness.auditor.events.size shouldBe 3
            harness.auditor.events.map { it.outcome } shouldContainExactly
                listOf(AuditOutcome.SUCCESS, AuditOutcome.FAILURE, AuditOutcome.FAILURE)
        }
    })
