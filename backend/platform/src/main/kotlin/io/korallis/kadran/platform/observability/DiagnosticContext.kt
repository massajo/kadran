package io.korallis.kadran.platform.observability

import io.korallis.kadran.platform.tenancy.TenantId
import org.slf4j.MDC

/**
 * Pose et nettoie les clés de diagnostic du MDC (spec §8.4).
 *
 * Volontairement **indépendant de HTTP** : le filtre servlet n'est que le premier appelant.
 * Un consommateur de messages, un job de recalcul ou un rejeu d'import devront corréler
 * leurs logs de la même façon, et doivent pouvoir le faire sans requête à porter.
 *
 * Les noms de clés sont figés et en `snake_case` : ils deviendront des champs de logs
 * structurés, et une clé renommée casse toutes les recherches déjà écrites.
 *
 * **Aucune PII n'entre ici** (spec §8.1). Les logs partent vers un agrégateur externe ;
 * un `tenant_id` en UUID opaque y est acceptable, une adresse, un SIREN, un nom ou un
 * e-mail ne le sont pas.
 */
object DiagnosticContext {
    const val CORRELATION_ID_KEY: String = "correlation_id"
    const val TENANT_ID_KEY: String = "tenant_id"

    /**
     * Exécute [block] avec les clés de diagnostic posées, puis restaure **l'intégralité** du
     * MDC antérieur — y compris si [block] lève.
     *
     * Restaurer l'état complet plutôt que retirer les deux clés : le MDC appartient au
     * thread, pas à l'appelant, et un appel imbriqué ne doit rien effacer de son englobant.
     *
     * @param tenantId `null` pour un traitement sans tenant établi. La clé est alors retirée,
     *   jamais laissée à la valeur d'un traitement précédent sur le même thread.
     */
    fun <T> within(
        correlationId: CorrelationId,
        tenantId: TenantId?,
        block: () -> T,
    ): T {
        val previous = MDC.getCopyOfContextMap()
        MDC.put(CORRELATION_ID_KEY, correlationId.value)
        replaceTenantId(tenantId)
        return try {
            block()
        } finally {
            restore(previous)
        }
    }

    private fun replaceTenantId(tenantId: TenantId?) {
        if (tenantId == null) MDC.remove(TENANT_ID_KEY) else MDC.put(TENANT_ID_KEY, tenantId.value.toString())
    }

    private fun restore(previous: Map<String, String>?) {
        if (previous == null) MDC.clear() else MDC.setContextMap(previous)
    }
}
