package io.korallis.kadran.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readText

/** Une chaîne SQL du code de production qui interroge une table métier sans la scoper. */
internal data class UnscopedSqlLiteral(
    val origin: String,
    val table: String,
    val query: String,
) {
    override fun toString(): String = "$origin — table `$table` sans tenant_id : $query"
}

/**
 * Second volet du contrôle 2 de la spec §9.1 : « une règle scannant les chaînes SQL
 * littérales — toute requête mentionnant une table métier sans `tenant_id` fait échouer le
 * build ».
 *
 * ArchUnit ne peut pas porter cette règle : le bytecode ne conserve pas les littéraux d'un
 * corps de méthode sous une forme que son modèle expose. Le contrôle se fait donc sur les
 * **sources**, ce qui est de toute façon le bon niveau — c'est la chaîne écrite qui est
 * fautive, pas la classe qui la porte.
 *
 * **Le sens de la règle est « refus par défaut ».** La spec parle de « table métier », mais
 * il n'existe pas de registre des tables métier tant qu'elles n'existent pas — une règle qui
 * en dépendrait serait vide aujourd'hui et le resterait tant que personne ne penserait à
 * l'alimenter. On inverse donc la charge : **toute** table est réputée scopée, sauf celles
 * listées dans [UNSCOPED_TABLES]. Ajouter une table à cette liste est un acte délibéré,
 * qui se relit en PR ; oublier d'y ajouter une table métier n'a aucune conséquence, puisque
 * le défaut est le comportement sûr.
 */
internal object SqlLiteralScanner {
    /**
     * Tables légitimement dépourvues de `tenant_id`.
     *
     * `schema_baseline` est la table de contrôle de KDN-14 ; les deux autres appartiennent à
     * Liquibase. **N'ajouter une entrée ici qu'avec sa justification** : chaque ligne est un
     * trou dans le contrôle 2.
     */
    val UNSCOPED_TABLES: Set<String> =
        setOf("schema_baseline", "databasechangelog", "databasechangeloglock")

    private val LITERAL = Regex("\"\"\"[\\s\\S]*?\"\"\"|\"(?:\\\\.|[^\"\\\\\\n])*\"")

    /**
     * Le verbe est **ancré en tête** de la chaîne. Le chercher n'importe où produisait des
     * faux positifs sur du français ordinaire — « un UPDATE sans affectation n'a pas de
     * sens » se lisait comme une requête sur une table nommée `sans`. Une requête SQL
     * commence par son verbe ; une phrase, non.
     */
    private val VERB =
        Regex("^(select|insert\\s+into|update|delete\\s+from|merge\\s+into|with)\\b", RegexOption.IGNORE_CASE)

    private val TABLE_REFERENCE =
        Regex("\\b(?:from|join|into|update)\\s+(?:only\\s+)?\"?([a-z_][a-z0-9_]*)\"?", RegexOption.IGNORE_CASE)

    private const val TENANT_COLUMN = "tenant_id"

    /** Analyse un contenu de source Kotlin. [origin] ne sert qu'au message d'erreur. */
    fun scanSource(
        origin: String,
        source: String,
    ): List<UnscopedSqlLiteral> =
        LITERAL
            .findAll(source)
            .map { it.value.trim('"').trim() }
            .filter { VERB.containsMatchIn(it) }
            .flatMap { query -> unscopedTablesIn(query).map { UnscopedSqlLiteral(origin, it, query.condense()) } }
            .toList()

    /** Analyse toutes les sources Kotlin de production sous [backendRoot]. */
    fun scanProductionSources(backendRoot: Path): List<UnscopedSqlLiteral> =
        Files
            .walk(backendRoot)
            .use { paths ->
                paths
                    .filter { it.extension == "kt" && it.isMainSource() }
                    .map { scanSource(backendRoot.relativize(it).toString(), it.readText()) }
                    .toList()
            }.flatten()

    private fun unscopedTablesIn(query: String): List<String> {
        if (query.contains(TENANT_COLUMN, ignoreCase = true)) return emptyList()
        return TABLE_REFERENCE
            .findAll(query)
            .map { it.groupValues[1].lowercase() }
            .filterNot { it in UNSCOPED_TABLES }
            .distinct()
            .toList()
    }

    private fun Path.isMainSource(): Boolean = toString().replace('\\', '/').contains("/src/main/kotlin/")

    private fun String.condense(): String = replace(Regex("\\s+"), " ").trim().take(MAX_EXCERPT)

    private const val MAX_EXCERPT = 160
}
