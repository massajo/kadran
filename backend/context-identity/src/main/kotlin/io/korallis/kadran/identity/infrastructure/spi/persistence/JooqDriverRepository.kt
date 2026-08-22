package io.korallis.kadran.identity.infrastructure.spi.persistence

import io.korallis.kadran.core.DriverId
import io.korallis.kadran.core.TenantId
import io.korallis.kadran.identity.domain.model.Driver
import io.korallis.kadran.identity.domain.model.DriverName
import io.korallis.kadran.identity.domain.spi.DriverRepository
import io.korallis.kadran.identity.infrastructure.spi.persistence.IdentityTables.DRIVER
import io.korallis.kadran.identity.infrastructure.spi.persistence.IdentityTables.DriverColumns
import io.korallis.kadran.platform.persistence.TenantScopedQuery
import org.jooq.Field
import org.jooq.Record

/**
 * Adaptateur `driver`.
 *
 * `findAll()` veut dire « tous les chauffeurs de cet exploitant » — la requête ne sait pas
 * dire autre chose. C'est le premier critère d'acceptation de l'issue, et il est tenu par la
 * construction plutôt que par un prédicat qu'on aurait pu oublier d'écrire.
 */
class JooqDriverRepository(
    private val query: TenantScopedQuery,
) : DriverRepository {
    override fun findById(id: DriverId): Driver? =
        query
            .selectFrom(DRIVER)
            .and(DriverColumns.ID.eq(id.value))
            .fetchOne()
            ?.toDriver()

    override fun findAll(): List<Driver> =
        query
            .selectFrom(DRIVER)
            .orderBy(DriverColumns.DISPLAY_NAME.asc())
            .fetch()
            .map { it.toDriver() }

    override fun save(driver: Driver) {
        query
            .insertInto(DRIVER, insertValues(driver))
            .onConflict(DRIVER.tenantId, DriverColumns.ID)
            .doUpdate()
            .set(mutableValues(driver))
            .execute()
    }

    override fun deleteById(id: DriverId): Boolean =
        query
            .deleteFrom(DRIVER)
            .and(DriverColumns.ID.eq(id.value))
            .execute() > 0

    private fun insertValues(driver: Driver): Map<Field<*>, Any?> =
        mutableValues(driver) +
            mapOf<Field<*>, Any?>(
                DRIVER.tenantId to driver.tenantId.value,
                DriverColumns.ID to driver.id.value,
            )

    private fun mutableValues(driver: Driver): Map<Field<*>, Any?> =
        mapOf(DriverColumns.DISPLAY_NAME to driver.displayName.value)

    private fun Record.toDriver(): Driver =
        Driver(
            id = DriverId(read(DriverColumns.ID)),
            tenantId = TenantId(read(DRIVER.tenantId)),
            displayName = DriverName.of(read(DriverColumns.DISPLAY_NAME)),
        )
}
