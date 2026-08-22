package io.korallis.kadran.platform.security.audit

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Point d'appel de l'audit d'authentification (spec §8.4.3, première ligne du tableau).
 *
 * **La table `audit_event` n'existe pas encore : elle relève de KDN-21.** Ce port est posé
 * maintenant pour que chaque connexion, échec, rafraîchissement, déconnexion et refus ait
 * déjà son site d'émission ; le jour où KDN-21 livre la table, il n'y a qu'une implémentation
 * à publier — aucun appelant à retrouver, aucun chemin à rouvrir. Poser la table ici aurait
 * été inventer un schéma que la spec attribue à une autre issue.
 */
fun interface AuthenticationAuditor {
    fun record(event: AuthenticationAuditEvent)
}

/**
 * Repli d'ici KDN-21 : l'événement part dans les logs applicatifs, en paires clé/valeur.
 *
 * **Ce n'est pas de l'audit** et ne doit pas être pris pour tel — spec §10.7 le dit en
 * toutes lettres : les logs applicatifs sont éphémères et non opposables, là où `audit_event`
 * est immuable et conservé cinq ans. Le repli sert à voir passer les événements en
 * développement, pas à satisfaire l'obligation.
 *
 * Conséquence directe : [AuthenticationAuditEvent.ipAddress] et
 * [AuthenticationAuditEvent.userAgent] ne sont **pas** journalisés ici. Ils appartiennent au
 * journal réglementaire ; les expédier vers un agrégateur externe serait exactement le
 * mélange des trois flux que §10.7 interdit.
 */
class LoggingAuthenticationAuditor(
    private val log: Logger = LoggerFactory.getLogger(CATEGORY),
) : AuthenticationAuditor {
    override fun record(event: AuthenticationAuditEvent) {
        log
            .atInfo()
            .addKeyValue(ACTION_KEY, event.action.name)
            .addKeyValue(OUTCOME_KEY, event.outcome.name)
            .addKeyValue(ACTOR_TYPE_KEY, event.actorType.name)
            .also { builder -> event.actorId?.let { builder.addKeyValue(ACTOR_ID_KEY, it.toString()) } }
            .also { builder -> event.reason?.let { builder.addKeyValue(REASON_KEY, it.name) } }
            .setMessage(MESSAGE)
            .log()
    }

    companion object {
        const val CATEGORY: String = "io.korallis.kadran.audit"

        const val ACTION_KEY: String = "audit_action"
        const val OUTCOME_KEY: String = "audit_outcome"
        const val ACTOR_TYPE_KEY: String = "audit_actor_type"
        const val ACTOR_ID_KEY: String = "audit_actor_id"
        const val REASON_KEY: String = "audit_reason"
        const val MESSAGE: String = "authentication audit"
    }
}
