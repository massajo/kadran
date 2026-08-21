package io.korallis.kadran.platform.persistence

import org.jooq.Field
import org.jooq.Record
import org.jooq.Table
import org.jooq.impl.DSL
import java.util.UUID

/**
 * Table métier porteuse d'un `tenant_id` (spec §9.1 contrôle 1, §9.3).
 *
 * Ce type n'existe pas pour décrire une table — jOOQ le fait déjà — mais pour **rendre
 * indicible** l'oubli du prédicat d'isolation. [TenantScopedQuery] n'accepte que des
 * `TenantScopedTable` : une table qu'on n'a pas déclarée scopée n'est pas interrogeable par
 * le chemin autorisé, et le développeur n'a jamais à se souvenir d'ajouter un `WHERE`.
 * Depuis l'abandon du RLS (ADR-001), c'est le typage qui porte la garantie que PostgreSQL ne
 * porte plus.
 *
 * La colonne est un [Field] et non un `TableField` à dessein : les tables se déclarent
 * aujourd'hui à la main via [named], la génération de code jOOQ n'étant pas encore en place.
 * Une table générée reste libre d'implémenter cette interface en exposant son propre
 * `TableField`, qui est un [Field].
 */
interface TenantScopedTable<R : Record> {
    /** La table jOOQ sous-jacente. */
    val table: Table<R>

    /**
     * La colonne `tenant_id`, **qualifiée** par [table] — une requête analytique joignant
     * deux tables scopées porte deux colonnes de même nom, et un prédicat ambigu ne protège
     * personne.
     */
    val tenantId: Field<UUID>

    companion object {
        /** Nom de la colonne d'isolation, identique sur toutes les tables métier (spec §9.3). */
        const val TENANT_ID_COLUMN: String = "tenant_id"

        /**
         * Déclare une table scopée par son nom.
         *
         * Le nom passe par `DSL.name` : il est traité comme un identifiant, jamais comme du
         * SQL. Ce point d'entrée ne peut donc pas servir à injecter un fragment de requête.
         */
        fun named(name: String): TenantScopedTable<Record> {
            require(name.isNotBlank()) { "le nom d'une table scopee ne peut pas etre vide" }
            return NamedTenantScopedTable(
                table = DSL.table(DSL.name(name)),
                tenantId = DSL.field(DSL.name(name, TENANT_ID_COLUMN), UUID::class.java),
            )
        }
    }
}

/**
 * Table déclarée à la main, en attendant la génération de code jOOQ.
 *
 * Privée : on passe par [TenantScopedTable.named], qui garantit que la colonne d'isolation
 * appartient bien à la table déclarée et non à une autre.
 */
private data class NamedTenantScopedTable(
    override val table: Table<Record>,
    override val tenantId: Field<UUID>,
) : TenantScopedTable<Record>
