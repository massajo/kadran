package io.korallis.kadran.platform.security

import io.korallis.kadran.platform.tenancy.TenantId

/**
 * Ce que l'authentification a besoin de savoir d'un compte, et **rien de plus**.
 *
 * Ni nom, ni e-mail, ni adresse : la couche de sécurité manipule le strict nécessaire pour
 * vérifier un mot de passe et remplir un jeton (spec §8.1, minimisation). Les agrégats
 * `Tenant`, `Membership` et `Driver` de KDN-27 portent le reste et ne remontent pas ici.
 *
 * @property passwordHash empreinte **préfixée de son algorithme**, au format
 *   `{bcrypt}$2b$…` de `DelegatingPasswordEncoder`. Le préfixe est ce qui rendra possible un
 *   changement d'algorithme sans réinitialiser les mots de passe : sans lui, l'algorithme
 *   d'aujourd'hui devient définitif.
 * @property enabled un compte désactivé échoue à l'authentification **après** la
 *   vérification du mot de passe, jamais avant : répondre plus vite à un compte désactivé
 *   qu'à un mot de passe faux transforme le formulaire de connexion en oracle d'existence.
 */
data class AccountCredentials(
    val accountId: AccountId,
    val tenantId: TenantId,
    val role: MembershipRole,
    val passwordHash: String,
    val enabled: Boolean = true,
)
