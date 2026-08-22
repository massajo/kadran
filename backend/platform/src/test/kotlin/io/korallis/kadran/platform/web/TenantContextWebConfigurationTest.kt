package io.korallis.kadran.platform.web

import io.korallis.kadran.platform.tenancy.TenantContext
import io.korallis.kadran.platform.tenancy.TenantId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import jakarta.servlet.FilterChain
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.util.UUID

private val PUBLISHED_TENANT = TenantId(UUID.fromString("44444444-4444-4444-8444-444444444444"))

/** Ce que publiera l'authentification — ici réduit à sa plus simple expression. */
@Configuration(proxyBeanMethods = false)
private class PublishingResolver {
    @Bean
    fun tenantIdResolver(): TenantIdResolver = TenantIdResolver { PUBLISHED_TENANT }
}

private fun tenantSeenByChain(vararg configurations: Class<*>): TenantId? {
    AnnotationConfigApplicationContext().use { context ->
        context.register(TenantContextWebConfiguration::class.java, *configurations)
        context.refresh()

        val filter =
            context
                .getBean("tenantContextFilterRegistration", FilterRegistrationBean::class.java)
                .filter as TenantContextFilter

        var seen: TenantId? = null
        filter.doFilter(
            MockHttpServletRequest(),
            MockHttpServletResponse(),
            FilterChain { _, _ ->
                seen = TenantContext.currentOrNull()
            },
        )
        return seen
    }
}

/**
 * Le défaut que ce test verrouille a été relevé pendant KDN-124 et corrigé par KDN-18.
 *
 * `absentTenantIdResolver` portait `@ConditionalOnMissingBean` dans une `@Configuration`
 * ordinaire, où la condition s'évalue à l'enregistrement de la définition — donc avant que
 * les autres configurations aient déclaré les leurs. Le repli ne s'effaçait pas devant
 * l'implémentation réelle : il entrait en conflit avec elle. Le test échoue si quelqu'un
 * réintroduit le repli sous forme de bean.
 */
class TenantContextWebConfigurationTest :
    StringSpec({
        "a published resolver is the one the filter uses" {
            tenantSeenByChain(PublishingResolver::class.java) shouldBe PUBLISHED_TENANT
        }

        "with no resolver published, the filter establishes no tenant" {
            tenantSeenByChain().shouldBeNull()
        }

        "the fallback is code, not a bean, so nothing can conflict with a real resolver" {
            AnnotationConfigApplicationContext().use { context ->
                context.register(
                    TenantContextWebConfiguration::class.java,
                    PublishingResolver::class.java,
                )
                context.refresh()

                // Un seul candidat dans le contexte : le repli n'en est pas un.
                val resolvers = context.getBeanNamesForType(TenantIdResolver::class.java)
                resolvers.size shouldBe 1
                context.getBean(TenantIdResolver::class.java) shouldBeSameInstanceAs
                    context.getBean("tenantIdResolver", TenantIdResolver::class.java)
            }
        }
    })
