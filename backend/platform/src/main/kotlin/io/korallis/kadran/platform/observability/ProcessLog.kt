package io.korallis.kadran.platform.observability

import org.slf4j.Logger

/**
 * Encadre un **traitement notable** d'une ligne d'entrée et d'une ligne de sortie (spec
 * §10.7.1) : import d'un lot, application d'un profil de mapping, recalcul de métriques,
 * consommation d'outbox.
 *
 * Les deux lignes portent le `correlation_id` du déclencheur, parce qu'elles sortent sous le
 * MDC ambiant. Un traitement asynchrone n'en hérite pas tout seul : il le rétablit d'abord.
 *
 * ```kotlin
 * DiagnosticContext.within(correlationId, tenantId) {
 *     ProcessLog(logger).around("ingestion.import.batch") { importer(lot) }
 * }
 * ```
 *
 * Les champs sont posés en paires clé/valeur SLF4J, pas concaténés dans le message : le
 * format JSON structuré les publie tels quels, et une recherche en aval porte sur un champ,
 * pas sur une expression rationnelle appliquée à une phrase.
 *
 * **Aucun contenu métier ne passe par ici** (spec §10.7.1). En cas d'échec, seule la classe
 * de l'exception est journalisée : son message porte souvent un nom de fichier, une adresse
 * ou un identifiant de contrepartie, et un log survit à l'effacement du compte qui l'a
 * produit. L'exception est relancée telle quelle — c'est à l'appelant de la traiter.
 */
class ProcessLog(
    private val logger: Logger,
) {
    @Suppress("TooGenericExceptionCaught")
    fun <T> around(
        process: String,
        block: () -> T,
    ): T {
        logger
            .atInfo()
            .addKeyValue(PROCESS_KEY, process)
            .setMessage(STARTED)
            .log()

        val startedAt = System.nanoTime()
        return try {
            val result = block()
            finished(process, startedAt, OUTCOME_SUCCESS, null)
            result
        } catch (failure: Throwable) {
            finished(process, startedAt, OUTCOME_FAILURE, failure)
            throw failure
        }
    }

    private fun finished(
        process: String,
        startedAt: Long,
        outcome: String,
        failure: Throwable?,
    ) {
        val builder = if (failure == null) logger.atInfo() else logger.atWarn()
        builder
            .addKeyValue(PROCESS_KEY, process)
            .addKeyValue(OUTCOME_KEY, outcome)
            .addKeyValue(DURATION_KEY, elapsedMillis(startedAt))
            .also { if (failure != null) it.addKeyValue(ERROR_TYPE_KEY, failure.javaClass.name) }
            .setMessage(FINISHED)
            .log()
    }

    private fun elapsedMillis(startedAt: Long): Long = (System.nanoTime() - startedAt) / NANOS_PER_MILLI

    companion object {
        const val PROCESS_KEY = "process"
        const val OUTCOME_KEY = "outcome"
        const val DURATION_KEY = "duration_ms"
        const val ERROR_TYPE_KEY = "error_type"
        const val OUTCOME_SUCCESS = "success"
        const val OUTCOME_FAILURE = "failure"
        const val STARTED = "process started"
        const val FINISHED = "process finished"
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
