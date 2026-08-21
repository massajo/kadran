package io.korallis.kadran.platform.observability

import org.slf4j.Marker
import org.slf4j.event.Level
import org.slf4j.event.LoggingEvent
import org.slf4j.helpers.LegacyAbstractLogger
import org.slf4j.spi.LoggingEventAware

/**
 * Journal d'essai qui **conserve l'événement SLF4J entier**, paires clé/valeur comprises.
 *
 * Deux raisons de ne pas passer par un `ListAppender` Logback : le module `platform` n'a
 * Logback qu'en `testRuntimeOnly`, et surtout un test qui assemble une configuration Logback
 * vérifie Logback, pas le code appelant. Ce qu'on veut savoir tient en une phrase — quels
 * champs le code a-t-il émis, sous quels noms.
 *
 * `LoggingEventAware` n'est pas décoratif : sans lui, SLF4J replie l'API fluide sur
 * `info(String, Object...)` et **perd les paires clé/valeur** en les recollant dans le
 * message. Le test passerait alors à côté de ce qu'il prétend vérifier.
 */
class RecordingLogger(
    name: String = "test",
) : LegacyAbstractLogger(),
    LoggingEventAware {
    private val recorded = mutableListOf<LoggingEvent>()

    init {
        this.name = name
    }

    val events: List<LoggingEvent> get() = recorded.toList()

    /** Paires clé/valeur de l'événement [index], à plat. */
    fun keyValuesOf(index: Int): Map<String, Any?> =
        recorded[index].keyValuePairs.orEmpty().associate { it.key to it.value }

    override fun log(event: LoggingEvent) {
        recorded += event
    }

    override fun getFullyQualifiedCallerName(): String? = null

    override fun handleNormalizedLoggingCall(
        level: Level?,
        marker: Marker?,
        messagePattern: String?,
        arguments: Array<out Any>?,
        throwable: Throwable?,
    ) = error("l'API fluide de SLF4J doit passer par log(LoggingEvent) : voir LoggingEventAware")

    override fun isTraceEnabled(): Boolean = true

    override fun isDebugEnabled(): Boolean = true

    override fun isInfoEnabled(): Boolean = true

    override fun isWarnEnabled(): Boolean = true

    override fun isErrorEnabled(): Boolean = true
}
