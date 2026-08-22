package io.korallis.kadran.identity.domain.model

import io.korallis.kadran.core.DriverId
import io.korallis.kadran.core.TenantId
import java.time.Instant
import java.util.UUID

/** Identifiant d'une appartenance. Unique **au sein** d'un exploitant, comme sa clé primaire. */
@JvmInline
value class MembershipId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()

    companion object {
        fun next(): MembershipId = MembershipId(UUID.randomUUID())
    }
}

/**
 * Compte d'authentification — **la couture avec KDN-18**.
 *
 * KDN-18 livre l'authentification et le port qui retrouve un identifiant (compte, empreinte
 * de mot de passe, exploitant, rôle). Ce type est le seul point de contact prévu : c'est
 * `Membership` qui répond à « ce compte, chez quel exploitant, avec quel rôle », et le port
 * de KDN-18 n'aura qu'à lire les appartenances ouvertes d'un `accountId`.
 *
 * Rien ici ne connaît de mot de passe ni d'empreinte : `identity` dit qui appartient à quoi,
 * pas comment on le prouve.
 */
@JvmInline
value class AccountId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()
}

/**
 * Rôle d'un membre au sein d'un exploitant (spec §9.3).
 *
 * Les trois rôles existent dès la v1 alors qu'un chauffeur indépendant est toujours `OWNER` :
 * c'est exactement ce que « anticiper le modèle flotte » veut dire. Ajouter `MANAGER` plus
 * tard aurait imposé une migration sur une colonne contrainte, et une reprise de tous les
 * contrôles d'accès écrits entre-temps sur l'hypothèse d'un rôle unique.
 */
enum class MembershipRole {
    /** Détient l'exploitant. Un exploitant en a toujours au moins un. */
    OWNER,

    /** Gère la flotte sans en être le titulaire — persona v2. */
    MANAGER,

    /** Conduit, et ne voit que ce qui le concerne. */
    DRIVER,
}

/**
 * Période de validité d'une appartenance.
 *
 * `validUntil` à `null` signifie « en cours », pas « inconnu » : c'est la seule valeur qui
 * autorise l'index unique partiel sur les appartenances ouvertes, donc l'invariant « un
 * chauffeur, une appartenance ouverte » tenu par la base et non par un contrôle applicatif.
 */
data class MembershipPeriod(
    val validFrom: Instant,
    val validUntil: Instant?,
) {
    init {
        require(validUntil == null || validUntil.isAfter(validFrom)) {
            "une appartenance ne peut pas se fermer avant de s'ouvrir : $validFrom -> $validUntil"
        }
    }

    /** Vrai tant que la période n'est pas fermée. */
    val isOpen: Boolean get() = validUntil == null

    /** Borne de début incluse, borne de fin exclue — deux périodes qui se suivent ne se recouvrent pas. */
    fun contains(instant: Instant): Boolean =
        !instant.isBefore(validFrom) && (validUntil == null || instant.isBefore(validUntil))

    /** @throws IllegalArgumentException si [at] ne suit pas [validFrom]. */
    fun closedAt(at: Instant): MembershipPeriod = copy(validUntil = at)
}

/**
 * Le lien daté entre un chauffeur, un exploitant et un rôle (spec §9.3).
 *
 * C'est l'agrégat qui porte l'anticipation de la flotte : la v1 en crée exactement une, avec
 * le rôle `OWNER`, mais rien dans le modèle ni dans le schéma ne suppose qu'il n'y en ait
 * qu'une. Passer à une flotte de dix chauffeurs, c'est insérer dix lignes.
 *
 * **Une appartenance ne se supprime pas, elle se ferme.** Le critère d'acceptation de l'issue
 * — « les deux appartenances sont conservées et datées » — n'est tenable qu'à cette
 * condition : un `DELETE` sur révocation effacerait l'historique que la spec §8.4 conserve.
 */
data class Membership(
    val id: MembershipId,
    val tenantId: TenantId,
    val driverId: DriverId,
    val accountId: AccountId?,
    val role: MembershipRole,
    val period: MembershipPeriod,
) {
    /** Vrai tant que l'appartenance n'a pas été révoquée. */
    val isOpen: Boolean get() = period.isOpen

    /** Vrai si l'appartenance était en vigueur à [instant] — la question que pose l'audit. */
    fun isActiveAt(instant: Instant): Boolean = period.contains(instant)

    /**
     * Rattache l'appartenance à un compte d'authentification (KDN-18).
     *
     * @throws IllegalStateException si un autre compte est déjà rattaché — deux comptes pour
     *   une appartenance, ce serait deux personnes derrière un même rôle.
     */
    fun attachTo(accountId: AccountId): Membership {
        check(this.accountId == null || this.accountId == accountId) {
            "l'appartenance $id est deja rattachee au compte ${this.accountId}"
        }
        return copy(accountId = accountId)
    }

    /**
     * Change le rôle et rend l'événement qui le consigne.
     *
     * Le rôle est modifié **en place** plutôt que par fermeture puis réouverture : l'historique
     * des rôles est précisément ce que `entity_change` enregistre (spec §8.4.1, « à quoi cela
     * ressemblait avant »), et le dupliquer en lignes d'appartenance ferait deux versions d'un
     * même passé — dont l'une contredirait l'invariant « une seule appartenance ouverte ».
     *
     * @throws IllegalStateException si l'appartenance est révoquée.
     * @throws IllegalArgumentException si le rôle est inchangé — un événement d'audit qui ne
     *   consigne aucun changement rend le journal moins lisible, pas plus.
     */
    fun changeRoleTo(
        newRole: MembershipRole,
        at: Instant,
    ): Transition<Membership> {
        check(isOpen) { "le role d'une appartenance revoquee ne change plus : $id" }
        require(newRole != role) { "le role de l'appartenance $id est deja $role" }
        return Transition(
            copy(role = newRole),
            MemberRoleChanged(tenantId, id, role, newRole, at),
        )
    }

    /**
     * Ferme l'appartenance à [at].
     *
     * @throws IllegalStateException si elle est déjà close.
     * @throws IllegalArgumentException si [at] précède la prise d'effet.
     */
    fun revokeAt(at: Instant): Transition<Membership> {
        check(isOpen) { "l'appartenance $id est deja close depuis ${period.validUntil}" }
        return Transition(
            copy(period = period.closedAt(at)),
            MembershipRevoked(tenantId, id, at),
        )
    }

    companion object {
        /**
         * Ouvre une appartenance à compter de [at].
         *
         * [accountId] est nul tant que le chauffeur n'a pas de compte : on peut enregistrer
         * un chauffeur avant de l'inviter, et l'inverse — un compte sans appartenance — ne
         * donne accès à rien.
         */
        fun invite(
            id: MembershipId,
            tenantId: TenantId,
            driverId: DriverId,
            role: MembershipRole,
            at: Instant,
            accountId: AccountId? = null,
        ): Transition<Membership> {
            val membership =
                Membership(id, tenantId, driverId, accountId, role, MembershipPeriod(at, validUntil = null))
            return Transition(membership, MemberInvited(tenantId, id, driverId, role, at))
        }
    }
}
