package io.korallis.kadran.platform.security.web

import io.korallis.kadran.platform.security.AuthenticationService
import io.korallis.kadran.platform.security.CredentialsFinder
import io.korallis.kadran.platform.security.NoAccountsCredentialsFinder
import io.korallis.kadran.platform.security.PasswordProperties
import io.korallis.kadran.platform.security.audit.AuthenticationAuditor
import io.korallis.kadran.platform.security.audit.LoggingAuthenticationAuditor
import io.korallis.kadran.platform.security.token.AccessTokenIssuer
import io.korallis.kadran.platform.security.token.InMemoryRefreshTokenStore
import io.korallis.kadran.platform.security.token.JwtCodec
import io.korallis.kadran.platform.security.token.JwtProperties
import io.korallis.kadran.platform.security.token.RefreshTokenStore
import io.korallis.kadran.platform.security.token.authenticatedSubjectOf
import io.korallis.kadran.platform.web.JwtTenantIdResolver
import io.korallis.kadran.platform.web.TenantIdResolver
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.DelegatingPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.web.SecurityFilterChain
import java.time.Clock

/**
 * Câblage de l'authentification (spec §10.3, ligne `Auth`).
 *
 * **Les trois ports de la sécurité se résolvent par [ObjectProvider], pas par
 * `@ConditionalOnMissingBean`.** Le motif est celui corrigé dans
 * [io.korallis.kadran.platform.web.TenantContextWebConfiguration] : dans une `@Configuration`
 * ordinaire, la condition s'évalue à l'enregistrement de la définition, donc avant que les
 * autres configurations aient déclaré les leurs — un repli conditionnel y gagne la course une
 * fois sur deux. Un [ObjectProvider] résolu paresseusement tranche au moment d'assembler les
 * beans, quand toutes les définitions sont connues.
 *
 * Chaque repli est délibérément le choix le plus restrictif : aucun compte, un magasin de
 * jetons en mémoire, un audit qui ne fait que journaliser. Aucun ne convient à la production,
 * et chacun le dit dans sa propre documentation.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties::class, PasswordProperties::class)
class SecurityConfiguration(
    private val credentialsFinderProvider: ObjectProvider<CredentialsFinder>,
    private val refreshTokenStoreProvider: ObjectProvider<RefreshTokenStore>,
    private val auditorProvider: ObjectProvider<AuthenticationAuditor>,
) {
    /**
     * `by lazy` et non une initialisation directe : résoudre un [ObjectProvider] dans le
     * constructeur d'une `@Configuration` forcerait l'instanciation de ces beans avant la fin
     * de l'enregistrement des définitions — le défaut d'ordonnancement qu'on corrige, à un
     * autre endroit.
     */
    private val credentialsFinder: CredentialsFinder by lazy {
        credentialsFinderProvider.getIfAvailable { NoAccountsCredentialsFinder }
    }

    /**
     * Résolu **une seule fois** : le repli en mémoire porte l'état des familles de jetons, et
     * deux instances se révoqueraient l'une l'autre dans le vide.
     */
    private val refreshTokenStore: RefreshTokenStore by lazy {
        refreshTokenStoreProvider.getIfAvailable { InMemoryRefreshTokenStore() }
    }

    private val auditor: AuthenticationAuditor by lazy {
        auditorProvider.getIfAvailable { LoggingAuthenticationAuditor() }
    }

    @Bean
    fun jwtCodec(properties: JwtProperties): JwtCodec = JwtCodec(properties)

    /**
     * `DelegatingPasswordEncoder` plutôt que `BCryptPasswordEncoder` nu, pour la seule raison
     * qui vaille : le préfixe `{bcrypt}` écrit dans l'empreinte. Sans lui, l'algorithme retenu
     * aujourd'hui deviendrait irrévocable — passer à argon2 exigerait de réinitialiser tous
     * les mots de passe. Avec lui, les deux cohabitent et chaque connexion réussie peut
     * réencoder.
     *
     * bcrypt et non argon2 : `Argon2PasswordEncoder` réclame BouncyCastle sur le classpath,
     * une dépendance lourde pour un gain nul face à un bcrypt correctement coûté. Le choix
     * reste réversible sans migration, c'est tout l'objet du préfixe.
     */
    @Bean
    fun passwordEncoder(properties: PasswordProperties): PasswordEncoder =
        DelegatingPasswordEncoder(
            BCRYPT_ID,
            mapOf(BCRYPT_ID to BCryptPasswordEncoder(properties.bcryptStrength)),
        )

    @Bean
    fun accessTokenIssuer(
        codec: JwtCodec,
        properties: JwtProperties,
    ): AccessTokenIssuer = AccessTokenIssuer(codec.encoder, properties)

    /**
     * L'horloge est injectée plutôt que lue par `Instant.now()` au fil du service : rotation,
     * consommation et expiration se testent alors en avançant le temps, sans attendre.
     */
    @Bean
    fun authenticationService(
        passwordEncoder: PasswordEncoder,
        accessTokenIssuer: AccessTokenIssuer,
        properties: JwtProperties,
    ): AuthenticationService =
        AuthenticationService(
            credentialsFinder = credentialsFinder,
            passwordEncoder = passwordEncoder,
            accessTokenIssuer = accessTokenIssuer,
            refreshTokens = refreshTokenStore,
            auditor = auditor,
            properties = properties,
            clock = Clock.systemUTC(),
        )

    /**
     * L'implémentation que [io.korallis.kadran.platform.web.TenantContextWebConfiguration]
     * attendait depuis KDN-15 : le tenant vient de la revendication du jeton, jamais d'un
     * en-tête.
     */
    @Bean
    fun jwtTenantIdResolver(codec: JwtCodec): TenantIdResolver = JwtTenantIdResolver(codec.decoder)

    /**
     * **Tout est refusé sauf ce qui est nommé ici.** La liste des exceptions est courte et
     * doit le rester : les deux portes d'entrée de l'authentification, le point de contrôle
     * de la chaîne de livraison, et la sonde de santé — cette dernière n'ayant d'effet que si
     * quelqu'un ramenait Actuator sur le port applicatif, puisque §10.7.2 le sert ailleurs.
     *
     * `csrf` désactivé et session `STATELESS` : il n'y a ni cookie ni session côté serveur, et
     * un jeton porteur ne part pas tout seul avec une requête inter-site. `formLogin` et
     * `httpBasic` désactivés pour une raison voisine — une API qui répond une page de
     * connexion ou un `WWW-Authenticate: Basic` transforme un 401 en fenêtre du navigateur.
     */
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        codec: JwtCodec,
    ): SecurityFilterChain {
        val entryPoint = AuditingAuthenticationEntryPoint(auditor)
        val accessDeniedHandler = AuditingAccessDeniedHandler(auditor)
        return http
            .csrf { it.disable() }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { registry ->
                registry
                    .requestMatchers(HttpMethod.POST, LOGIN_PATH, REFRESH_PATH)
                    .permitAll()
                    .requestMatchers(HELLO_PATH, HEALTH_PATH, PROMETHEUS_PATH, ERROR_PATH)
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            }.oauth2ResourceServer { resourceServer ->
                resourceServer
                    .jwt { jwt ->
                        jwt.decoder(codec.decoder)
                        jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
                    }.authenticationEntryPoint(entryPoint)
                    .accessDeniedHandler(accessDeniedHandler)
            }.exceptionHandling {
                it.authenticationEntryPoint(entryPoint)
                it.accessDeniedHandler(accessDeniedHandler)
            }.build()
    }

    /**
     * Le rôle du jeton devient l'unique autorité de l'utilisateur.
     *
     * Une revendication illisible donne une liste **vide**, pas une exception : le jeton est
     * alors authentifié sans pouvoir, ce qui se traduit par un 403 plutôt que par un 500. Le
     * cas ne devrait pas survenir — `KadranClaimsValidator` refuse déjà ces jetons — et le
     * traiter quand même évite qu'un assouplissement futur de la validation ouvre une brèche.
     */
    private fun jwtAuthenticationConverter(): JwtAuthenticationConverter =
        JwtAuthenticationConverter().apply {
            setJwtGrantedAuthoritiesConverter(
                Converter<Jwt, Collection<GrantedAuthority>> { token ->
                    authenticatedSubjectOf(token)
                        ?.let { listOf(SimpleGrantedAuthority(it.role.authority)) }
                        ?: emptyList()
                },
            )
        }

    private companion object {
        const val BCRYPT_ID = "bcrypt"

        const val LOGIN_PATH = "/api/auth/login"
        const val REFRESH_PATH = "/api/auth/refresh"
        const val HELLO_PATH = "/hello"

        /**
         * **Les deux endpoints Actuator exposés sont permis parce que cette chaîne s'applique
         * aussi au port de management.** Contrairement aux `FilterRegistrationBean` du
         * contexte parent, qui ne franchissent pas la frontière du contexte enfant (voir
         * [io.korallis.kadran.platform.web.AccessLogWebConfiguration]), la chaîne de sécurité,
         * elle, la franchit : sans ces deux lignes, le collecteur Prometheus se prend un 401
         * et la sonde de santé aussi. Ce qui protège ce port n'est pas l'authentification mais
         * la topologie — il n'est publié ni par le service web ni par l'ingress (ADR-011,
         * spec §10.7.2).
         *
         * Les permettre ne les ouvre pas sur le port applicatif : Actuator n'y est pas
         * cartographié, et une requête permise vers un chemin sans gestionnaire reste un 404.
         */
        const val HEALTH_PATH = "/actuator/health/**"
        const val PROMETHEUS_PATH = "/actuator/prometheus"

        /**
         * **`/error` doit être permis, et l'oubli est vicieux.** Spring Boot enregistre la
         * chaîne de sécurité sur les aiguillages `REQUEST` **et** `ERROR` ; quand une requête
         * authentifiée échoue, le conteneur ré-aiguille vers `/error`, la chaîne s'y applique
         * de nouveau et, ce chemin n'étant permis à personne, répond 401. Le symptôme est
         * qu'une erreur de sérialisation ou un 404 remontent au client en « non authentifié »,
         * en masquant complètement la cause. Le corps servi là ne dit rien de sensible :
         * `server.error.include-*` vaut `never` par défaut.
         */
        const val ERROR_PATH = "/error"
    }
}
