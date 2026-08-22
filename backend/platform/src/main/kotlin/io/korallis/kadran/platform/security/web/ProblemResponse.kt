package io.korallis.kadran.platform.security.web

import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

/**
 * Écrit un refus au format `application/problem+json` (RFC 9457).
 *
 * Le corps est composé à la main plutôt que sérialisé : il n'a que trois champs, tous des
 * littéraux du code, et l'écrire ici évite de faire dépendre le chemin d'erreur de la
 * configuration de sérialisation — c'est précisément le chemin qu'on veut voir fonctionner
 * quand le reste va mal.
 *
 * **Aucun détail n'est renvoyé.** Ni le motif du refus, ni la revendication manquante, ni la
 * date d'expiration : ce sont autant d'indications offertes à qui tâtonne. Le motif exact
 * part dans le journal d'audit, où il est utile à l'exploitant et invisible du client.
 */
internal object ProblemResponse {
    fun write(
        response: HttpServletResponse,
        status: HttpStatus,
    ) {
        // Rien n'a encore été écrit sur cette réponse : les gestionnaires de sécurité
        // s'exécutent avant tout contrôleur. Un `reset()` défensif serait un aveu de doute.
        response.status = status.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        response.writer.write(
            """{"type":"about:blank","title":"${status.reasonPhrase}","status":${status.value()}}""",
        )
    }
}
