package io.korallis.kadran.platform.tenancy

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Un `ThreadLocal` ne suit pas une coroutine : ces cas prouvent que le tenant traverse bien
 * un changement de dispatcher, et — tout aussi important — qu'il ne reste pas collé au
 * thread emprunté (spec §9.2).
 */
class TenantContextElementTest :
    StringSpec({
        val tenantA = TenantId(UUID.fromString("33333333-3333-3333-3333-333333333333"))

        "le tenant survit a un changement de dispatcher" {
            val appelant = Thread.currentThread()

            TenantContext.withTenant(tenantA) {
                runBlocking(currentTenantContext()) {
                    withContext(Dispatchers.IO) {
                        Thread.currentThread() shouldNotBe appelant
                        TenantContext.requireTenantId() shouldBe tenantA
                    }
                }
            }
        }

        "sans l'element, le tenant ne franchit pas le changement de dispatcher" {
            TenantContext.withTenant(tenantA) {
                runBlocking {
                    withContext(Dispatchers.IO) {
                        shouldThrow<MissingTenantContextException> { TenantContext.requireTenantId() }
                    }
                }
            }
        }

        "la coroutine ne laisse pas son tenant sur le thread du pool" {
            val executor = Executors.newSingleThreadExecutor()
            val dispatcher = executor.asCoroutineDispatcher()
            try {
                runBlocking {
                    val premierThread =
                        withContext(dispatcher + TenantContextElement(tenantA)) {
                            TenantContext.requireTenantId() shouldBe tenantA
                            Thread.currentThread()
                        }

                    // Même thread, travail suivant : il ne doit rien avoir hérité.
                    withContext(dispatcher) {
                        Thread.currentThread() shouldBe premierThread
                        TenantContext.currentOrNull() shouldBe null
                    }
                }
            } finally {
                dispatcher.close()
                executor.shutdownNow()
            }
        }

        "currentTenantContext capture aussi l'absence de tenant" {
            val executor = Executors.newSingleThreadExecutor()
            val dispatcher = executor.asCoroutineDispatcher()
            try {
                runBlocking {
                    // Thread du pool sali volontairement, sans rien pour le nettoyer.
                    withContext(dispatcher) { TenantContext.replace(tenantA) }
                    withContext(dispatcher) { TenantContext.currentOrNull() shouldBe tenantA }

                    // Le thread appelant n'a pas de tenant : l'element propage cette absence
                    // plutot que de laisser la coroutine heriter du residu du pool.
                    withContext(dispatcher + currentTenantContext()) {
                        TenantContext.currentOrNull() shouldBe null
                    }

                    withContext(dispatcher) { TenantContext.replace(null) }
                }
            } finally {
                dispatcher.close()
                executor.shutdownNow()
            }
        }
    })
