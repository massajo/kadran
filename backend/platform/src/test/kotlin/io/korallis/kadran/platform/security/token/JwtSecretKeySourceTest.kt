package io.korallis.kadran.platform.security.token

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.Base64

class JwtSecretKeySourceTest :
    StringSpec({
        "an unset secret yields a fresh random key rather than a well-known one" {
            val first = JwtSecretKeySource.keyFrom(JwtProperties(secret = ""))
            val second = JwtSecretKeySource.keyFrom(JwtProperties(secret = ""))

            // Deux clés distinctes : il n'y a aucun secret de développement dans le dépôt,
            // donc rien qu'un attaquant puisse lire pour forger un jeton.
            first.encoded.toList() shouldNotBe second.encoded.toList()
            first.encoded.size shouldBe JwtSecretKeySource.MINIMUM_KEY_BYTES
        }

        "a base64 secret is decoded rather than taken for its characters" {
            val material = ByteArray(JwtSecretKeySource.MINIMUM_KEY_BYTES) { it.toByte() }
            val encoded = Base64.getEncoder().encodeToString(material)

            JwtSecretKeySource.keyFrom(JwtProperties(secret = encoded)).encoded.toList() shouldBe material.toList()
        }

        "a plain passphrase that happens to be valid base64 is not silently shortened" {
            // 32 caractères sans accent ni espace : décodés en base64 ils ne pèseraient que
            // 24 octets, et le message d'erreur parlerait d'une longueur que l'auteur croit
            // avoir respectée.
            val passphrase = "abcdefghijklmnopqrstuvwxyzABCDEF"

            JwtSecretKeySource
                .keyFrom(JwtProperties(secret = passphrase))
                .encoded
                .size shouldBe passphrase.length
        }

        "a secret shorter than the digest it protects is refused" {
            shouldThrow<IllegalArgumentException> {
                JwtSecretKeySource.keyFrom(JwtProperties(secret = "trop-court"))
            }
        }
    })
