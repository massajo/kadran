package io.korallis.kadran.platform.web

import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered

/**
 * Enregistre [AccessLogFilter] dans la chaîne servlet du contexte applicatif.
 *
 * Découverte par le `scanBasePackages = ["io.korallis.kadran"]` du module `app`, comme
 * [TenantContextWebConfiguration].
 *
 * **Le port de management n'est pas concerné.** Dès que `management.server.port` diffère du
 * port applicatif, Spring Boot sert Actuator depuis un contexte enfant qui n'hérite pas des
 * `FilterRegistrationBean` du parent : les scrutations du collecteur Prometheus, toutes les
 * quinze secondes, ne noient donc pas la ligne d'accès du trafic réel.
 */
@Configuration(proxyBeanMethods = false)
class AccessLogWebConfiguration {
    /**
     * Juste après [TenantContextFilter] (`HIGHEST_PRECEDENCE`) et le filtre d'observation de
     * Spring (`HIGHEST_PRECEDENCE + 1`) : le premier a posé le `correlation_id` en MDC, le
     * second le contexte d'où sort la route templatisée. Assez en amont, malgré tout, pour
     * que la durée mesurée couvre la quasi-totalité du traitement.
     */
    @Bean
    fun accessLogFilterRegistration(): FilterRegistrationBean<AccessLogFilter> =
        FilterRegistrationBean(AccessLogFilter()).apply {
            order = Ordered.HIGHEST_PRECEDENCE + ORDER_OFFSET
            addUrlPatterns("/*")
        }

    private companion object {
        const val ORDER_OFFSET = 10
    }
}
