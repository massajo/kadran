package io.korallis.kadran.platform.tenancy

/**
 * Levée quand du code réclame le tenant courant alors qu'aucun n'est établi.
 *
 * Ce n'est jamais un cas nominal (spec §9.2) : c'est soit un appel hors du périmètre d'une
 * requête authentifiée, soit un job asynchrone qui aurait dû recevoir son tenant en
 * paramètre explicite. Dans les deux cas, échouer bruyamment est la seule issue sûre —
 * retourner `null` ou un tenant par défaut ferait fuiter les données d'un autre exploitant.
 */
class MissingTenantContextException :
    IllegalStateException(
        "Aucun tenant n'est etabli sur ce thread. " +
            "Une requete HTTP doit passer par TenantContextFilter ; " +
            "un traitement asynchrone doit recevoir son TenantId explicitement (spec §9.2).",
    )
