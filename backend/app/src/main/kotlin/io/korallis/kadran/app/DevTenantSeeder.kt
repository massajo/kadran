package io.korallis.kadran.app

import io.korallis.kadran.identity.domain.model.LegalName
import io.korallis.kadran.identity.domain.model.OnboardingStatus
import io.korallis.kadran.identity.domain.model.Siren
import io.korallis.kadran.identity.domain.model.Tenant
import io.korallis.kadran.identity.domain.spi.TenantRepository
import io.korallis.kadran.identity.infrastructure.spi.persistence.JooqTenantRepository
import io.korallis.kadran.platform.persistence.TenantScopedQueryFactory
import io.korallis.kadran.platform.security.DevCredentialsFinder
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.Instant
import io.korallis.kadran.core.TenantId as AggregateTenantId
import io.korallis.kadran.platform.tenancy.TenantId as IsolationTenantId

/**
 * Enregistre le tenant de développement au démarrage, s'il n'existe pas déjà (KDN-139).
 *
 * ### Pourquoi ce n'est pas dans `identity`, ni dans `platform`
 *
 * - `identity/application` n'a encore aucun cas d'usage (l'onboarding complet est KDN-26/28,
 *   hors de portée) : il n'y a pas de commande « enregistrer un tenant » à appeler, seul
 *   l'agrégat `Tenant.register` existe. Écrire ici la composition minimale — construire la
 *   requête scopée, appeler le domaine, sauvegarder — est donc l'équivalent local d'un cas
 *   d'usage d'une ligne, pas un contournement de la couche application.
 * - `platform` ne peut pas dépendre de `context-identity` (ce serait une dépendance Gradle
 *   circulaire, `context-identity` dépendant déjà de `platform`) : `LegalName`, `Siren`,
 *   `Tenant` et `JooqTenantRepository` n'y sont pas visibles. `app` est le seul module qui
 *   dépende des deux (spec §10.1) — c'est la raison structurelle, pas une question de
 *   commodité, pour laquelle ce fichier vit ici plutôt qu'ailleurs.
 *
 * ### Pas d'événement d'audit
 *
 * `CLAUDE.md` §2.5 l'exige pour toute mutation, via `@Audited` — qui n'existe pas encore
 * (KDN-22/KDN-21), exactement comme le documente `IdentityEvent`. Ce script de démarrage
 * n'invente pas de mécanisme d'audit qu'aucun autre code d'`identity` ne porte aujourd'hui ;
 * il applique la même règle transitoire qui régit déjà `Tenant.register`.
 *
 * ### Idempotence
 *
 * Une lecture précède l'écriture (`findCurrent`), scopée sur le même [DevCredentialsFinder.DEV_TENANT_ID]
 * que celui du compte de développement — redémarrer l'application ne réinsère jamais la ligne.
 */
@Component
@Profile("dev")
class DevTenantSeeder(
    private val queries: TenantScopedQueryFactory,
) : CommandLineRunner {
    override fun run(vararg args: String) {
        val tenantId = IsolationTenantId(DevCredentialsFinder.DEV_TENANT_ID)
        val repository: TenantRepository = JooqTenantRepository(queries.forTenant(tenantId))

        if (repository.findCurrent() != null) {
            log.info("tenant de developpement deja present : {}", tenantId)
            return
        }

        val registered =
            Tenant
                .register(
                    id = AggregateTenantId(DevCredentialsFinder.DEV_TENANT_ID),
                    legalName = LegalName.of(LEGAL_NAME),
                    siren = Siren.of(SIREN),
                    at = Instant.now(),
                ).state
                // Complet plutot que bloque a l'etape 1 : le but est de tester les ecrans
                // au-dela de l'assistant d'onboarding (KDN-40 et suivants), pas de le rejouer.
                .resumeOnboardingAt(OnboardingStatus.COMPLETED)

        repository.save(registered)
        log.info("tenant de developpement cree : {}", tenantId)
    }

    private companion object {
        val log = LoggerFactory.getLogger(DevTenantSeeder::class.java)

        const val LEGAL_NAME = "Kadran Dev"

        // SIREN de test a cle de Luhn valide, deja utilise par TenantTest (context-identity) :
        // aucune entreprise reelle ne doit etre engagee par une donnee de developpement.
        const val SIREN = "732829320"
    }
}
