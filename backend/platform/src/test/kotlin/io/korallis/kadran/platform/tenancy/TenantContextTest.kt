package io.korallis.kadran.platform.tenancy

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.UUID

/** Échec provoqué par un test, distinct de tout ce que le code de production peut lever. */
private class TestFailure : RuntimeException("echec deliberement provoque")

/**
 * Le contexte de tenant est le seul garde-fou d'isolation depuis l'abandon du RLS
 * (spec §9.1) : ces cas décrivent ce qu'il doit garantir, thread par thread.
 */
class TenantContextTest :
    StringSpec({
        val tenantA = TenantId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
        val tenantB = TenantId(UUID.fromString("22222222-2222-2222-2222-222222222222"))

        "requireTenantId leve quand aucun tenant n'est etabli" {
            shouldThrow<MissingTenantContextException> { TenantContext.requireTenantId() }
        }

        "withTenant expose le tenant puis rend le thread vierge" {
            TenantContext.withTenant(tenantA) {
                TenantContext.requireTenantId() shouldBe tenantA
                TenantContext.isEstablished() shouldBe true
            }

            TenantContext.isEstablished() shouldBe false
            shouldThrow<MissingTenantContextException> { TenantContext.requireTenantId() }
        }

        "le tenant est nettoye meme si le bloc leve" {
            shouldThrow<TestFailure> {
                TenantContext.withTenant(tenantA) { throw TestFailure() }
            }

            TenantContext.isEstablished() shouldBe false
        }

        "un bloc imbrique restaure le tenant de son englobant" {
            TenantContext.withTenant(tenantA) {
                TenantContext.withTenant(tenantB) {
                    TenantContext.requireTenantId() shouldBe tenantB
                }
                TenantContext.requireTenantId() shouldBe tenantA
            }
        }

        "un bloc sans tenant masque celui de son englobant au lieu d'en heriter" {
            TenantContext.withTenant(tenantA) {
                TenantContext.withOptionalTenant(null) {
                    shouldThrow<MissingTenantContextException> { TenantContext.requireTenantId() }
                }
                TenantContext.requireTenantId() shouldBe tenantA
            }
        }

        "le tenant reste confine a son thread" {
            TenantContext.withTenant(tenantA) {
                var vuAilleurs: TenantId? = tenantA
                val autreThread = Thread { vuAilleurs = TenantContext.currentOrNull() }
                autreThread.start()
                autreThread.join()

                vuAilleurs shouldBe null
            }
        }
    })
