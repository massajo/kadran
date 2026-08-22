package io.korallis.kadran.identity.domain.model

import io.korallis.kadran.core.DriverId
import io.korallis.kadran.core.TenantId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.util.UUID

/** Chauffeur et véhicule : les deux agrégats que la flotte fera passer de un à plusieurs. */
class DriverAndVehicleTest :
    StringSpec({
        val tenantId = TenantId.of("11111111-1111-1111-1111-111111111111")
        val driverId = DriverId.of("33333333-3333-3333-3333-333333333333")

        "a driver belongs to the tenant that registered it" {
            val driver = Driver.register(driverId, tenantId, DriverName.of("Jean Dupont"))

            driver.id shouldBe driverId
            driver.tenantId shouldBe tenantId
            driver.displayName shouldBe DriverName.of("Jean Dupont")
        }

        "renaming a driver keeps its identity" {
            val driver = Driver.register(driverId, tenantId, DriverName.of("Jean Dupont"))

            val renamed = driver.renameTo(DriverName.of("Jean Dupond"))

            renamed.id shouldBe driver.id
            renamed.displayName shouldBe DriverName.of("Jean Dupond")
            (renamed == driver) shouldBe false
            driver.toString().contains("Jean Dupont") shouldBe true
        }

        "identifiers minted for two drivers differ" {
            (DriverId.next() == DriverId.next()) shouldBe false
            DriverId.next().toString().length shouldBe UUID.randomUUID().toString().length
        }

        "a plate is normalised so one vehicle is not registered twice" {
            Plate.of("aa-123-aa") shouldBe Plate.of("AA 123 AA")
            Plate.of("AA-123-AA").value shouldBe "AA123AA"
            Plate.of("AA-123-AA").toString() shouldBe "AA123AA"
        }

        "a foreign or pre-2009 plate is accepted, since no format is imposed" {
            Plate.of("123 ABC 75").value shouldBe "123ABC75"
            Plate.of("B-MW 1234").value shouldBe "BMW1234"
        }

        "a blank or oversized plate is rejected" {
            shouldThrow<IllegalArgumentException> { Plate.of(" - ") }
            shouldThrow<IllegalArgumentException> { Plate.of("A".repeat(Plate.MAX_LENGTH + 1)) }
        }

        "an owned vehicle is amortizable, a leased one is not" {
            fun vehicleWith(mode: OwnershipMode) =
                Vehicle(
                    id = VehicleId.next(),
                    tenantId = tenantId,
                    plate = Plate.of("AA-123-AA"),
                    energy = EnergySource.ELECTRIC,
                    ownership = mode,
                    firstRegisteredOn = LocalDate.of(2024, 3, 12),
                )

            vehicleWith(OwnershipMode.OWNED_OUTRIGHT).isAmortizable shouldBe true
            vehicleWith(OwnershipMode.OWNED_FINANCED).isAmortizable shouldBe true
            vehicleWith(OwnershipMode.LEASE_LLD).isAmortizable shouldBe false
            vehicleWith(OwnershipMode.LEASE_LOA).isAmortizable shouldBe false
            vehicleWith(OwnershipMode.RENTAL).isAmortizable shouldBe false
        }

        "a vehicle keeps the tenant it was declared under" {
            val vehicleId = VehicleId(UUID.fromString("77777777-7777-7777-7777-777777777777"))
            val vehicle =
                Vehicle(
                    id = vehicleId,
                    tenantId = tenantId,
                    plate = Plate.of("AA-123-AA"),
                    energy = EnergySource.DIESEL,
                    ownership = OwnershipMode.LEASE_LLD,
                    firstRegisteredOn = LocalDate.of(2024, 3, 12),
                )

            vehicle.tenantId shouldBe tenantId
            vehicleId.toString() shouldBe "77777777-7777-7777-7777-777777777777"
            (VehicleId.next() == VehicleId.next()) shouldBe false
            vehicle.copy(energy = EnergySource.HYBRID).energy shouldBe EnergySource.HYBRID
            vehicle.toString().contains("AA123AA") shouldBe true
        }

        "the persisted vocabularies match the database check constraints" {
            EnergySource.entries.map { it.name } shouldBe
                listOf("DIESEL", "PETROL", "HYBRID", "PLUG_IN_HYBRID", "ELECTRIC", "LPG")
            OwnershipMode.entries.map { it.name } shouldBe
                listOf("OWNED_OUTRIGHT", "OWNED_FINANCED", "LEASE_LOA", "LEASE_LLD", "RENTAL")
            OnboardingStatus.entries.map { it.name } shouldBe
                listOf("IDENTITY", "FISCAL_PROFILE", "VEHICLE", "COST_MODEL", "FIRST_IMPORT", "COMPLETED")
        }
    })
