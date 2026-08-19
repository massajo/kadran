package io.korallis.kadran.platform.web

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered

/**
 * Enregistre [TenantContextFilter] dans la chaîne servlet.
 *
 * Découverte par le `scanBasePackages = ["io.korallis.kadran"]` du module `app` : aucun
 * câblage à ajouter côté bootstrap.
 */
@Configuration(proxyBeanMethods = false)
class TenantContextWebConfiguration {
    /**
     * Repli tant que l'authentification n'existe pas. `@ConditionalOnMissingBean` fait que
     * la future implémentation JWT prendra la place sans rien retirer d'ici.
     */
    @Bean
    @ConditionalOnMissingBean(TenantIdResolver::class)
    fun absentTenantIdResolver(): TenantIdResolver = AbsentTenantIdResolver

    /**
     * Ordre le plus haut possible : tout ce qui logue pendant la requête — y compris les
     * filtres de sécurité et les gestionnaires d'erreur — doit trouver le `correlation_id`
     * déjà posé.
     */
    @Bean
    fun tenantContextFilterRegistration(
        tenantIdResolver: TenantIdResolver,
    ): FilterRegistrationBean<TenantContextFilter> =
        FilterRegistrationBean(TenantContextFilter(tenantIdResolver)).apply {
            order = Ordered.HIGHEST_PRECEDENCE
            addUrlPatterns("/*")
        }
}
