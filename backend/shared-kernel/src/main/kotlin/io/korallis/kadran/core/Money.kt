package io.korallis.kadran.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency

/**
 * Toute valeur monétaire est un `Money` en centimes (`CLAUDE.md` §2.2) — jamais un `Double`,
 * jamais un `Float`, jamais un `BigDecimal` nu. Un centime est l'unité entière la plus fine
 * qu'aucune plateforme ne subdivise ; travailler dessus élimine toute erreur d'arrondi binaire
 * qu'un `Double` introduirait silencieusement.
 *
 * [plus] et [minus] refusent de mélanger deux devises — additionner des EUR et des USD sans
 * conversion explicite serait un montant faux qui *a l'air* juste.
 */
data class Money(
    val amountCents: Long,
    val currency: Currency,
) {
    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(amountCents + other.amountCents, currency)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return Money(amountCents - other.amountCents, currency)
    }

    operator fun unaryMinus(): Money = Money(-amountCents, currency)

    /**
     * Applique un [Ratio] — une commission, une quote-part — à ce montant.
     *
     * Arrondi au centime le plus proche (`HALF_UP`), explicitement : un arrondi non documenté
     * est une source silencieuse d'écarts d'un centime qui ne se retrouvent qu'au
     * rapprochement.
     */
    operator fun times(ratio: Ratio): Money {
        val scaled = BigDecimal(amountCents).multiply(ratio.value).setScale(0, RoundingMode.HALF_UP)
        return Money(scaled.toLong(), currency)
    }

    val isNegative: Boolean get() = amountCents < 0

    private fun requireSameCurrency(other: Money) {
        require(currency == other.currency) {
            "impossible de combiner ${currency.currencyCode} et ${other.currency.currencyCode}"
        }
    }

    companion object {
        private val EUR: Currency = Currency.getInstance("EUR")

        /** L'activité est facturée en euros (spec) : le raccourci qu'utilisera tout le reste du domaine. */
        fun euroCents(amountCents: Long): Money = Money(amountCents, EUR)

        fun zero(currency: Currency): Money = Money(0, currency)
    }
}

/**
 * Ratio encapsulant un `BigDecimal` (`CLAUDE.md` §2.2) — une commission, une quote-part, un
 * taux de conversion. Aucune contrainte de signe ou d'échelle : un écart déclaré/réel (`M3`)
 * est un ratio légitimement négatif.
 */
data class Ratio(
    val value: BigDecimal,
)
