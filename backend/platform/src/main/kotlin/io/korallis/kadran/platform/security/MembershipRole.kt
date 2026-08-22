package io.korallis.kadran.platform.security

/**
 * Rôle porté par le rattachement d'un compte à un tenant (spec §9.3).
 *
 * Le rôle est une propriété du **couple** compte/tenant, pas du compte : un chauffeur peut
 * appartenir à plusieurs tenants au fil du temps, avec un rôle différent dans chacun. C'est
 * pourquoi il voyage dans le jeton aux côtés du tenant, et non à part.
 */
enum class MembershipRole {
    OWNER,
    MANAGER,
    DRIVER,
    ;

    /**
     * Nom d'autorité Spring Security. Le préfixe `ROLE_` n'est pas décoratif : `hasRole("OWNER")`
     * le rajoute silencieusement, et une autorité qui ne le porte pas ne correspond alors à rien.
     */
    val authority: String get() = AUTHORITY_PREFIX + name

    companion object {
        const val AUTHORITY_PREFIX: String = "ROLE_"

        /** Retourne `null` sur une valeur inconnue : la source est un jeton, donc non fiable. */
        fun parse(raw: String?): MembershipRole? = entries.firstOrNull { it.name == raw }
    }
}
