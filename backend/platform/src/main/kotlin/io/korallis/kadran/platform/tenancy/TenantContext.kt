package io.korallis.kadran.platform.tenancy

/**
 * Tenant courant du thread d'exécution (spec §9.2).
 *
 * Depuis l'abandon du RLS (ADR-001, spec §9.1), ce contexte est le **seul** garde-fou entre
 * deux exploitants : aucune policy PostgreSQL ne rattrapera un prédicat oublié. D'où deux
 * partis pris qui ne se négocient pas :
 *
 * - [requireTenantId] lève au lieu de retourner `null`. Pas de valeur par défaut, pas de
 *   `null` silencieux qu'un appelant distrait laisserait filer jusqu'à une requête sans
 *   `WHERE tenant_id`.
 * - L'établissement du contexte passe exclusivement par [withTenant], dont le `finally`
 *   restaure l'état précédent. Un `ThreadLocal` posé sans être nettoyé fuite d'une requête
 *   vers la suivante dès que le conteneur réutilise le thread — le pire défaut possible ici,
 *   car il est silencieux et sert des données étrangères à un utilisateur légitime.
 *
 * Les jobs asynchrones ne **doivent pas** hériter de ce contexte : ils reçoivent leur
 * [TenantId] en paramètre et ouvrent leur propre [withTenant]. La propagation vers les
 * coroutines ([TenantContextElement]) ne sert qu'à ne pas perdre le contexte d'une requête
 * en cours au fil des changements de dispatcher.
 */
object TenantContext {
    private val holder = ThreadLocal<TenantId?>()

    /**
     * Tenant courant.
     *
     * @throws MissingTenantContextException si aucun tenant n'est établi.
     */
    fun requireTenantId(): TenantId = holder.get() ?: throw MissingTenantContextException()

    /** Vrai si un tenant est établi. Sert aux garde-fous, jamais à choisir un repli. */
    fun isEstablished(): Boolean = holder.get() != null

    /**
     * Exécute [block] avec [tenantId] pour tenant courant, puis restaure l'état précédent —
     * y compris si [block] lève.
     */
    fun <T> withTenant(
        tenantId: TenantId,
        block: () -> T,
    ): T = withOptionalTenant(tenantId, block)

    /**
     * Variante interne acceptant l'absence de tenant : la couche web l'utilise pour les
     * requêtes non authentifiées, qui doivent s'exécuter **sans** tenant plutôt qu'avec
     * celui laissé par la requête précédente sur le même thread.
     */
    internal fun <T> withOptionalTenant(
        tenantId: TenantId?,
        block: () -> T,
    ): T {
        val previous = holder.get()
        replace(tenantId)
        return try {
            block()
        } finally {
            replace(previous)
        }
    }

    internal fun currentOrNull(): TenantId? = holder.get()

    /**
     * `remove()` plutôt que `set(null)` : laisser une entrée vide dans la table du thread
     * la ferait survivre au thread du pool sans raison.
     */
    internal fun replace(tenantId: TenantId?) {
        if (tenantId == null) holder.remove() else holder.set(tenantId)
    }
}
