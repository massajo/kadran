package io.korallis.kadran.activity.domain.model

import java.math.BigDecimal

/**
 * Valeur JSON, sans dépendance externe — le typage retenu pour `platformExtras` (spec §7.3,
 * §7.6), à la place du `JsonNode` (Jackson) que la spec esquisse.
 *
 * ### Pourquoi ni `JsonNode` ni `Map<String, Any?>`
 *
 * `domain/model` ne dépend que de `shared-kernel` et de la bibliothèque standard
 * (`CLAUDE.md` §2.4) : Jackson n'y entre pas, quand bien même il finira par transiter par
 * `infrastructure/spi/persistence` pour parler à la colonne JSONB. Rendre le `JsonNode` de la
 * spec littéralement aurait donc fait dépendre le domaine de Jackson pour un type qui ne sert
 * qu'à porter une valeur structurée — la première violation de §2.4 que ce module aurait pu
 * commettre.
 *
 * Un `Map<String, Any?>` évite Jackson mais pas le problème symétrique : `Any?` accepte
 * n'importe quel type Kotlin, y compris un type métier qui ne sait pas se sérialiser en JSON.
 * L'erreur se découvrirait alors à l'exécution, au moment d'écrire en base — trop tard pour
 * ce que `platformExtras` doit garantir : rester **exploitable par le moteur de métriques**
 * (spec §7.6), donc de forme JSON connue à la compilation.
 *
 * [RevenueRecordJson] est un type somme strictement isomorphe à un document JSON : aucune
 * valeur de ce type ne peut échouer à se sérialiser. La conversion vers/depuis le `JSONB` de
 * jOOQ (et, si besoin un jour, un `JsonNode` Jackson) est le travail de
 * `infrastructure/spi/persistence`, jamais de `domain/model`.
 *
 * Portée à ce contexte : le nom n'est pas générique (`JsonValue`) pour ne pas tenter un autre
 * agrégat du module — `Outing` en particulier, construit en parallèle (KDN-34) — de réutiliser
 * ce fichier et d'en disputer l'évolution. Un type équivalent, si besoin, s'introduit à côté.
 */
sealed interface RevenueRecordJson {
    /** Objet JSON — les clés de `platformExtras` sont préfixées par plateforme (spec §7.6). */
    data class Obj(
        val fields: Map<String, RevenueRecordJson>,
    ) : RevenueRecordJson

    data class Arr(
        val items: List<RevenueRecordJson>,
    ) : RevenueRecordJson

    data class Str(
        val value: String,
    ) : RevenueRecordJson

    data class Num(
        val value: BigDecimal,
    ) : RevenueRecordJson

    data class Bool(
        val value: Boolean,
    ) : RevenueRecordJson

    data object Null : RevenueRecordJson

    companion object {
        /** L'objet vide — valeur par défaut d'un `platformExtras` sans spécificité plateforme. */
        fun empty(): Obj = Obj(emptyMap())
    }
}
