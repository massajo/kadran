package io.korallis.kadran.platform.persistence

import io.korallis.kadran.platform.tenancy.MissingTenantContextException
import io.korallis.kadran.platform.tenancy.TenantContext
import io.korallis.kadran.platform.tenancy.TenantId
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.DeleteConditionStep
import org.jooq.Field
import org.jooq.InsertOnDuplicateStep
import org.jooq.Record
import org.jooq.SelectConditionStep
import org.jooq.SelectFieldOrAsterisk
import org.jooq.UpdateConditionStep

/**
 * Seul chemin d'accès à la base pour un repository (`CLAUDE.md` §2.3, spec §9.1 contrôle 1).
 *
 * L'ADR-001 écarte le Row-Level Security : l'isolation devient une propriété du code et un
 * prédicat oublié expose les données d'un autre exploitant, sans filet en dessous. D'où deux
 * partis pris structurels, dont aucun n'est une commodité :
 *
 * - **Le [TenantId] est exigé à la construction, pas en paramètre de méthode.** Un paramètre
 *   s'oublie, se met à `null`, se copie-colle depuis l'appel voisin ; un constructeur, non.
 *   Il n'existe aucune instance de ce type qui ne connaisse pas son tenant.
 * - **Chaque méthode rend une étape déjà filtrée** — `SelectConditionStep`,
 *   `UpdateConditionStep`, `DeleteConditionStep`. Le prédicat n'est pas *ajoutable* par
 *   l'appelant : il est déjà là. L'appelant ne peut qu'ajouter ses propres critères par
 *   `and(...)`, jamais retirer celui du tenant.
 *
 * Il n'existe volontairement **aucun accesseur** vers le [DSLContext] sous-jacent ni vers le
 * [TenantId] : les exposer rouvrirait exactement le trou que ce type ferme. Un besoin non
 * couvert — jointure analytique, fenêtrage — s'ajoute *ici*, il ne se contourne pas.
 *
 * Une instance vaut pour un tenant et pour la durée d'un traitement : elle se construit au
 * point d'entrée du repository, jamais en champ d'un singleton Spring, qui servirait le
 * tenant de la première requête à toutes les suivantes.
 */
class TenantScopedQuery private constructor(
    private val tenantId: TenantId,
    private val delegate: DSLContext,
) {
    /**
     * `SELECT * FROM <table> WHERE tenant_id = ?`.
     *
     * Le retour est un [SelectConditionStep] : le `WHERE` est consommé, l'appelant enchaîne
     * en `and(...)`.
     */
    fun <R : Record> selectFrom(table: TenantScopedTable<R>): SelectConditionStep<R> =
        delegate
            .selectFrom(table.table)
            .where(scopeOf(table))

    /** `SELECT <fields> FROM <table> WHERE tenant_id = ?`, pour une projection. */
    fun <R : Record> select(
        table: TenantScopedTable<R>,
        fields: List<SelectFieldOrAsterisk>,
    ): SelectConditionStep<Record> {
        require(fields.isNotEmpty()) { "une projection vide n'a pas de sens : utiliser selectFrom" }
        return delegate
            .select(fields)
            .from(table.table)
            .where(scopeOf(table))
    }

    /**
     * `INSERT INTO <table> (…, tenant_id) VALUES (…, ?)`.
     *
     * Le `tenant_id` est **posé par la requête**, pas par l'appelant. C'est la symétrie de la
     * lecture : une écriture dont le tenant serait un champ parmi d'autres finirait par être
     * insérée sans lui, ou avec celui d'un objet mal recopié.
     *
     * @throws IllegalArgumentException si [values] porte déjà un `tenant_id` différent de
     *   celui du contexte — écraser silencieusement masquerait une tentative d'écriture
     *   croisée, qui doit au contraire faire du bruit (spec §9.1 contrôle 4).
     */
    fun <R : Record> insertInto(
        table: TenantScopedTable<R>,
        values: Map<Field<*>, Any?>,
    ): InsertOnDuplicateStep<R> {
        val supplied = values[table.tenantId]
        require(supplied == null || supplied == tenantId.value) {
            "tentative d'ecriture sur le tenant $supplied depuis le contexte $tenantId"
        }
        val scoped = LinkedHashMap<Field<*>, Any?>(values)
        scoped[table.tenantId] = tenantId.value
        return delegate.insertInto(table.table).set(scoped)
    }

    /**
     * `UPDATE <table> SET … WHERE tenant_id = ?`.
     *
     * Les valeurs sont passées en argument plutôt que rendues par un `UpdateSetStep` : en
     * jOOQ le `WHERE` vient après le `SET`, donc rendre une étape « avant `SET` » laisserait
     * l'appelant maître du `WHERE` — c'est-à-dire libre de l'oublier.
     *
     * @throws IllegalArgumentException si [values] prétend déplacer la ligne vers un autre
     *   tenant.
     */
    fun <R : Record> update(
        table: TenantScopedTable<R>,
        values: Map<Field<*>, Any?>,
    ): UpdateConditionStep<R> {
        require(values.isNotEmpty()) { "un UPDATE sans affectation n'a pas de sens" }
        require(!values.containsKey(table.tenantId)) {
            "le tenant_id d'une ligne ne se modifie pas : ce serait un transfert entre exploitants"
        }
        return delegate
            .update(table.table)
            .set(values)
            .where(scopeOf(table))
    }

    /** `DELETE FROM <table> WHERE tenant_id = ?`. */
    fun <R : Record> deleteFrom(table: TenantScopedTable<R>): DeleteConditionStep<R> =
        delegate
            .deleteFrom(table.table)
            .where(scopeOf(table))

    /**
     * Prédicat d'isolation d'une table, pour composer une requête multi-tables.
     *
     * Exposé parce qu'une jointure exige **un prédicat par table jointe** : sans lui, la
     * seule issue serait de récupérer le [DSLContext], ce qui est interdit. Ce point d'entrée
     * ne donne accès à aucune capacité d'exécution — il rend une [Condition], pas une
     * requête.
     */
    fun <R : Record> scopeOf(table: TenantScopedTable<R>): Condition = table.tenantId.eq(tenantId.value)

    companion object {
        /**
         * Requête servie dans le fil d'une requête HTTP : le tenant vient du contexte établi
         * par le filtre servlet (spec §9.2).
         *
         * La spec esquissait `of(context: TenantContext, delegate)` ; `TenantContext` est un
         * `object` et non un collaborateur injectable, l'argument n'aurait rien porté.
         *
         * @throws MissingTenantContextException si aucun tenant n'est établi — échouer est la
         *   seule issue sûre, une requête sans tenant lirait tout.
         */
        fun forCurrentTenant(delegate: DSLContext): TenantScopedQuery =
            TenantScopedQuery(TenantContext.requireTenantId(), delegate)

        /**
         * Requête d'un traitement asynchrone, qui reçoit son tenant explicitement en
         * paramètre et n'hérite jamais du contexte d'un thread de pool (spec §9.2).
         */
        fun forTenant(
            tenantId: TenantId,
            delegate: DSLContext,
        ): TenantScopedQuery = TenantScopedQuery(tenantId, delegate)
    }
}
