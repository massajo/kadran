package io.korallis.kadran.platform.web

import org.springframework.beans.factory.ObjectProvider
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
     * Ordre le plus haut possible : tout ce qui logue pendant la requête — y compris la
     * chaîne de filtres Spring Security et les gestionnaires d'erreur — doit trouver le
     * `correlation_id` déjà posé.
     *
     * **Le repli n'est plus un bean, et ce n'est pas un détail de style.** Il l'était, sous
     * `@ConditionalOnMissingBean`, dans cette `@Configuration` ordinaire — où la condition
     * s'évalue au moment de l'enregistrement, donc avant que les autres configurations aient
     * déclaré les leurs. Le repli gagnait la course une fois sur deux et entrait en conflit
     * avec le vrai résolveur au lieu de s'effacer devant lui ; `@ConditionalOnMissingBean`
     * n'a le comportement qu'on lui prête que dans une auto-configuration, dont l'ordre
     * d'évaluation est garanti postérieur au code applicatif. Défaut relevé en KDN-124, corrigé
     * ici par KDN-18, la première issue à publier une implémentation.
     *
     * Un [ObjectProvider] tranche la question à l'endroit où elle se pose : au moment
     * d'assembler le filtre, tous les beans sont déclarés. `getIfAvailable` prend
     * l'implémentation publiée, retombe sur [AbsentTenantIdResolver] s'il n'y en a aucune, et
     * **lève s'il y en a plusieurs sans `@Primary`** — une ambiguïté sur la provenance du
     * tenant doit empêcher le démarrage, jamais se résoudre en silence.
     */
    @Bean
    fun tenantContextFilterRegistration(
        tenantIdResolver: ObjectProvider<TenantIdResolver>,
    ): FilterRegistrationBean<TenantContextFilter> {
        val resolver = tenantIdResolver.getIfAvailable { AbsentTenantIdResolver }
        return FilterRegistrationBean(TenantContextFilter(resolver)).apply {
            order = Ordered.HIGHEST_PRECEDENCE
            addUrlPatterns("/*")
        }
    }
}
