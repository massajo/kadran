package io.korallis.kadran.activity.domain.spi

import io.korallis.kadran.activity.domain.model.RevenueRecord
import io.korallis.kadran.activity.domain.model.RevenueRecordId
import io.korallis.kadran.activity.domain.model.RevenueRecordJson

/**
 * Port piloté du `RevenueRecord` — ce que le domaine **exige** d'un fournisseur de
 * persistance (spec §10.2, ADR-005), même patron que `IdentityRepositories` (KDN-27).
 *
 * Aucune méthode ne prend de `TenantId` : il est fixé à la construction de l'adaptateur, par
 * le `TenantScopedQuery` qui l'exige lui-même à la sienne (ADR-001). `findAll()` veut dire
 * « tous les `RevenueRecord` de l'exploitant courant », et ne peut pas dire autre chose.
 *
 * ### `save` prend un `rawPayload` séparé de l'agrégat
 *
 * `RevenueRecord` ne porte pas le document source intégral — voir la note sur l'agrégat.
 * L'écriture, elle, doit bien le recevoir : la colonne `raw_payload` (spec §7.6) est
 * `NOT NULL`, sans valeur par défaut, et la seule voie d'écriture est ce port. Le séparer en
 * paramètre plutôt que de l'ajouter à l'agrégat maintient l'invariant vérifiable par
 * construction : aucune méthode de lecture de ce port ne peut renvoyer un `rawPayload`,
 * puisqu'aucune n'en a le type. C'est la preuve, au niveau du port et non seulement de son
 * implémentation, que le critère d'acceptation « aucun calcul ne lit `raw_payload` » tient.
 */
interface RevenueRecordRepository {
    fun findById(id: RevenueRecordId): RevenueRecord?

    fun findAll(): List<RevenueRecord>

    /** Insère ou met à jour le revenu, avec le document source qui l'a produit. */
    fun save(
        record: RevenueRecord,
        rawPayload: RevenueRecordJson,
    )

    /** @return vrai si une ligne a été supprimée — faux si elle appartient à un autre exploitant. */
    fun deleteById(id: RevenueRecordId): Boolean
}
