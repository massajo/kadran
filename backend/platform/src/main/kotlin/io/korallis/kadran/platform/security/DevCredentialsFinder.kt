package io.korallis.kadran.platform.security

import io.korallis.kadran.platform.tenancy.TenantId
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Compte unique de développement (KDN-139) : la seule façon de se connecter à l'UI tant que
 * l'onboarding complet (KDN-26/28, `identity`, agrégats `Membership`/`Driver`) n'existe pas.
 *
 * ### Pourquoi `@Profile("dev")` suffit à ne jamais fuir
 *
 * `SecurityConfiguration` résout `CredentialsFinder` par `ObjectProvider<CredentialsFinder>`,
 * avec `NoAccountsCredentialsFinder` en repli si **aucun** bean n'est publié (voir sa KDoc).
 * Sans le profil `dev` actif, Spring n'enregistre jamais ce bean : il n'existe alors aucun
 * `CredentialsFinder` dans le contexte, et le repli s'applique — refus systématique. C'est
 * l'inverse d'un bean neutre qu'il faudrait explicitement désactiver ailleurs : l'absence de
 * profil suffit, et `DevProfileContainmentTest` (module `app`) le prouve en démarrant
 * l'application réelle sans ce profil.
 *
 * ### Le tenant annoncé doit exister réellement
 *
 * [DEV_TENANT_ID] n'est pas qu'une valeur portée par le jeton : c'est aussi l'identifiant que
 * `DevTenantSeeder` (module `app`, seul point où `platform` peut être composé avec
 * `context-identity`) enregistre en base au démarrage, s'il n'y est pas déjà. Sans ce
 * seeding, la connexion réussirait mais tout accès scopé sur ce tenant ne verrait aucune
 * ligne — les deux classes partagent donc le même UUID fixe, jamais généré à la volée.
 *
 * [DEV_ACCOUNT_ID], à l'inverse, n'est **jamais persisté** : `identity` ne porte pas encore
 * d'agrégat `Account`/`Membership` réel pour ce compte de secours, et l'issue est explicite —
 * l'agrégat `Tenant` seul suffit à donner un `tenant_id` valide aux écrans à tester. Ce n'est
 * pas une donnée manquante déguisée : l'`AccountId` du jeton ne sert qu'à remplir l'`actor_id`
 * de l'audit (spec §8.4), qui n'est pas non plus câblé pour `identity` à ce stade.
 */
@Component
@Profile("dev")
class DevCredentialsFinder(
    passwordEncoder: PasswordEncoder,
    @Value("\${KADRAN_DEV_LOGIN:dev@kadran.local}") login: String,
    @Value("\${KADRAN_DEV_PASSWORD:kadran-dev-only}") password: String,
) : CredentialsFinder {
    private val normalizedLogin = login.normalized()

    private val account =
        AccountCredentials(
            accountId = AccountId(DEV_ACCOUNT_ID),
            tenantId = TenantId(DEV_TENANT_ID),
            role = MembershipRole.OWNER,
            passwordHash =
                checkNotNull(passwordEncoder.encode(password)) {
                    "l'encodeur de mots de passe n'a rien produit"
                },
        )

    /**
     * Même normalisation que celle attendue d'un adaptateur réel (voir la KDoc de
     * [CredentialsFinder.findByLogin]) : insensible à la casse et aux espaces superflus.
     */
    override fun findByLogin(login: String): AccountCredentials? =
        account.takeIf {
            login.normalized() ==
                normalizedLogin
        }

    private fun String.normalized(): String = trim().lowercase()

    companion object {
        /** Partagé avec `DevTenantSeeder` (module `app`) — jamais généré, toujours ce même UUID. */
        val DEV_TENANT_ID: UUID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")

        /** N'est jamais écrit en base — voir la KDoc de la classe. */
        val DEV_ACCOUNT_ID: UUID = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")
    }
}
