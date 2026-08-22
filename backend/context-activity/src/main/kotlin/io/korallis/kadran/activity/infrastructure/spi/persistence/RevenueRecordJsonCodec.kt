package io.korallis.kadran.activity.infrastructure.spi.persistence

import io.korallis.kadran.activity.domain.model.RevenueRecordJson
import java.math.BigDecimal

/**
 * Sérialisation de [RevenueRecordJson] vers le texte que jOOQ lie à une colonne `JSONB`
 * (`platform_extras`, `external_refs`, `provenance`, `raw_payload` — spec §7.6).
 *
 * `domain/model` ne dépend ni de Spring ni de Jackson (`CLAUDE.md` §2.4), et aucune des deux
 * bibliothèques n'apparaît sur le classpath des modules de persistance de ce dépôt (jOOQ y
 * est en `api`, pas de starter `-json`). Plutôt que d'ajouter une dépendance pour un besoin
 * limité à un type fermé que ce module contrôle des deux côtés — écriture et lecture — ce
 * fichier écrit et lit lui-même le texte JSON.
 */
internal fun RevenueRecordJson.toJsonText(): String = StringBuilder().also { appendTo(it) }.toString()

private fun RevenueRecordJson.appendTo(sb: StringBuilder) {
    when (this) {
        is RevenueRecordJson.Obj ->
            fields.entries.joinTo(sb, prefix = "{", postfix = "}") { (key, value) ->
                StringBuilder().apply {
                    appendJsonString(key, this)
                    append(':')
                    value.appendTo(this)
                }
            }
        is RevenueRecordJson.Arr ->
            items.joinTo(sb, prefix = "[", postfix = "]") { item ->
                StringBuilder().apply { item.appendTo(this) }
            }
        is RevenueRecordJson.Str -> appendJsonString(value, sb)
        is RevenueRecordJson.Num -> sb.append(value.toPlainString())
        is RevenueRecordJson.Bool -> sb.append(value)
        RevenueRecordJson.Null -> sb.append("null")
    }
}

private const val UNICODE_ESCAPE_HEX_WIDTH = 4
private const val ESCAPE_THRESHOLD = 0x20
private const val HEX_RADIX = 16

private fun appendJsonString(
    value: String,
    sb: StringBuilder,
) {
    sb.append('"')
    for (c in value) {
        when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else ->
                if (c.code < ESCAPE_THRESHOLD) {
                    sb.append("\\u").append(c.code.toString(HEX_RADIX).padStart(UNICODE_ESCAPE_HEX_WIDTH, '0'))
                } else {
                    sb.append(c)
                }
        }
    }
    sb.append('"')
}

/**
 * Lecture d'une colonne `JSONB` vers [RevenueRecordJson].
 *
 * @throws IllegalArgumentException si [text] n'est pas un document JSON bien formé — ce
 *   serait une colonne écrite hors de ce codec, donc un schéma qui ne correspond plus à ce
 *   que ce module écrit.
 */
internal fun parseRevenueRecordJson(text: String): RevenueRecordJson {
    val parser = JsonTextParser(text)
    val value = parser.parseValue()
    parser.skipWhitespace()
    require(parser.isAtEnd()) { "contenu JSON superflu apres la valeur racine" }
    return value
}

/**
 * Décode l'échappement qui commence juste après le `\` situé en [pos] - 1, et rend le
 * caractère qu'il produit avec la position à laquelle reprendre.
 *
 * Fonction libre plutôt que méthode de [JsonTextParser] : la complexité cyclomatique des sept
 * échappements à un caractère plus l'échappement `\u` appartient à la grammaire JSON, pas à
 * l'état de l'analyseur — [JsonTextParser.parseString] n'a besoin que du résultat.
 */
private fun decodeJsonEscape(
    text: String,
    pos: Int,
): Pair<Char, Int> {
    require(pos < text.length) { "sequence d'echappement incomplete" }
    return when (val esc = text[pos]) {
        '"' -> '"' to pos + 1
        '\\' -> '\\' to pos + 1
        '/' -> '/' to pos + 1
        'n' -> '\n' to pos + 1
        'r' -> '\r' to pos + 1
        't' -> '\t' to pos + 1
        'b' -> '\b' to pos + 1
        'f' -> '\u000C' to pos + 1
        'u' -> {
            val hexStart = pos + 1
            val hexEnd = hexStart + UNICODE_ESCAPE_HEX_WIDTH
            require(hexEnd <= text.length) { "echappement unicode incomplet" }
            text.substring(hexStart, hexEnd).toInt(HEX_RADIX).toChar() to hexEnd
        }
        else -> error("sequence d'echappement inconnue : \\$esc")
    }
}

/**
 * Analyseur récursif-descendant minimal, borné à la grammaire JSON (RFC 8259).
 *
 * Onze petites fonctions à un seul travail chacune valent mieux que quelques grandes qui
 * mélangeraient les niveaux de la grammaire — `TooManyFunctions` désactivé sciemment pour
 * cette seule classe, dont c'est justement le métier.
 */
@Suppress("TooManyFunctions")
private class JsonTextParser(
    private val text: String,
) {
    private var pos = 0

    fun isAtEnd(): Boolean = pos >= text.length

    fun skipWhitespace() {
        while (pos < text.length && text[pos].isWhitespace()) pos++
    }

    fun parseValue(): RevenueRecordJson {
        skipWhitespace()
        require(!isAtEnd()) { "JSON tronque a la position $pos" }
        return when (text[pos]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> RevenueRecordJson.Str(parseString())
            't' -> parseLiteral("true", RevenueRecordJson.Bool(true))
            'f' -> parseLiteral("false", RevenueRecordJson.Bool(false))
            'n' -> parseLiteral("null", RevenueRecordJson.Null)
            else -> RevenueRecordJson.Num(parseNumber())
        }
    }

    private fun parseObject(): RevenueRecordJson.Obj {
        expectChar('{')
        val fields = LinkedHashMap<String, RevenueRecordJson>()
        skipWhitespace()
        if (peek() == '}') {
            pos++
            return RevenueRecordJson.Obj(fields)
        }
        while (true) {
            skipWhitespace()
            val key = parseString()
            skipWhitespace()
            expectChar(':')
            fields[key] = parseValue()
            skipWhitespace()
            if (advanceOnSeparatorOrClose(',', '}')) break
        }
        return RevenueRecordJson.Obj(fields)
    }

    private fun parseArray(): RevenueRecordJson.Arr {
        expectChar('[')
        val items = mutableListOf<RevenueRecordJson>()
        skipWhitespace()
        if (peek() == ']') {
            pos++
            return RevenueRecordJson.Arr(items)
        }
        while (true) {
            items += parseValue()
            skipWhitespace()
            if (advanceOnSeparatorOrClose(',', ']')) break
        }
        return RevenueRecordJson.Arr(items)
    }

    /** @return vrai si le caractère de fermeture a été consommé — la boucle appelante doit s'arrêter. */
    private fun advanceOnSeparatorOrClose(
        separator: Char,
        close: Char,
    ): Boolean =
        when (peek()) {
            separator -> {
                pos++
                false
            }
            close -> {
                pos++
                true
            }
            else -> error("attendu '$separator' ou '$close' a la position $pos")
        }

    /**
     * Le decodage d'un echappement vit hors de la classe (`decodeJsonEscape`) : le garder ici
     * ferait grimper `parseString` au-dela du seuil de complexite cyclomatique de detekt, pour
     * une fonction qui n'a besoin d'aucun autre etat que la position courante.
     */
    private fun parseString(): String {
        expectChar('"')
        val sb = StringBuilder()
        while (true) {
            require(pos < text.length) { "chaine JSON non fermee" }
            when (val c = text[pos++]) {
                '"' -> return sb.toString()
                '\\' -> {
                    val (decoded, next) = decodeJsonEscape(text, pos)
                    sb.append(decoded)
                    pos = next
                }
                else -> sb.append(c)
            }
        }
    }

    private fun parseNumber(): BigDecimal {
        val start = pos
        if (peek() == '-') pos++
        while (pos < text.length && (text[pos].isDigit() || text[pos] in NUMBER_CHARS)) pos++
        val raw = text.substring(start, pos)
        require(raw.isNotEmpty()) { "nombre JSON invalide a la position $start" }
        return BigDecimal(raw)
    }

    private fun <T : RevenueRecordJson> parseLiteral(
        literal: String,
        value: T,
    ): T {
        require(text.regionMatches(pos, literal, 0, literal.length)) { "attendu '$literal' a la position $pos" }
        pos += literal.length
        return value
    }

    private fun peek(): Char {
        require(pos < text.length) { "JSON tronque a la position $pos" }
        return text[pos]
    }

    private fun expectChar(expected: Char) {
        require(!isAtEnd() && text[pos] == expected) { "attendu '$expected' a la position $pos" }
        pos++
    }

    private companion object {
        val NUMBER_CHARS = charArrayOf('.', 'e', 'E', '+', '-')
    }
}
