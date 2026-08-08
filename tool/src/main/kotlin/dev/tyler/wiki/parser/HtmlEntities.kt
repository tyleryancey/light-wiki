package dev.tyler.wiki.parser

/**
 * HTML entity decoding, shared by [HtmlLexer] (article bodies, char-stream
 * path) and the search-snippet strip (whole-string path). One table, one
 * tolerance policy: unknown or malformed entities stay literal, and decoding
 * happens exactly once — never over its own output.
 */
internal object HtmlEntities {

    private const val MAX_ENTITY_LEN = 12 // longest name + '&' + ';'

    private val NAMED_ENTITIES = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "nbsp" to " ", "shy" to "­", "thinsp" to " ",
        "ensp" to " ", "emsp" to " ",
        "ndash" to "–", "mdash" to "—", "minus" to "−",
        "hellip" to "…", "middot" to "·", "bull" to "•",
        "ldquo" to "“", "rdquo" to "”", "lsquo" to "‘", "rsquo" to "’",
        "laquo" to "«", "raquo" to "»",
        "deg" to "°", "times" to "×", "divide" to "÷", "plusmn" to "±",
        "frac12" to "½", "frac14" to "¼", "frac34" to "¾",
        "sup1" to "¹", "sup2" to "²", "sup3" to "³",
        "micro" to "µ", "sect" to "§", "para" to "¶",
        "copy" to "©", "reg" to "®", "trade" to "™",
        "dagger" to "†", "Dagger" to "‡",
        "prime" to "′", "Prime" to "″",
        "larr" to "←", "rarr" to "→", "uarr" to "↑", "darr" to "↓",
        "euro" to "€", "pound" to "£", "yen" to "¥", "cent" to "¢",
    )

    /**
     * Attempts to decode one entity at [amp] (index of '&'), appending the
     * result (or the raw '&' when it is not an entity) to [sb]. Returns the
     * index to continue scanning from.
     */
    fun appendEntity(input: String, amp: Int, sb: StringBuilder): Int {
        // Bounded scan: an entity fits in MAX_ENTITY_LEN chars or it isn't one.
        // (An unbounded indexOf here is O(n²) on ampersand floods — M2 review.)
        var semi = -1
        val searchEnd = minOf(input.length, amp + MAX_ENTITY_LEN + 1)
        for (j in amp + 1 until searchEnd) {
            if (input[j] == ';') {
                semi = j
                break
            }
        }
        if (semi == -1) {
            sb.append('&')
            return amp + 1
        }
        val body = input.substring(amp + 1, semi)
        if (body.startsWith("#")) {
            val code = if (body.startsWith("#x") || body.startsWith("#X")) {
                body.substring(2).toIntOrNull(16)
            } else {
                body.substring(1).toIntOrNull(10)
            }
            if (code != null && code in 1..0x10FFFF && code !in 0xD800..0xDFFF) {
                sb.appendCodePoint(code)
                return semi + 1
            }
            sb.append('&')
            return amp + 1
        }
        val decoded = NAMED_ENTITIES[body]
        if (decoded != null) {
            sb.append(decoded)
            return semi + 1
        }
        sb.append('&')
        return amp + 1
    }

    /** Decodes every entity in [value] in one pass. */
    fun decodeAll(value: String): String {
        if ('&' !in value) return value
        val sb = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            if (value[i] == '&') {
                i = appendEntity(value, i, sb)
            } else {
                sb.append(value[i])
                i++
            }
        }
        return sb.toString()
    }
}
