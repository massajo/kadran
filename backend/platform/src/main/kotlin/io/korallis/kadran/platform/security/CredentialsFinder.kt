package io.korallis.kadran.platform.security

/**
 * Port sortant de l'authentification : retrouver de quoi vérifier un mot de passe.
 *
 * **C'est la couture avec le contexte `identity` (KDN-27).** La sécurité n'a pas à connaître
 * les agrégats `Tenant`, `Membership` et `Driver` ; elle a besoin d'un identifiant de compte,
 * d'une empreinte, d'un tenant et d'un rôle. Tant que l'adaptateur réel n'existe pas, aucun
 * compte n'est trouvé et toute connexion échoue — ce qui est le bon comportement par défaut
 * pour un système d'authentification.
 *
 * L'implémentation à venir doit respecter deux contraintes :
 *
 * - **La recherche porte sur l'identifiant de connexion seul, jamais sur le tenant.** Le
 *   tenant est un *résultat* de l'authentification, pas une donnée d'entrée : le laisser
 *   fournir par le client permettrait de sonder l'appartenance d'un compte à un tenant.
 * - **La comparaison de l'identifiant est insensible à la casse et normalisée** côté
 *   adaptateur, là où vit la colonne indexée.
 */
fun interface CredentialsFinder {
    /**
     * @param login identifiant de connexion saisi par l'utilisateur — une **PII** (spec §8.1) :
     *   il ne doit apparaître ni dans un log, ni dans un événement d'audit, ni dans un message
     *   d'erreur renvoyé au client.
     * @return les identifiants du compte, ou `null` s'il n'en existe aucun.
     */
    fun findByLogin(login: String): AccountCredentials?
}

/**
 * Repli tant qu'aucun adaptateur n'est publié : **aucun compte n'existe**.
 *
 * Volontairement un refus systématique plutôt qu'un compte de démonstration. Un jeu
 * d'identifiants par défaut est la façon la plus banale de mettre une porte ouverte en
 * production ; l'absence de compte, elle, se remarque à la première connexion.
 */
object NoAccountsCredentialsFinder : CredentialsFinder {
    override fun findByLogin(login: String): AccountCredentials? = null
}
