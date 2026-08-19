package io.korallis.kadran.architecture.fixtures.activity.infrastructure.spi.persistence

import org.jooq.DSLContext
import org.jooq.impl.DSL

/**
 * Violation délibérée, écrite pour être rejetée par
 * `KadranArchRules.databaseAccessOnlyFromPlatform` — voir `UnscopedRepositories`.
 *
 * C'est le cas exact que vise `CLAUDE.md` §2.3 : un repository qui tient le `DSLContext` peut
 * émettre `select * from outing` sans prédicat de tenant, et depuis l'ADR-001 aucune policy
 * PostgreSQL ne le rattrapera.
 */
internal class RepositoryUsingDslContext(
    private val dsl: DSLContext,
) {
    fun countAll(): Int = dsl.fetchCount(DSL.table(DSL.name("outing")))
}
