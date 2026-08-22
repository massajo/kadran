package io.korallis.kadran.activity.domain.model

import io.korallis.kadran.core.Distance
import io.korallis.kadran.core.Grain
import io.korallis.kadran.core.Money
import io.korallis.kadran.core.PlatformId
import io.korallis.kadran.core.Ratio
import io.korallis.kadran.core.TenantId
import io.korallis.kadran.core.WorkPeriod
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Identifiant d'un `RevenueRecord`, unique au sein d'un exploitant.
 *
 * Défini ici et non dans le shared kernel : la spec §7.2 range `TenantId`/`DriverId` dans
 * `core` parce qu'ils traversent plusieurs contextes bornés (spec §9.3 les cite pour
 * `Outing` et `RevenueRecord` à la fois). `RevenueRecordId` n'a qu'un seul agrégat, dans un
 * seul contexte — exactement le raisonnement qui range déjà `VehicleId` et `MembershipId`
 * dans `context-identity` plutôt que dans `core`.
 */
@JvmInline
value class RevenueRecordId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()

    companion object {
        fun next(): RevenueRecordId = RevenueRecordId(UUID.randomUUID())
    }
}

/**
 * Référence d'un `RevenueRecord` dans le système de la plateforme source.
 *
 * Elle porte le rapprochement idempotent d'un réimport — spec §7.7 : « recouvrement de
 * périodes : last-write-wins par `externalRef` ». Un enregistrement peut en porter plusieurs :
 * Bolt fournit à la fois `Identifiant chauffeur` et `Identifiant individuel` (§3.4), et rien
 * n'exclut qu'une future plateforme en fournisse davantage — d'où le `Set` sur l'agrégat.
 *
 * [label] n'est pas un type fermé : il nomme la colonne source telle que la plateforme la
 * nomme (`NuméroFacture`, §3.3 — retenu comme `externalRef` Uber ; `Identifiant chauffeur`,
 * §3.4). Un enum fermé se réécrirait à chaque plateforme ajoutée, quand cette variabilité est
 * déjà le problème que `platformExtras` résout par des clés préfixées plutôt que par un
 * schéma figé.
 */
data class ExternalRef(
    val label: String,
    val value: String,
) {
    init {
        require(label.isNotBlank()) { "le libelle d'une reference externe ne peut pas etre vide" }
        require(value.isNotBlank()) { "la valeur d'une reference externe ne peut pas etre vide" }
    }
}

/**
 * Ventilation d'un revenu — brut, net, commission, pourboires, incitations, suppléments
 * (spec §7.3). Tous en [Money] (`CLAUDE.md` §2.2) : la commission effective ne se lit qu'en
 * comparant [gross] et [net] au centime (spec §3.2 — 786,91 / 2 257,18 = 34,9 %).
 *
 * Les six montants partagent nécessairement une devise : mélanger des lignes de devises
 * différentes dans un seul `RevenueRecord` n'a pas de sens métier (spec §3.3 — `Devise` vaut
 * `EUR` sur tout l'échantillon), et cet agrégat n'a pas vocation à faire de la conversion.
 *
 * @throws IllegalArgumentException si les six montants ne partagent pas la même devise.
 */
data class RevenueBreakdown(
    val gross: Money,
    val net: Money,
    val commission: Money,
    val tips: Money,
    val incentives: Money,
    val surcharges: Money,
) {
    init {
        val currencies = setOf(gross, net, commission, tips, incentives, surcharges).map { it.currency }.toSet()
        require(currencies.size == 1) {
            "un RevenueBreakdown ne peut pas melanger des devises : $currencies"
        }
    }

    /** La devise commune aux six montants — jamais ambiguë après construction. */
    val currency get() = gross.currency
}

/**
 * Ventilation TVA d'un revenu — base HT, montant de TVA, total TTC et taux appliqué
 * (spec §3.3 : `MontantNet`, `MontantTaxe`, `MontantBrut`, `Tauxtaxe`).
 *
 * Les trois montants ne sont **jamais recalculés l'un depuis l'autre** ici : ils sont stockés
 * tels que la plateforme les fournit. Ce n'est pas prudence gratuite — `CLAUDE.md` §8 documente
 * un piège symétrique sur Driversnote (`Taux` arrondi, `Remboursement` non) : un contrôle de
 * cohérence est un travail d'ingestion (§7.7, « contrôles d'intégrité par source »), pas un
 * invariant que le domaine imposerait au prix de rejeter une plateforme moins rigoureuse que
 * l'échantillon Uber vérifié à ±0,01 près.
 *
 * @throws IllegalArgumentException si les trois montants ne partagent pas la même devise.
 */
data class VatBreakdown(
    val baseExcludingVat: Money,
    val vatAmount: Money,
    val totalIncludingVat: Money,
    val rate: Ratio,
) {
    init {
        val currencies = setOf(baseExcludingVat, vatAmount, totalIncludingVat).map { it.currency }.toSet()
        require(currencies.size == 1) {
            "un VatBreakdown ne peut pas melanger des devises : $currencies"
        }
    }
}

/**
 * Compteurs d'activité fournis par la plateforme — courses, temps en ligne, distance
 * (spec §7.3, §3.2 « Nombre de courses », §3.4 « Courses terminées », « Temps en ligne (min) »,
 * « Distance totale de course »).
 *
 * Chaque compteur est individuellement nullable : une source peut fournir l'un sans les
 * autres (le récapitulatif fiscal Uber donne courses et kilométrage, jamais de temps en
 * ligne — §3.2), et `CLAUDE.md` §2.1 interdit d'inventer les compteurs absents plutôt que de
 * les laisser à `null`.
 *
 * @throws IllegalArgumentException si les trois compteurs sont `null` — dans ce cas
 *   `RevenueRecord.counts` doit lui-même valoir `null`, pas porter un objet sans contenu.
 * @throws IllegalArgumentException si [trips] est négatif.
 */
data class ActivityCounts(
    val trips: Int?,
    val onlineTime: Duration?,
    val distance: Distance?,
) {
    init {
        require(trips != null || onlineTime != null || distance != null) {
            "un ActivityCounts entierement vide doit etre absent (null), pas construit"
        }
        require(trips == null || trips >= 0) { "un nombre de courses ne peut pas etre negatif : $trips" }
    }
}

/**
 * Document source ayant contribué aux données d'un `RevenueRecord` (spec §3.1-§3.3).
 *
 * Fermé sur les trois documents Uber du périmètre v1 (ADR-008) : le relevé hebdomadaire donne
 * le net et les pourboires, le récapitulatif fiscal donne le kilométrage mensuel et la
 * commission agrégée, la facture donne le brut, la TVA et le détail par course. Une future
 * plateforme ajoute ses propres valeurs, comme `PlatformId` grandit avec elle.
 */
enum class SourceDocument {
    /** Relevé hebdomadaire Uber (PDF) — spec §3.1. Grain course, net par course, pourboires. */
    UBER_WEEKLY_STATEMENT,

    /** Récapitulatif fiscal Uber (PDF, mensuel) — spec §3.2. Grain mois, kilométrage, commission. */
    UBER_TAX_SUMMARY,

    /** Factures Uber (CSV) — spec §3.3. Grain course, brut, TVA, PII passager et chauffeur. */
    UBER_INVOICE,
}

/**
 * Origine d'une contribution aux données d'un `RevenueRecord`.
 *
 * `provenance` est un `Set` parce qu'un seul enregistrement peut agréger plusieurs documents :
 * la commission effective ne se calcule qu'en croisant le relevé hebdomadaire (le net) et la
 * facture (le brut et la TVA) — spec §3.1, §3.3, §7.7. Chaque document qui a contribué à
 * `amounts` ou `vat` laisse ici sa trace, ce que l'endpoint de traçabilité de la spec §7.8
 * exige de pouvoir répondre : « pour toute métrique, les enregistrements sources ».
 */
data class DataProvenance(
    val document: SourceDocument,
    val importedAt: Instant,
)

/**
 * Le revenu, quelle que soit la plateforme et quel que soit son grain (spec §7.3).
 *
 * Remplace `Trip` comme porteur de revenu : `Trip` supposait un grain course uniforme, que
 * seule Uber respecte. `grain` explicite la granularité plutôt que la masquer derrière un
 * type unique qui prétendrait à tort que toute plateforme parle au même niveau.
 *
 * ### `coverage` — instant unique ou fenêtre, jamais les deux types
 *
 * [WorkPeriod] modélise déjà un instant par `from == to` (durée nulle) — pas la peine d'un
 * second type pour ce que `grain` distingue déjà sémantiquement. `TRIP` exige `from == to`
 * (spec §7.3 : « instant unique pour `TRIP` ») ; les autres grains couvrent une fenêtre
 * généralement non nulle, sans que l'agrégat ait à l'imposer strictement — un enregistrement
 * `DAY` construit sur une journée exacte reste un instant si sa source ne donne qu'une date
 * sans heure (spec §3.3 : `DateFacture` est une date seule).
 *
 * ### Ce que l'agrégat ne porte pas : `raw_payload`
 *
 * Le document source intégral (spec §7.6, zone « Brut ») n'est **pas** un champ de cet
 * agrégat — la définition de la spec §7.3 elle-même ne le liste pas parmi les champs de
 * `RevenueRecord`, à la différence de `platformExtras`. Le porter ici obligerait toute
 * lecture (`findById`, `findAll`) à le charger en mémoire, alors que le critère d'acceptation
 * de l'issue est justement qu'aucun calcul ne le lit jamais. `infrastructure/spi/persistence`
 * le reçoit séparément, à l'écriture seulement — voir `RevenueRecordRepository.save`.
 *
 * @throws IllegalArgumentException si [grain] vaut `TRIP` et que [coverage] n'est pas un
 *   instant unique (`from != to`).
 */
data class RevenueRecord(
    val id: RevenueRecordId,
    val tenantId: TenantId,
    val platform: PlatformId,
    val grain: Grain,
    val coverage: WorkPeriod,
    val externalRefs: Set<ExternalRef>,
    val amounts: RevenueBreakdown,
    val vat: VatBreakdown?,
    val counts: ActivityCounts?,
    val platformExtras: RevenueRecordJson,
    val provenance: Set<DataProvenance>,
) {
    init {
        if (grain == Grain.TRIP) {
            require(coverage.from.equals(coverage.to)) {
                "un RevenueRecord au grain TRIP couvre un instant unique : $coverage"
            }
        }
    }

    companion object {
        /**
         * Rend [record] avec l'événement qui justifie son enregistrement.
         *
         * L'agrégat se construit par son constructeur — `detekt.yml` relève
         * `constructorThreshold: 16` précisément pour lui (« les agrégats du domaine portent
         * beaucoup de champs et sont construits en une fois ») — plutôt que par une fabrique
         * à douze paramètres, qui buterait elle sur `functionThreshold: 8` pour n'en éviter
         * qu'une redite. Cette fonction ne fait donc que le travail qu'un constructeur ne
         * peut pas faire lui-même : produire l'événement qui accompagne toute mutation
         * (`CLAUDE.md` §2.5), même patron que `Transition<Membership>` en KDN-27.
         */
        fun record(
            record: RevenueRecord,
            occurredAt: Instant,
        ): Transition<RevenueRecord> =
            Transition(
                record,
                RevenueRecorded(record.tenantId, record.id, record.platform, record.grain, occurredAt),
            )
    }
}
