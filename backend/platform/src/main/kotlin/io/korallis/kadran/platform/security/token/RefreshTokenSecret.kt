package io.korallis.kadran.platform.security.token

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.HexFormat

/**
 * Empreinte d'un jeton de rafraîchissement, telle qu'elle est conservée côté serveur.
 *
 * **SHA-256 nu est ici le bon choix, et n'entre pas en contradiction avec l'interdiction du
 * condensat simple pour les mots de passe.** Un mot de passe est choisi par un humain :
 * quelques dizaines de bits d'entropie au mieux, donc énumérable, d'où bcrypt et son coût
 * réglable. Un [RefreshTokenSecret] est tiré de 256 bits aléatoires : il n'y a rien à
 * énumérer, et un KDF coûteux n'ajouterait que de la latence sur le chemin critique.
 */
@JvmInline
value class RefreshTokenDigest(
    val value: String,
) {
    /** Jamais la valeur : un condensat en clair de log reste un identifiant de session. */
    override fun toString(): String = "RefreshTokenDigest(…)"
}

/**
 * Le jeton de rafraîchissement remis au client — **opaque, jamais un JWT**.
 *
 * Un JWT de rafraîchissement se valide par sa seule signature, donc reste utilisable après
 * une déconnexion : la révocation exigerait de toute façon un état côté serveur. Autant que
 * le jeton soit une simple référence à cet état, ce qui rend la rotation (§ [RefreshTokenStore])
 * et la révocation immédiates et sans ambiguïté.
 */
@JvmInline
value class RefreshTokenSecret private constructor(
    val value: String,
) {
    /** Jamais la valeur : elle vaut un mot de passe le temps de sa validité. */
    override fun toString(): String = "RefreshTokenSecret(…)"

    fun digest(): RefreshTokenDigest {
        val bytes = MessageDigest.getInstance(DIGEST_ALGORITHM).digest(value.toByteArray(Charsets.US_ASCII))
        return RefreshTokenDigest(HexFormat.of().formatHex(bytes))
    }

    companion object {
        private const val DIGEST_ALGORITHM = "SHA-256"

        /** 256 bits : au-delà de toute énumération, et court en base64url. */
        private const val ENTROPY_BYTES = 32

        /**
         * Base64 **url-safe et sans remplissage** : le jeton traverse des en-têtes, des
         * corps JSON et parfois un stockage client. Le `=` et le `+` de l'alphabet standard
         * s'y font ré-encoder au petit bonheur, et un jeton ré-encodé ne correspond plus à
         * son empreinte.
         */
        private val ENCODER: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()

        fun generate(random: SecureRandom): RefreshTokenSecret {
            val bytes = ByteArray(ENTROPY_BYTES)
            random.nextBytes(bytes)
            return RefreshTokenSecret(ENCODER.encodeToString(bytes))
        }

        /** Reprend la valeur présentée par le client, sans jugement : c'est l'empreinte qui tranche. */
        fun of(raw: String): RefreshTokenSecret = RefreshTokenSecret(raw)
    }
}
