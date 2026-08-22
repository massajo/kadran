package io.korallis.kadran.platform.security.token

import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Fournit la clé HMAC qui signe et vérifie les jetons d'accès.
 *
 * **Aucun secret de développement n'est écrit dans le dépôt** (spec §10.6). Deux cas, et
 * deux seulement :
 *
 * - `KADRAN_JWT_SECRET` est renseigné : c'est lui, et il doit peser au moins 256 bits — la
 *   taille du condensat de HS256. Une clé plus courte n'ajoute pas de sécurité au-delà de sa
 *   propre longueur, et la RFC 7518 §3.2 l'interdit.
 * - Il ne l'est pas : une clé aléatoire est tirée pour la durée du processus. Le
 *   développement démarre sans configuration, et un déploiement distrait le découvre au
 *   premier redémarrage — tous les jetons deviennent invalides — plutôt que de tourner des
 *   mois avec une clé lisible dans un dépôt public.
 */
object JwtSecretKeySource {
    /**
     * `HmacSHA256` : algorithme de la clé pour la JCA. Nimbus refuse une clé dont
     * l'algorithme ne correspond pas à celui du JWS.
     */
    private const val KEY_ALGORITHM = "HmacSHA256"

    /** 256 bits, la taille de bloc de HMAC-SHA-256. */
    const val MINIMUM_KEY_BYTES: Int = 32

    private val log = LoggerFactory.getLogger(JwtSecretKeySource::class.java)

    fun keyFrom(properties: JwtProperties): SecretKey =
        if (properties.secret.isBlank()) generated() else configured(properties.secret)

    private fun generated(): SecretKey {
        log.warn(
            "KADRAN_JWT_SECRET n'est pas defini : une cle aleatoire est tiree pour la duree du " +
                "processus. Tous les jetons deviendront invalides au prochain redemarrage, et " +
                "deux instances ne se reconnaitront pas. Acceptable en developpement, jamais ailleurs.",
        )
        val bytes = ByteArray(MINIMUM_KEY_BYTES)
        SecureRandom().nextBytes(bytes)
        return SecretKeySpec(bytes, KEY_ALGORITHM)
    }

    private fun configured(secret: String): SecretKey {
        val bytes = decode(secret)
        require(bytes.size >= MINIMUM_KEY_BYTES) {
            "la cle de signature des jetons doit peser au moins $MINIMUM_KEY_BYTES octets, " +
                "elle en fait ${bytes.size}"
        }
        return SecretKeySpec(bytes, KEY_ALGORITHM)
    }

    /**
     * Accepte une clé en base64 comme en texte brut. Le base64 sert à transporter une clé
     * réellement aléatoire dans une variable d'environnement ; le texte brut évite d'imposer
     * un encodage à qui écrit une phrase secrète assez longue.
     *
     * L'interprétation base64 n'est retenue que si elle produit **assez** d'octets. Sans cette
     * condition, une phrase secrète de 32 caractères qui se trouve être du base64 valide —
     * ce qui arrive dès qu'elle n'a ni espace ni accent — serait décodée en 24 octets et
     * rejetée, avec un message parlant d'une longueur que l'auteur croyait avoir respectée.
     */
    private fun decode(secret: String): ByteArray {
        val raw = secret.toByteArray(Charsets.UTF_8)
        val decoded = runCatching { Base64.getDecoder().decode(secret) }.getOrNull()
        return decoded?.takeIf { it.size >= MINIMUM_KEY_BYTES } ?: raw
    }
}
