package eu.kadran.app

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Point d'entrée trivial servant à valider la chaîne de bout en bout — du code source
 * jusqu'à l'image publiée (KDN-3, définition de fin de l'EPIC KDN-1).
 *
 * Il sera remplacé par les contrôleurs des contextes bornés et n'a pas vocation à durer.
 */
@RestController
class HelloController {
    @GetMapping("/hello")
    fun hello(): Map<String, String> = mapOf("application" to "kadran", "status" to "up")
}
