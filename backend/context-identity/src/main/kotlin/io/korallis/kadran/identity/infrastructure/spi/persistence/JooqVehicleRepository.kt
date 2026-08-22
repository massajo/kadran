package io.korallis.kadran.identity.infrastructure.spi.persistence

import io.korallis.kadran.core.TenantId
import io.korallis.kadran.identity.domain.model.EnergySource
import io.korallis.kadran.identity.domain.model.OwnershipMode
import io.korallis.kadran.identity.domain.model.Plate
import io.korallis.kadran.identity.domain.model.Vehicle
import io.korallis.kadran.identity.domain.model.VehicleId
import io.korallis.kadran.identity.domain.spi.VehicleRepository
import io.korallis.kadran.identity.infrastructure.spi.persistence.IdentityTables.VEHICLE
import io.korallis.kadran.identity.infrastructure.spi.persistence.IdentityTables.VehicleColumns
import io.korallis.kadran.platform.persistence.TenantScopedQuery
import org.jooq.Field
import org.jooq.Record

/** Adaptateur `vehicle`. Le véhicule appartient à l'exploitant, pas au chauffeur (spec §9.3). */
class JooqVehicleRepository(
    private val query: TenantScopedQuery,
) : VehicleRepository {
    override fun findById(id: VehicleId): Vehicle? =
        query
            .selectFrom(VEHICLE)
            .and(VehicleColumns.ID.eq(id.value))
            .fetchOne()
            ?.toVehicle()

    override fun findAll(): List<Vehicle> =
        query
            .selectFrom(VEHICLE)
            .orderBy(VehicleColumns.PLATE.asc())
            .fetch()
            .map { it.toVehicle() }

    override fun save(vehicle: Vehicle) {
        query
            .insertInto(VEHICLE, insertValues(vehicle))
            .onConflict(VEHICLE.tenantId, VehicleColumns.ID)
            .doUpdate()
            .set(mutableValues(vehicle))
            .execute()
    }

    override fun deleteById(id: VehicleId): Boolean =
        query
            .deleteFrom(VEHICLE)
            .and(VehicleColumns.ID.eq(id.value))
            .execute() > 0

    private fun insertValues(vehicle: Vehicle): Map<Field<*>, Any?> =
        mutableValues(vehicle) +
            mapOf<Field<*>, Any?>(
                VEHICLE.tenantId to vehicle.tenantId.value,
                VehicleColumns.ID to vehicle.id.value,
            )

    private fun mutableValues(vehicle: Vehicle): Map<Field<*>, Any?> =
        mapOf(
            VehicleColumns.PLATE to vehicle.plate.value,
            VehicleColumns.ENERGY to vehicle.energy.name,
            VehicleColumns.OWNERSHIP_MODE to vehicle.ownership.name,
            VehicleColumns.FIRST_REGISTERED_ON to vehicle.firstRegisteredOn,
        )

    private fun Record.toVehicle(): Vehicle =
        Vehicle(
            id = VehicleId(read(VehicleColumns.ID)),
            tenantId = TenantId(read(VEHICLE.tenantId)),
            plate = Plate.of(read(VehicleColumns.PLATE)),
            energy = EnergySource.valueOf(read(VehicleColumns.ENERGY)),
            ownership = OwnershipMode.valueOf(read(VehicleColumns.OWNERSHIP_MODE)),
            firstRegisteredOn = read(VehicleColumns.FIRST_REGISTERED_ON),
        )
}
