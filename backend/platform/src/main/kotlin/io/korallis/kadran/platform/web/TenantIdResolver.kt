package io.korallis.kadran.platform.web

import io.korallis.kadran.platform.tenancy.TenantId
import jakarta.servlet.http.HttpServletRequest

/**
 * Extrait le tenant d'une requête entrante.
 *
 * La spec §9.2 le veut alimenté **depuis le JWT** ; Spring Security n'est pas encore en
 * place (KDN-15 ne porte que le contexte lui-même). Le port est donc défini ici et le
 * filtre s'en sert dès maintenant : quand l'authentification arrivera, elle publiera son
 * implémentation et rien d'autre ne bougera.
 */
fun interface TenantIdResolver {
    /** @return le tenant de la requête, ou `null` si elle n'en porte aucun (requête anonyme). */
    fun resolve(request: HttpServletRequest): TenantId?
}

/**
 * Implémentation par défaut, tant qu'aucune authentification n'est branchée : **aucun
 * tenant**.
 *
 * Le choix est délibéré. Lire le tenant d'un en-tête de requête serait commode en
 * développement et deviendrait une usurpation triviale en production ; le laisser absent
 * fait échouer `requireTenantId()` bruyamment, ce qui est exactement le comportement voulu
 * tant que personne ne peut prouver son identité.
 */
object AbsentTenantIdResolver : TenantIdResolver {
    override fun resolve(request: HttpServletRequest): TenantId? = null
}
