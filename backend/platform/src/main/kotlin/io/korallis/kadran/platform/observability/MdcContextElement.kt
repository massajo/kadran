package io.korallis.kadran.platform.observability

import kotlinx.coroutines.ThreadContextElement
import org.slf4j.MDC
import kotlin.coroutines.CoroutineContext

/**
 * Porte le MDC à travers les coroutines, pendant du `TenantContextElement`.
 *
 * Sans lui, `correlation_id` disparaît des logs dès le premier changement de dispatcher :
 * la trace de bout en bout promise en §8.4 s'arrête au premier `withContext`.
 */
class MdcContextElement(
    private val snapshot: Map<String, String>?,
) : ThreadContextElement<Map<String, String>?> {
    override val key: CoroutineContext.Key<MdcContextElement> get() = Key

    override fun updateThreadContext(context: CoroutineContext): Map<String, String>? {
        val previous = MDC.getCopyOfContextMap()
        writeMdc(snapshot)
        return previous
    }

    override fun restoreThreadContext(
        context: CoroutineContext,
        oldState: Map<String, String>?,
    ) {
        writeMdc(oldState)
    }

    private fun writeMdc(state: Map<String, String>?) {
        if (state == null) MDC.clear() else MDC.setContextMap(state)
    }

    companion object Key : CoroutineContext.Key<MdcContextElement>
}

/**
 * Capture le MDC du thread appelant pour le transporter dans une coroutine :
 * `withContext(Dispatchers.IO + currentTenantContext() + currentDiagnosticContext()) { ... }`.
 */
fun currentDiagnosticContext(): MdcContextElement = MdcContextElement(MDC.getCopyOfContextMap())
