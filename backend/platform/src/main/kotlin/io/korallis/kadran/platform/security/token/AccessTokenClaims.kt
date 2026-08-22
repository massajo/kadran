package io.korallis.kadran.platform.security.token

/**
 * Noms des revendications propres à Kadran.
 *
 * En `snake_case`, comme les clés de MDC et les colonnes : `tenant_id` désigne la même chose
 * dans un jeton, dans une ligne de log et dans une table, et doit s'écrire pareil partout.
 *
 * **Ces noms sont un contrat.** Un jeton émis avant un renommage resterait signé, donc valide,
 * mais sa revendication ne serait plus lue — l'utilisateur se retrouverait authentifié sans
 * tenant. Renommer impose d'invalider les jetons en circulation.
 */
object AccessTokenClaims {
    /** Le tenant du membership, et **la seule source de tenant acceptée** (spec §9.2). */
    const val TENANT_ID: String = "tenant_id"

    /** Le rôle du membership : `OWNER`, `MANAGER` ou `DRIVER`. */
    const val ROLE: String = "role"
}
