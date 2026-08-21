package io.korallis.kadran.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.properties.HasName
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

/**
 * Règles partagées entre les tests qui les **appliquent** au code de production et le test
 * qui prouve qu'elles **rejettent réellement** une violation
 * ([ArchRulesRejectViolationsTest]).
 *
 * Elles vivent ici plutôt que d'être écrites en ligne dans chaque test parce qu'une règle
 * ArchUnit recopiée à l'identique dans sa propre preuve ne prouve rien : c'est le même objet
 * qui doit être vert sur la production et rouge sur une violation délibérée. Une règle qui
 * passe faute de matcher quoi que ce soit est pire qu'absente — elle rassure à tort.
 */
internal object KadranArchRules {
    const val PLATFORM: String = "io.korallis.kadran.platform.."
    const val PERSISTENCE: String = "io.korallis.kadran.platform.persistence.."

    /**
     * Points d'accès *directs* à la base : ceux qui permettent d'émettre une requête que
     * personne n'a scopée.
     *
     * Le reste de jOOQ n'est pas visé — `Field`, `Condition`, `Record` sont le vocabulaire
     * qu'un repository échange avec `TenantScopedQuery`. C'est `DSLContext` qui exécute, donc
     * c'est lui qu'on confisque, avec ses équivalents JDBC.
     */
    val directDatabaseAccess: DescribedPredicate<JavaClass> =
        JavaClass.Predicates
            .resideInAnyPackage("org.springframework.jdbc..")
            .or(
                HasName.Predicates.nameMatching(
                    "org\\.jooq\\.DSLContext" +
                        "|javax\\.sql\\.DataSource" +
                        "|java\\.sql\\.(Connection|Statement|PreparedStatement|CallableStatement)",
                ),
            ).`as`("un acces direct a la base (DSLContext, JdbcTemplate, DataSource, Connection)")

    /**
     * `CLAUDE.md` §2.3, spec §9.1 contrôle 2.
     *
     * L'exemption de `platform` n'est pas un privilège : c'est là, et là seulement, que vit
     * `TenantScopedQuery`, qui exige le tenant à la construction. Ailleurs, tenir un
     * `DSLContext` c'est pouvoir écrire une requête sans `WHERE tenant_id` — et depuis
     * l'ADR-001, rien en base ne la rattrapera.
     */
    val databaseAccessOnlyFromPlatform: ArchRule =
        noClasses()
            .that()
            .resideInAPackage("io.korallis.kadran..")
            .and()
            .resideOutsideOfPackage(PLATFORM)
            .should()
            .dependOnClassesThat(directDatabaseAccess)
            .because(
                "tout acces a la base passe par TenantScopedQuery, qui exige le TenantId " +
                    "a la construction (CLAUDE.md §2.3, spec §9.1)",
            )

    /**
     * `CLAUDE.md` §2.4, tranché en revue de KDN-15.
     *
     * `TenantId` reste dans `platform` — les contextes bornés en dépendent déjà par
     * convention Gradle — **à condition que le domaine ne nomme jamais le tenant**.
     * L'isolation s'applique à la frontière de persistance, elle n'est pas portée par les
     * agrégats. Sans cette règle, cette condition ne serait garantie par rien : `platform`
     * tire Spring depuis KDN-15, si bien qu'un domaine qui le nommerait le ferait entrer par
     * la bande, sous le nez de la règle « le domaine ignore Spring ».
     */
    val domainDoesNotDependOnPlatform: ArchRule =
        noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage(PLATFORM)
            .because(
                "le domaine ne depend que de shared-kernel et de la stdlib, et ne nomme " +
                    "jamais le tenant (CLAUDE.md §2.4, revue KDN-15)",
            )

    /**
     * Le scoping tenant est une préoccupation d'adaptateur.
     *
     * Un cas d'usage qui nomme `TenantScopedQuery` s'est mis à écrire des requêtes : le port
     * de `domain/spi` est alors mal découpé, et la couche `application` devient un endroit de
     * plus où vérifier qu'un prédicat n'a pas été oublié. On veut exactement l'inverse — un
     * seul endroit, `infrastructure/spi/persistence`.
     */
    val tenantScopingDoesNotLeakIntoApplication: ArchRule =
        noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage(PERSISTENCE)
            .because(
                "le scoping tenant s'applique a la frontiere de persistance, pas dans les " +
                    "cas d'usage (spec §10.2)",
            )
}
