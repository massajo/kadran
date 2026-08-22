package io.korallis.kadran.platform.security

import io.korallis.kadran.platform.security.audit.ActorType
import io.korallis.kadran.platform.security.audit.AuditOutcome
import io.korallis.kadran.platform.security.audit.AuthenticationAction
import io.korallis.kadran.platform.security.audit.AuthenticationAuditEvent
import io.korallis.kadran.platform.security.audit.AuthenticationAuditor
import io.korallis.kadran.platform.security.audit.AuthenticationFailureReason
import io.korallis.kadran.platform.security.token.AccessTokenIssuer
import io.korallis.kadran.platform.security.token.JwtProperties
import io.korallis.kadran.platform.security.token.RefreshTokenFamilyId
import io.korallis.kadran.platform.security.token.RefreshTokenSecret
import io.korallis.kadran.platform.security.token.RefreshTokenState
import io.korallis.kadran.platform.security.token.RefreshTokenStore
import io.korallis.kadran.platform.security.token.StoredRefreshToken
import io.korallis.kadran.platform.tenancy.TenantId
import org.springframework.security.crypto.password.PasswordEncoder
import java.security.SecureRandom
import java.time.Clock
import java.util.UUID

/**
 * Connexion, rotation du rafraîchissement, déconnexion.
 *
 * Trois partis pris structurent la classe :
 *
 * 1. **Le tenant sort de l'authentification, il n'y entre jamais.** Aucune méthode ne prend
 *    de tenant en paramètre d'entrée : il est lu sur le compte retrouvé, puis inscrit dans le
 *    jeton. C'est ce qui rend impossible de se déclarer d'un tenant (spec §9.2).
 * 2. **Le refus est uniforme, le journal est précis.** Le client reçoit
 *    [AuthenticationResult.Refused] dans tous les cas ; le motif exact part dans l'audit.
 * 3. **Chaque chemin, y compris l'échec, émet un événement** (spec §8.4.3). Un journal qui
 *    n'enregistrerait que les succès ne servirait à rien : c'est la série d'échecs qui se
 *    surveille.
 */
class AuthenticationService(
    private val credentialsFinder: CredentialsFinder,
    private val passwordEncoder: PasswordEncoder,
    private val accessTokenIssuer: AccessTokenIssuer,
    private val refreshTokens: RefreshTokenStore,
    private val auditor: AuthenticationAuditor,
    private val properties: JwtProperties,
    private val clock: Clock,
    private val random: SecureRandom = SecureRandom(),
) {
    /**
     * Empreinte d'un mot de passe aléatoire, calculée une fois au premier besoin.
     *
     * Elle sert à vérifier *quelque chose* quand aucun compte ne correspond. Sans elle, une
     * connexion sur un identifiant inconnu répond en une fraction du temps d'une connexion
     * sur un identifiant connu, et cet écart suffit à énumérer les comptes existants — ce que
     * l'uniformité des messages d'erreur cherche précisément à empêcher.
     */
    private val decoyHash: String by lazy {
        checkNotNull(passwordEncoder.encode(UUID.randomUUID().toString())) {
            "l'encodeur de mots de passe n'a rien produit"
        }
    }

    fun login(
        login: String,
        password: CharSequence,
        context: AuthenticationRequestContext,
    ): AuthenticationResult {
        val credentials = credentialsFinder.findByLogin(login)
        // Calculé avant tout branchement, y compris quand le compte est inconnu : c'est le
        // coût du hachage qui égalise les temps de réponse, pas le résultat.
        val passwordMatches = passwordEncoder.matches(password, credentials?.passwordHash ?: decoyHash)

        return if (credentials == null) {
            refuse(AuthenticationAction.LOGIN, AuthenticationFailureReason.UNKNOWN_ACCOUNT, null, context)
        } else {
            admit(credentials, passwordMatches, context)
        }
    }

    /**
     * Échange un jeton de rafraîchissement contre un couple neuf, **en consommant l'ancien**.
     */
    fun refresh(
        presented: RefreshTokenSecret,
        context: AuthenticationRequestContext,
    ): AuthenticationResult {
        val stored = refreshTokens.find(presented.digest())
        return if (stored == null) {
            refuse(
                AuthenticationAction.TOKEN_REFRESHED,
                AuthenticationFailureReason.REFRESH_TOKEN_UNKNOWN,
                tenantId = null,
                context = context,
            )
        } else {
            rotate(stored, context)
        }
    }

    /**
     * Déconnexion : **tous** les jetons de rafraîchissement du compte sont révoqués.
     *
     * Le jeton d'accès en cours, lui, reste valide jusqu'à son expiration — c'est le prix
     * d'une validation sans état, et la raison pour laquelle sa durée de vie est courte.
     */
    fun logout(
        accountId: AccountId,
        tenantId: TenantId,
        context: AuthenticationRequestContext,
    ) {
        refreshTokens.revokeAllOf(accountId, clock.instant())
        auditor.record(
            AuthenticationAuditEvent(
                action = AuthenticationAction.LOGOUT,
                outcome = AuditOutcome.SUCCESS,
                actorType = ActorType.USER,
                correlationId = context.correlationId,
                actorId = accountId,
                tenantId = tenantId,
                ipAddress = context.ipAddress,
                userAgent = context.userAgent,
            ),
        )
    }

    private fun admit(
        credentials: AccountCredentials,
        passwordMatches: Boolean,
        context: AuthenticationRequestContext,
    ): AuthenticationResult {
        val refusal =
            when {
                !passwordMatches -> AuthenticationFailureReason.BAD_PASSWORD
                // Vérifié **après** le mot de passe : répondre plus vite à un compte
                // désactivé qu'à un mot de passe faux en révélerait l'existence.
                !credentials.enabled -> AuthenticationFailureReason.ACCOUNT_DISABLED
                else -> null
            }
        return if (refusal == null) {
            grant(
                credentials.subject(),
                RefreshTokenFamilyId(UUID.randomUUID()),
                AuthenticationAction.LOGIN,
                context,
            )
        } else {
            refuse(
                AuthenticationAction.LOGIN,
                refusal,
                credentials.tenantId,
                context,
                credentials.accountId,
            )
        }
    }

    /**
     * La consommation précède l'émission : si l'écriture du nouveau jeton échouait, l'ancien
     * resterait consommé. Une session perdue est un incident bénin ; un jeton à usage unique
     * qui survit à son usage n'en est pas un.
     *
     * Les revendications sont reprises du jeton stocké, jamais du client : c'est le serveur
     * qui a écrit le tenant et le rôle à la connexion, et une rotation n'est pas une occasion
     * de les changer.
     */
    private fun rotate(
        stored: StoredRefreshToken,
        context: AuthenticationRequestContext,
    ): AuthenticationResult {
        val now = clock.instant()
        return when (stored.stateAt(now)) {
            RefreshTokenState.USABLE -> {
                refreshTokens.markConsumed(stored.digest, now)
                grant(stored.subject(), stored.familyId, AuthenticationAction.TOKEN_REFRESHED, context)
            }

            RefreshTokenState.ALREADY_CONSUMED -> {
                // Deux porteurs détiennent le même jeton : impossible de savoir lequel se
                // présente, donc la session se ferme pour les deux (voir RefreshTokenState).
                refreshTokens.revokeFamily(stored.familyId, now)
                refuseRotation(stored, AuthenticationFailureReason.REFRESH_TOKEN_REPLAYED, context)
            }

            RefreshTokenState.EXPIRED ->
                refuseRotation(stored, AuthenticationFailureReason.REFRESH_TOKEN_EXPIRED, context)

            RefreshTokenState.REVOKED ->
                refuseRotation(stored, AuthenticationFailureReason.REFRESH_TOKEN_REVOKED, context)
        }
    }

    private fun refuseRotation(
        stored: StoredRefreshToken,
        reason: AuthenticationFailureReason,
        context: AuthenticationRequestContext,
    ): AuthenticationResult.Refused =
        refuse(AuthenticationAction.TOKEN_REFRESHED, reason, stored.tenantId, context, stored.accountId)

    private fun grant(
        subject: AuthenticatedSubject,
        familyId: RefreshTokenFamilyId,
        action: AuthenticationAction,
        context: AuthenticationRequestContext,
    ): AuthenticationResult.Granted {
        val now = clock.instant()
        val access = accessTokenIssuer.issue(subject, now)
        val secret = RefreshTokenSecret.generate(random)
        val refreshExpiresAt = now.plus(properties.refreshTokenTtl)

        refreshTokens.save(
            StoredRefreshToken(
                digest = secret.digest(),
                familyId = familyId,
                accountId = subject.accountId,
                tenantId = subject.tenantId,
                role = subject.role,
                issuedAt = now,
                expiresAt = refreshExpiresAt,
            ),
        )
        auditor.record(
            AuthenticationAuditEvent(
                action = action,
                outcome = AuditOutcome.SUCCESS,
                actorType = ActorType.USER,
                correlationId = context.correlationId,
                actorId = subject.accountId,
                tenantId = subject.tenantId,
                ipAddress = context.ipAddress,
                userAgent = context.userAgent,
            ),
        )

        return AuthenticationResult.Granted(
            accessToken = access.value,
            accessTokenExpiresIn = properties.accessTokenTtl,
            refreshToken = secret,
            accountId = subject.accountId,
            tenantId = subject.tenantId,
            role = subject.role,
        )
    }

    private fun refuse(
        action: AuthenticationAction,
        reason: AuthenticationFailureReason,
        tenantId: TenantId?,
        context: AuthenticationRequestContext,
        accountId: AccountId? = null,
    ): AuthenticationResult.Refused {
        auditor.record(
            AuthenticationAuditEvent(
                action = action,
                outcome = AuditOutcome.FAILURE,
                // Personne n'a prouvé son identité : l'acteur reste anonyme même quand le
                // compte visé est, lui, parfaitement connu du serveur.
                actorType = ActorType.ANONYMOUS,
                correlationId = context.correlationId,
                actorId = accountId,
                tenantId = tenantId,
                reason = reason,
                ipAddress = context.ipAddress,
                userAgent = context.userAgent,
            ),
        )
        return AuthenticationResult.Refused
    }
}

private fun StoredRefreshToken.subject(): AuthenticatedSubject = AuthenticatedSubject(accountId, tenantId, role)
