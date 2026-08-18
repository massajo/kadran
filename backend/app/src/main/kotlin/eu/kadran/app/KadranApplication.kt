package eu.kadran.app

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Point d'entrée de l'application.
 *
 * Le module `app` ne porte aucune règle métier : il compose les contextes bornés
 * et fournit le bootstrap Spring (spec §10.1).
 */
@SpringBootApplication(scanBasePackages = ["eu.kadran"])
class KadranApplication

// `runApplication` impose le spread sur `args` : la copie de tableau qu'il provoque a lieu
// une fois au demarrage, ce qui rend l'avertissement de performance sans objet ici.
@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<KadranApplication>(*args)
}
