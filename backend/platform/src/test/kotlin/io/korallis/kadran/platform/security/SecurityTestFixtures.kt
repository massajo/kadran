package io.korallis.kadran.platform.security

import io.korallis.kadran.platform.observability.CorrelationId
import io.korallis.kadran.platform.security.audit.AuthenticationAuditEvent
import io.korallis.kadran.platform.security.audit.AuthenticationAuditor
import io.korallis.kadran.platform.security.token.JwtCodec
import io.korallis.kadran.platform.security.token.JwtProperties
import io.korallis.kadran.platform.tenancy.TenantId
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.DelegatingPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/**
 * Fixtures partagées par les tests de sécurité.
 *
 * **Aucun mock.** Le chiffrement, le hachage et l'horloge sont réels ; seules la source des
 * comptes et la destination de l'audit sont remplacées, et par des objets qui font vraiment
 * le travail. MockK reste réservé à la couche `application` (spec §10.4), et un test qui
 * simulerait un encodeur de mots de passe ne prouverait rien de ce qui compte ici.
 */
internal object SecurityTestFixtures {
    val TENANT_A: TenantId = TenantId(UUID.fromString("11111111-1111-4111-8111-111111111111"))
    val TENANT_B: TenantId = TenantId(UUID.fromString("22222222-2222-4222-8222-222222222222"))
    val ACCOUNT_A: AccountId = AccountId(UUID.fromString("33333333-3333-4333-8333-333333333333"))

    const val SECRET: String = "une-cle-de-test-de-plus-de-trente-deux-octets-!"
    const val PASSWORD: String = "correct horse battery staple"

    /**
     * Coût bcrypt minimal : un test qui hache une dizaine de mots de passe au coût de
     * production durerait plusieurs secondes pour ne rien prouver de plus. C'est exactement
     * pourquoi le coût est une propriété de configuration et non une constante du code.
     */
    const val TEST_BCRYPT_STRENGTH: Int = 4

    fun properties(secret: String = SECRET): JwtProperties = JwtProperties(secret = secret)

    fun codec(secret: String = SECRET): JwtCodec = JwtCodec(properties(secret))

    fun passwordEncoder(): PasswordEncoder =
        DelegatingPasswordEncoder(
            "bcrypt",
            mapOf("bcrypt" to BCryptPasswordEncoder(TEST_BCRYPT_STRENGTH)),
        )

    fun requestContext(): AuthenticationRequestContext =
        AuthenticationRequestContext(
            correlationId = CorrelationId.generate(),
            ipAddress = "203.0.113.7",
            userAgent = "kadran-tests",
        )
}

/** Horloge que le test avance à la main : l'expiration se vérifie sans attendre. */
internal class SettableClock(
    var now: Instant,
) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = now
}

/** Source de comptes à laquelle le test ajoute ce dont il a besoin. */
internal class InMemoryCredentialsFinder(
    private val encoder: PasswordEncoder,
) : CredentialsFinder {
    private val accounts = mutableMapOf<String, AccountCredentials>()

    fun register(
        login: String,
        password: String,
        accountId: AccountId,
        tenantId: TenantId,
        role: MembershipRole = MembershipRole.OWNER,
        enabled: Boolean = true,
    ): AccountCredentials {
        val credentials =
            AccountCredentials(
                accountId = accountId,
                tenantId = tenantId,
                role = role,
                passwordHash = checkNotNull(encoder.encode(password)),
                enabled = enabled,
            )
        accounts[login.lowercase()] = credentials
        return credentials
    }

    override fun findByLogin(login: String): AccountCredentials? = accounts[login.lowercase()]
}

/** Journal d'audit du test : on vérifie ce qui a été émis, pas qu'un appel a eu lieu. */
internal class RecordingAuditor : AuthenticationAuditor {
    val events: MutableList<AuthenticationAuditEvent> = mutableListOf()

    override fun record(event: AuthenticationAuditEvent) {
        events += event
    }

    fun last(): AuthenticationAuditEvent = events.last()
}
