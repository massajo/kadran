package io.korallis.kadran.identity.domain.model

import io.korallis.kadran.core.TenantId
import java.time.Instant

/**
 * Étape atteinte par l'assistant d'onboarding (spec §9.4).
 *
 * L'état est persisté sur l'exploitant parce que l'assistant est **reprenable** : l'abandon
 * en cours de route est le premier risque d'activation du produit, et un brouillon qui ne
 * survit pas à la fermeture de l'onglet est un abandon garanti.
 *
 * L'ordre de déclaration suit celui des cinq étapes ; l'assistant autorise le retour en
 * arrière, aucune transition n'est donc interdite ici. Le pilotage de l'assistant lui-même
 * relève de KDN-28.
 */
enum class OnboardingStatus {
    /** Raison sociale, SIREN, forme juridique. */
    IDENTITY,

    /** Régime de TVA et schéma de cotisations, proposés puis modifiables. */
    FISCAL_PROFILE,

    /** Véhicule : immatriculation, énergie, mise en circulation, mode de détention. */
    VEHICLE,

    /** Modèle de coûts poste par poste — l'étape déterminante pour l'activation. */
    COST_MODEL,

    /** Premier import, guidé document par document. */
    FIRST_IMPORT,

    /** Un import a réussi : l'onboarding est terminé. */
    COMPLETED,
}

/**
 * L'entité juridique exploitante — la racine du modèle de la spec §9.3.
 *
 * ### `tenant` est une table scopée, et sa clé primaire est la clé d'isolation
 *
 * L'issue posait la question ; la réponse retenue est « oui, sans colonne supplémentaire ».
 * La table `tenant` porte `tenant_id` en clé primaire, si bien qu'elle satisfait
 * `TenantScopedTable` sans exception, et qu'une lecture scopée y rend au plus une ligne :
 * celle de l'appelant. L'alternative — une table `tenant (id, …)` non scopée — aurait exigé
 * un chemin d'accès hors de `TenantScopedQuery`, c'est-à-dire précisément le trou que
 * l'ADR-001 nous oblige à fermer par le code, faute de RLS pour le rattraper.
 *
 * ### Ce que l'agrégat ne porte pas, et pourquoi
 *
 * - **L'adresse** (spec §9.4, étape 1). Pour une entreprise individuelle, c'est le domicile
 *   du chauffeur : donnée `PII_HIGH` au sens de la spec §8.2, dont le chiffrement enveloppe
 *   n'est pas livré. On ne conserve pas ce qu'on ne sait pas encore protéger.
 * - **La forme juridique**. La spec §7.5 la place sur `FiscalProfile`, avec le régime de TVA
 *   et le schéma de cotisations qu'elle détermine. La dupliquer ici en ferait deux sources
 *   de vérité pour une donnée dont dépendent des calculs fiscaux.
 * - **`retainCounterpartyIdentity`** (spec §8.1). Option de tenant, mais qui appartient à
 *   l'épique Confidentialité avec la purge qui va avec.
 */
data class Tenant(
    val id: TenantId,
    val legalName: LegalName,
    val siren: Siren,
    val onboardingStatus: OnboardingStatus,
    val closedAt: Instant?,
    /**
     * Date d'enregistrement, portée par l'agrégat depuis KDN-137.
     *
     * Sans elle, [close] ne pouvait valider que la clôture n'est pas antérieure à la création
     * — la seule table où cette borne vivait était `ck_tenant_closed_after_creation`, un
     * `CHECK` sans équivalent applicatif. La colonne existait déjà en base (`DEFAULT now()`) ;
     * ce n'est que la lecture qui manquait pour que le domaine tienne seul l'invariant.
     */
    val createdAt: Instant,
) {
    /** Un exploitant clos ne reçoit plus ni import, ni invitation, ni changement de rôle. */
    val isClosed: Boolean get() = closedAt != null

    /** Vrai quand les cinq étapes de la spec §9.4 sont derrière nous. */
    val isOnboarded: Boolean get() = onboardingStatus == OnboardingStatus.COMPLETED

    /**
     * Enregistre l'étape courante de l'assistant.
     *
     * @throws IllegalStateException si l'exploitant est clos — reprendre l'onboarding d'une
     *   entité qui n'exploite plus n'a pas de sens, et le laisser passer produirait un
     *   brouillon que personne ne terminerait jamais.
     */
    fun resumeOnboardingAt(step: OnboardingStatus): Tenant {
        check(!isClosed) { "l'onboarding d'un exploitant clos ne se reprend pas : $id" }
        return copy(onboardingStatus = step)
    }

    /**
     * Clôt l'exploitant.
     *
     * @throws IllegalStateException si la clôture a déjà eu lieu — une seconde clôture
     *   émettrait un second événement d'audit pour un fait unique.
     * @throws IllegalArgumentException si [at] précède [createdAt] — un exploitant ne peut pas
     *   cesser d'exploiter avant d'avoir commencé (ex-`ck_tenant_closed_after_creation`,
     *   retirée en KDN-137).
     */
    fun close(at: Instant): Transition<Tenant> {
        check(!isClosed) { "l'exploitant $id est deja clos depuis $closedAt" }
        require(!at.isBefore(createdAt)) {
            "l'exploitant $id ne peut pas cloturer le $at, avant sa creation le $createdAt"
        }
        return Transition(copy(closedAt = at), TenantClosed(id, at))
    }

    companion object {
        /**
         * Crée l'exploitant à l'étape 1 de l'onboarding.
         *
         * L'identifiant est **passé** et non tiré ici : c'est lui qui devra scoper la requête
         * d'insertion, et le cas d'usage doit pouvoir l'ouvrir avant d'appeler le domaine.
         */
        fun register(
            id: TenantId,
            legalName: LegalName,
            siren: Siren,
            at: Instant,
        ): Transition<Tenant> {
            val tenant =
                Tenant(id, legalName, siren, OnboardingStatus.IDENTITY, closedAt = null, createdAt = at)
            return Transition(tenant, TenantRegistered(id, siren, at))
        }
    }
}
