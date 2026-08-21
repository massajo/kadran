package io.korallis.kadran.platform.persistence

import io.korallis.kadran.platform.tenancy.MissingTenantContextException
import io.korallis.kadran.platform.tenancy.TenantContext
import io.korallis.kadran.platform.tenancy.TenantId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.jooq.Field
import org.jooq.Query
import org.jooq.SQLDialect
import org.jooq.conf.ParamType
import org.jooq.impl.DSL
import java.util.UUID

/**
 * Le contrat de `TenantScopedQuery` est le SQL qu'il produit : le prédicat `tenant_id` doit
 * être présent **sans que l'appelant l'ait écrit** (spec §9.1, contrôle 1).
 *
 * Ces cas s'exécutent sans base : `DSL.using(dialect)` est un vrai `DSLContext` qui rend du
 * SQL sans connexion. Rien n'est simulé — pas de mock, conformément à §10.4 — et la preuve
 * porte exactement sur ce qui compte, la requête émise. L'isolation réellement observée en
 * base relève du test d'isolation par table (contrôle 3, KDN-17).
 */
class TenantScopedQueryTest :
    StringSpec({
        val tenant = TenantId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
        val autreTenant = TenantId(UUID.fromString("22222222-2222-2222-2222-222222222222"))
        val dsl = DSL.using(SQLDialect.POSTGRES)
        val outing = TenantScopedTable.named("outing")
        val id: Field<UUID> = DSL.field(DSL.name("outing", "id"), UUID::class.java)
        val purpose: Field<String> = DSL.field(DSL.name("outing", "purpose"), String::class.java)

        fun sql(query: Query): String = query.getSQL(ParamType.INLINED).lowercase()

        // jOOQ rend un UUID en `cast('…' as uuid)` sur PostgreSQL : on assère le SQL tel
        // qu'il partira au pilote, pas une forme idéalisée.
        val scopeOnOuting = """"outing"."tenant_id" = cast('${tenant.value}' as uuid)"""

        "a SELECT carries the predicate without the caller writing it" {
            val query = TenantScopedQuery.forTenant(tenant, dsl).selectFrom(outing)

            sql(query) shouldContain scopeOnOuting
        }

        "a business criterion adds to the predicate, it does not replace it" {
            val query =
                TenantScopedQuery
                    .forTenant(tenant, dsl)
                    .selectFrom(outing)
                    .and(purpose.eq("BUSINESS"))

            val rendered = sql(query)
            rendered shouldContain scopeOnOuting
            rendered shouldContain "'business'"
        }

        "a projection carries the predicate" {
            val query = TenantScopedQuery.forTenant(tenant, dsl).select(outing, listOf(id, purpose))

            val rendered = sql(query)
            rendered shouldContain scopeOnOuting
            rendered shouldNotContain "select *"
        }

        "an empty projection is refused" {
            shouldThrow<IllegalArgumentException> {
                TenantScopedQuery.forTenant(tenant, dsl).select(outing, emptyList())
            }
        }

        "a DELETE carries the predicate" {
            val query = TenantScopedQuery.forTenant(tenant, dsl).deleteFrom(outing)

            sql(query) shouldContain scopeOnOuting
        }

        "an UPDATE carries the predicate" {
            val query = TenantScopedQuery.forTenant(tenant, dsl).update(outing, mapOf(purpose to "PERSONAL"))

            sql(query) shouldContain scopeOnOuting
        }

        "an UPDATE cannot move a row to another tenant" {
            shouldThrow<IllegalArgumentException> {
                TenantScopedQuery.forTenant(tenant, dsl).update(outing, mapOf(outing.tenantId to autreTenant.value))
            }
        }

        "an INSERT sets tenant_id even when the caller omits it" {
            val query = TenantScopedQuery.forTenant(tenant, dsl).insertInto(outing, mapOf(purpose to "BUSINESS"))

            val rendered = sql(query)
            rendered shouldContain "tenant_id"
            rendered shouldContain "'${tenant.value}'"
        }

        "an INSERT accepts a redundant tenant_id when it matches" {
            val values = mapOf<Field<*>, Any?>(purpose to "BUSINESS", outing.tenantId to tenant.value)
            val query = TenantScopedQuery.forTenant(tenant, dsl).insertInto(outing, values)

            sql(query) shouldContain "'${tenant.value}'"
        }

        "un INSERT vers un tenant etranger est refuse au lieu d'etre silencieusement reecrit" {
            val values = mapOf<Field<*>, Any?>(purpose to "BUSINESS", outing.tenantId to autreTenant.value)

            shouldThrow<IllegalArgumentException> {
                TenantScopedQuery.forTenant(tenant, dsl).insertInto(outing, values)
            }
        }

        "a join carries one predicate per table, qualified by its table" {
            val vehicle = TenantScopedTable.named("vehicle")
            val query = TenantScopedQuery.forTenant(tenant, dsl)

            val rendered = DSL.and(query.scopeOf(outing), query.scopeOf(vehicle)).toString().lowercase()

            rendered shouldContain """"outing"."tenant_id""""
            rendered shouldContain """"vehicle"."tenant_id""""
        }

        "forCurrentTenant takes the tenant established on the thread" {
            val rendered =
                TenantContext.withTenant(tenant) {
                    sql(TenantScopedQuery.forCurrentTenant(dsl).selectFrom(outing))
                }

            rendered shouldContain scopeOnOuting
        }

        "forCurrentTenant throws rather than produce an unscoped query" {
            shouldThrow<MissingTenantContextException> { TenantScopedQuery.forCurrentTenant(dsl) }
        }

        "forTenant n'herite pas du contexte du thread : un job porte son propre tenant" {
            val rendered =
                TenantContext.withTenant(autreTenant) {
                    sql(TenantScopedQuery.forTenant(tenant, dsl).selectFrom(outing))
                }

            rendered shouldContain scopeOnOuting
            rendered shouldNotContain autreTenant.value.toString()
        }

        "le tenant est fige a la construction : changer le contexte ensuite ne change rien" {
            val query = TenantContext.withTenant(tenant) { TenantScopedQuery.forCurrentTenant(dsl) }

            val rendered =
                TenantContext.withTenant(autreTenant) {
                    sql(query.selectFrom(outing))
                }

            rendered shouldContain scopeOnOuting
        }

        "a scoped table qualifies its isolation column" {
            outing.tenantId.qualifiedName.toString() shouldBe """"outing"."tenant_id""""
        }

        "an unnamed table is refused" {
            shouldThrow<IllegalArgumentException> { TenantScopedTable.named("  ") }
        }
    })
