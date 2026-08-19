package io.korallis.kadran.architecture.fixtures.activity.infrastructure.spi.persistence

import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Connection
import javax.sql.DataSource

/**
 * Violations délibérées, écrites pour être **rejetées** par
 * `KadranArchRules.databaseAccessOnlyFromPlatform`.
 *
 * Une règle ArchUnit qui passe faute de matcher quoi que ce soit donne une fausse assurance :
 * sur un dépôt qui n'a encore ni repository ni table métier, les règles de §9.1 seraient
 * vertes même vidées de leur substance. Ces classes sont la contre-épreuve, exercée par
 * `ArchRulesRejectViolationsTest`.
 *
 * Elles vivent dans les sources de **test** : les règles réelles importent la production avec
 * `ImportOption.DoNotIncludeTests` et ne les voient pas. **Ne pas déplacer ce paquet dans
 * `src/main`** — le build échouerait, et à raison.
 *
 * `DSLContext` a son propre fichier, `RepositoryUsingDslContext`.
 */
internal class RepositoryUsingJdbcTemplate(
    private val jdbc: JdbcTemplate,
) {
    fun count(): Int = jdbc.queryForObject("SELECT count(*) FROM outing", Int::class.java) ?: 0
}

/** Tenir la `DataSource`, c'est pouvoir ouvrir une connexion que personne n'a scopée. */
internal class RepositoryUsingDataSource(
    private val dataSource: DataSource,
) {
    fun open(): Connection = dataSource.connection
}

/** Une connexion nue : le dernier étage sous lequel il n'y a plus aucun garde-fou. */
internal class RepositoryUsingConnection(
    private val connection: Connection,
) {
    fun close() = connection.close()
}
