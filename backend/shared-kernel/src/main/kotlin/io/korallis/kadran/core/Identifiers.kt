package io.korallis.kadran.core

import java.util.UUID

/**
 * Identifiant de l'entité juridique exploitante, **tel que le domaine le nomme** (spec §7.2).
 *
 * ### Pourquoi il ne s'agit pas d'un doublon de `platform.tenancy.TenantId`
 *
 * Les deux types portent le même UUID et répondent à deux questions différentes :
 *
 * - `platform.tenancy.TenantId` est un **jeton d'isolation**. Il vit dans le `TenantContext`,
 *   il est exigé à la construction d'un `TenantScopedQuery`, et il ne quitte jamais la
 *   frontière web/persistance. C'est un dispositif de sécurité (ADR-001).
 * - `io.korallis.kadran.core.TenantId` est l'**identité d'un agrégat**. La spec §7.3 le place
 *   dans `RevenueRecord`, `WorkDay` et `ImportBatch` ; la spec §9.3 en fait la racine du
 *   modèle de flotte. Un domaine qui ne pourrait pas nommer l'exploitant ne pourrait pas
 *   modéliser `Tenant` — l'agrégat aurait une identité, mais pas de type pour la dire.
 *
 * La règle ArchUnit `domainDoesNotDependOnPlatform` (KDN-16) interdit au domaine de nommer le
 * premier, et c'est très bien : `platform` tire Spring, et le domaine ne doit pas le voir. Le
 * second vit donc ici, dans le shared kernel, qui n'a aucune dépendance — exactement où la
 * spec §7.2 l'écrit.
 *
 * La conversion est explicite et n'a lieu que dans `infrastructure/spi/persistence`, sur
 * l'`UUID` nu. Rien à unifier tant que `platform` ne peut pas dépendre du shared kernel ;
 * le jour où il le pourra, un `typealias` d'une ligne suffira.
 *
 * L'UUID est opaque : il ne porte aucune donnée personnelle.
 */
@JvmInline
value class TenantId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()

    companion object {
        /** Identifiant neuf, pour la création d'un exploitant. */
        fun next(): TenantId = TenantId(UUID.randomUUID())

        /**
         * @throws IllegalArgumentException si [raw] n'est pas un UUID — un identifiant
         *   d'exploitant illisible n'a pas de repli raisonnable.
         */
        fun of(raw: String): TenantId = TenantId(UUID.fromString(raw))
    }
}

/**
 * Identifiant d'un chauffeur au sein d'un exploitant (spec §7.2, §9.3).
 *
 * Un chauffeur est enregistré **par** un exploitant : l'unicité de cet identifiant se lit
 * toujours avec un [TenantId], jamais seule. La continuité d'une personne physique d'un
 * exploitant à l'autre n'est pas portée par ce type mais par le compte d'authentification
 * référencé par l'appartenance — voir `Membership.accountId`.
 */
@JvmInline
value class DriverId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()

    companion object {
        /** Identifiant neuf, pour l'enregistrement d'un chauffeur. */
        fun next(): DriverId = DriverId(UUID.randomUUID())

        /** @throws IllegalArgumentException si [raw] n'est pas un UUID. */
        fun of(raw: String): DriverId = DriverId(UUID.fromString(raw))
    }
}
