package io.korallis.kadran.activity.domain.model

import io.korallis.kadran.core.BusinessDayPolicy
import io.korallis.kadran.core.Distance
import io.korallis.kadran.core.Money
import io.korallis.kadran.core.RevenueRecordId
import io.korallis.kadran.core.TenantId
import io.korallis.kadran.core.WorkPeriod
import io.korallis.kadran.core.toBusinessDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Identifiant d'une sortie, unique au sein d'un exploitant (spec §4.4). */
@JvmInline
value class OutingId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()

    companion object {
        fun next(): OutingId = OutingId(UUID.randomUUID())
    }
}

/**
 * Finalité d'une sortie (spec §4.4).
 *
 * Une sortie `PERSONNEL` est conservée — elle compte dans le total odométrique — mais exclue
 * de tout calcul de rentabilité : la marge d'un trajet privé n'a pas de sens.
 */
enum class TripPurpose {
    PROFESSIONNEL,
    PERSONNEL,
}

/**
 * Origine du kilométrage d'une sortie (spec §4.4).
 *
 * `DRIVERSNOTE` est la source de référence de la v1 (spec §3.5) ; `CSV_GENERIC` couvre un
 * export dont le format n'est pas nommément outillé ; `MANUAL` une saisie directe.
 */
enum class MileageSource {
    DRIVERSNOTE,
    CSV_GENERIC,
    MANUAL,
}

/**
 * Ce que [Outing.record] a besoin de savoir sur le temps d'une sortie — sa journée
 * d'exploitation, sa fenêtre horaire éventuelle, et si elle traverse minuit.
 *
 * Regroupé dans un seul type plutôt que porté comme trois paramètres de fonction distincts,
 * pour tenir `Outing.record` sous le seuil de `LongParameterList` (`detekt.yml`, 8 paramètres)
 * sans découper artificiellement le modèle — les trois champs restent verrouillés ensemble,
 * ce qu'ils sont : on ne construit jamais `spansMidnight` sans la fenêtre qui le justifie.
 */
data class OutingTiming(
    val businessDay: LocalDate,
    val window: WorkPeriod?,
    val spansMidnight: Boolean,
) {
    companion object {
        /** Fuseau d'exploitation (spec §4.3) : jamais un autre, ni le fuseau système ambiant. */
        private val OPERATING_ZONE: ZoneId = ZoneId.of("Europe/Paris")

        /**
         * Dérive la journée d'exploitation et le franchissement de minuit d'une fenêtre
         * horaire connue.
         *
         * `businessDay` est dérivé de `[window].from` via [BusinessDayPolicy] — jamais de
         * `LocalDate.from(instant)` (`CLAUDE.md` §2.6) — et rattaché à son **début** (spec
         * §4.3 : « une sortie chevauchant le seuil est rattachée à la journée d'exploitation
         * de son début »). `spansMidnight` compare les dates calendaires locales de début et
         * de fin : c'est un fait physique, dérivé et non saisi.
         */
        fun fromWindow(
            window: WorkPeriod,
            policy: BusinessDayPolicy = BusinessDayPolicy(),
            zone: ZoneId = OPERATING_ZONE,
        ): OutingTiming {
            val businessDay = window.from.toBusinessDay(policy, zone)
            val startDate = window.from.atZone(zone).toLocalDate()
            val endDate = window.to.atZone(zone).toLocalDate()
            // `LocalDate` est une classe « value-based » du JDK (JEP 401) : `!=`/`==` y
            // compilent en Kotlin vers un test de reference rapide avant `equals`, que
            // `-Werror` refuse sur ce type. `isEqual` compare par valeur sans cette voie.
            return OutingTiming(businessDay, window, spansMidnight = !startDate.isEqual(endDate))
        }

        /**
         * Journée d'exploitation connue, sans horaire — le cas d'une source qui ne fournit
         * qu'une date.
         *
         * [businessDay] est fourni directement par l'appelant plutôt que dérivé d'un instant
         * qui n'existe pas : c'est exactement le cas que vise le piège Driversnote de
         * `CLAUDE.md` §8 (`Début`/`Fin` sans heure — « mettre `window` à `null` plutôt que
         * d'inventer `00:00` »). `spansMidnight` est `false` : sans horaire, il n'y a rien à
         * observer qui traverse minuit.
         */
        fun withoutWindow(businessDay: LocalDate): OutingTiming =
            OutingTiming(businessDay, window = null, spansMidnight = false)
    }
}

/**
 * Champs d'une sortie que toutes les sources ne fournissent pas (spec §4.4).
 *
 * Regroupés pour la même raison que [OutingTiming] : tenir `Outing.record` sous le seuil de
 * `LongParameterList`. Tous à `null` par défaut — une source qui n'en fournit aucun (Driversnote
 * sans étiquette, une saisie manuelle) n'a rien à passer.
 */
data class OutingDetails(
    val startLabel: String? = null,
    val endLabel: String? = null,
    val mileageAllowance: Money? = null,
    val linkedRevenue: RevenueRecordId? = null,
)

/**
 * L'unité économique du produit (spec §4.1, §4.4, ADR-002).
 *
 * ### `window` nullable, jamais d'heure inventée
 *
 * Driversnote peut ne fournir qu'une date, sans horaire (`CLAUDE.md` §8, piège Driversnote).
 * Dans ce cas, `window` vaut `null` : inventer `00:00` produirait une amplitude et un
 * rattachement à la journée d'exploitation faux, pour un chiffre qui *aurait l'air* d'une
 * mesure sans en être une (ADR-004).
 *
 * ### Ni `driverId` ni `vehicleId`
 *
 * Le pseudocode de la spec §4.4 ne les liste pas, et l'issue KDN-34 non plus. Le v1 est
 * mono-chauffeur (persona §2, « un véhicule ») ; l'anticipation de la flotte passe par
 * `Membership`/`Vehicle` (KDN-27), pas par `Outing`.
 *
 * ### `startLabel` / `endLabel` : PII non réduite
 *
 * Ces champs portent potentiellement une adresse complète (« Home, 1 Rue…, 91300 Massy »,
 * spec §8.1). Leur réduction au code postal et à la ville est traitée par KDN-47, pas encore
 * livrée. Le domaine porte donc ces deux champs — c'est ce que dit la spec §4.4 — mais
 * l'adaptateur de persistance ne les écrit pas en base (voir le changeset KDN-34) : les
 * persister en clair aujourd'hui les exposerait sans le traitement que KDN-47 doit apporter.
 *
 * ### `linkedRevenue` : une référence, pas un rapprochement enrichi
 *
 * `RevenueRecord` (KDN-35) est construit en parallèle, sur une autre branche. Tant que le
 * score et le statut de rapprochement (KDN-75) n'existent pas, `linkedRevenue` reste une
 * simple référence optionnelle vers un `RevenueRecordId` — rien de plus.
 */
data class Outing(
    val id: OutingId,
    val tenantId: TenantId,
    val businessDay: LocalDate,
    val window: WorkPeriod?,
    val spansMidnight: Boolean,
    val distance: Distance,
    val purpose: TripPurpose,
    val startLabel: String?,
    val endLabel: String?,
    val mileageAllowance: Money?,
    val source: MileageSource,
    val linkedRevenue: RevenueRecordId?,
) {
    /** Faux pour une sortie `PERSONNEL` : conservée, mais hors calcul de marge (spec §4.4). */
    val countsTowardProfitability: Boolean get() = purpose == TripPurpose.PROFESSIONNEL

    companion object {
        /**
         * Enregistre une sortie et rend l'événement qui la justifie.
         *
         * [timing] porte `businessDay`/`window`/`spansMidnight` déjà résolus par
         * [OutingTiming.fromWindow] ou [OutingTiming.withoutWindow] — c'est là, et seulement
         * là, que se décide si une heure est connue ou non.
         *
         * Les champs qu'une source ne fournit pas toujours (`startLabel`, `endLabel`,
         * `mileageAllowance`, `linkedRevenue`) ne sont **pas** un paramètre de cette fonction :
         * à sept paramètres déjà, un huitième aurait franchi le seuil `LongParameterList` de
         * `detekt.yml`. Ils s'ajoutent après coup avec [with], sur le [Transition] rendu ici —
         * ce qui ne change ni l'agrégat déjà valide ni l'événement, qui ne les porte pas.
         */
        fun record(
            id: OutingId,
            tenantId: TenantId,
            timing: OutingTiming,
            distance: Distance,
            purpose: TripPurpose,
            source: MileageSource,
            recordedAt: Instant,
        ): Transition<Outing, OutingEvent> {
            val outing =
                Outing(
                    id = id,
                    tenantId = tenantId,
                    businessDay = timing.businessDay,
                    window = timing.window,
                    spansMidnight = timing.spansMidnight,
                    distance = distance,
                    purpose = purpose,
                    startLabel = null,
                    endLabel = null,
                    mileageAllowance = null,
                    source = source,
                    linkedRevenue = null,
                )
            return Transition(outing, OutingRecorded(tenantId, id, timing.businessDay, recordedAt))
        }
    }
}

/**
 * Attache les champs optionnels de [details] à la sortie que porte cette transition.
 *
 * N'affecte pas [Transition.event] : `OutingRecorded` ne porte que `businessDay`, jamais ces
 * champs, si bien que les y ajouter après coup ne rend pas l'événement obsolète.
 */
fun Transition<Outing, OutingEvent>.with(details: OutingDetails): Transition<Outing, OutingEvent> =
    copy(
        state =
            state.copy(
                startLabel = details.startLabel,
                endLabel = details.endLabel,
                mileageAllowance = details.mileageAllowance,
                linkedRevenue = details.linkedRevenue,
            ),
    )
