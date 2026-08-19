package io.korallis.kadran.platform.tenancy

import kotlinx.coroutines.ThreadContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Porte le tenant courant à travers les coroutines (spec §9.2).
 *
 * Un `ThreadLocal` ne suit pas une coroutine : dès qu'elle reprend sur un autre thread —
 * changement de dispatcher, suspension, `async` — le contexte est perdu et
 * [TenantContext.requireTenantId] lève. Cet élément repose le tenant à chaque reprise et
 * **restaure l'état antérieur du thread** à chaque suspension : sans cela, une coroutine
 * empruntant un thread de `Dispatchers.IO` y abandonnerait son tenant pour le travail
 * suivant.
 */
class TenantContextElement(
    private val tenantId: TenantId?,
) : ThreadContextElement<TenantId?> {
    override val key: CoroutineContext.Key<TenantContextElement> get() = Key

    override fun updateThreadContext(context: CoroutineContext): TenantId? {
        val previous = TenantContext.currentOrNull()
        TenantContext.replace(tenantId)
        return previous
    }

    override fun restoreThreadContext(
        context: CoroutineContext,
        oldState: TenantId?,
    ) {
        TenantContext.replace(oldState)
    }

    companion object Key : CoroutineContext.Key<TenantContextElement>
}

/**
 * Capture le tenant du thread appelant pour le transporter dans une coroutine :
 * `withContext(Dispatchers.IO + currentTenantContext()) { ... }`.
 *
 * Capture aussi l'**absence** de tenant, volontairement : propager « pas de tenant » vaut
 * mieux que laisser la coroutine hériter de celui d'un thread de pool.
 */
fun currentTenantContext(): TenantContextElement = TenantContextElement(TenantContext.currentOrNull())
