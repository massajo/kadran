package io.korallis.kadran.identity.domain.model

import io.korallis.kadran.core.TenantId
import java.time.LocalDate
import java.util.UUID

/** Identifiant d'un véhicule, unique au sein d'un exploitant. */
@JvmInline
value class VehicleId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()

    companion object {
        fun next(): VehicleId = VehicleId(UUID.randomUUID())
    }
}

/**
 * Immatriculation, normalisée en majuscules sans séparateur.
 *
 * **Aucun format n'est imposé.** Le SIV français (`AA-123-AA`), l'ancien FNI (`123 ABC 75`)
 * et les plaques étrangères coexistent dans la vraie vie ; un contrôle calqué sur le seul SIV
 * rejetterait des véhicules parfaitement légitimes, et forcerait l'utilisateur à mentir pour
 * passer l'étape 3 de l'onboarding. Ce qui est normalisé, c'est la *forme* — de sorte que
 * `AA-123-AA` et `aa 123 aa` ne créent pas deux véhicules.
 */
@JvmInline
value class Plate private constructor(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH: Int = 16

        /** @throws IllegalArgumentException si l'immatriculation est vide ou trop longue. */
        fun of(raw: String): Plate {
            val normalized = raw.filterNot { it.isWhitespace() || it == '-' }.uppercase()
            require(normalized.isNotEmpty()) { "une immatriculation ne peut pas etre vide" }
            require(normalized.length <= MAX_LENGTH) {
                "une immatriculation depasse $MAX_LENGTH caracteres : $raw"
            }
            return Plate(normalized)
        }
    }
}

/**
 * Énergie du véhicule (spec §9.4, étape 3).
 *
 * Le vocabulaire n'est pas dans la spec : il est repris de la rubrique P.3 du certificat
 * d'immatriculation, la seule source dont l'utilisateur dispose au moment de la saisie.
 * Il détermine les valeurs de coûts proposées par défaut — carburant contre électricité,
 * entretien, malus — d'où son appartenance au véhicule et non au modèle de coûts.
 */
enum class EnergySource {
    DIESEL,
    PETROL,
    HYBRID,
    PLUG_IN_HYBRID,
    ELECTRIC,
    LPG,
}

/**
 * Mode de détention (spec §9.4, étape 3).
 *
 * C'est lui qui décide de la structure du coût fixe : la spec §6.4 oppose explicitement
 * « LLD » à « amortissement + financement ». Un véhicule en LLD n'a pas d'amortissement à
 * calculer, un véhicule financé en a un et un coût de crédit ; les confondre fausserait `C3`,
 * donc toutes les métriques de marge.
 */
enum class OwnershipMode {
    /** Acquis, sans crédit en cours : amortissement seul. */
    OWNED_OUTRIGHT,

    /** Acquis à crédit : amortissement plus coût du financement. */
    OWNED_FINANCED,

    /** Location avec option d'achat. */
    LEASE_LOA,

    /** Location longue durée : un loyer, pas d'amortissement. */
    LEASE_LLD,

    /** Location courte durée, au jour ou à la semaine. */
    RENTAL,
}

/**
 * Le véhicule, **rattaché à l'exploitant et non au chauffeur** (spec §9.3).
 *
 * Ce rattachement est un autre point d'anticipation de la flotte : une flotte de trois
 * véhicules et deux chauffeurs n'a pas de correspondance un pour un, et `Outing` porte
 * `driver_id` *et* `vehicle_id` séparément (spec §9.3). Accrocher le véhicule au chauffeur
 * aurait rendu ce cas impossible sans migration.
 */
data class Vehicle(
    val id: VehicleId,
    val tenantId: TenantId,
    val plate: Plate,
    val energy: EnergySource,
    val ownership: OwnershipMode,
    val firstRegisteredOn: LocalDate,
) {
    /**
     * Vrai lorsque le coût de détention comporte un amortissement, faux lorsqu'il se réduit
     * à un loyer. C'est la question que le modèle de coûts posera au véhicule.
     */
    val isAmortizable: Boolean
        get() = ownership == OwnershipMode.OWNED_OUTRIGHT || ownership == OwnershipMode.OWNED_FINANCED
}
