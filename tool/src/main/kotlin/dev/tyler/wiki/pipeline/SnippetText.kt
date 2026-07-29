package dev.tyler.wiki.pipeline

private val htmlTagRegex = Regex("<[^>]+>")
private val numericEntityRegex = Regex("&#(x[0-9a-fA-F]+|[0-9]+);")

private val namedEntities = mapOf(
    "&amp;" to "&",
    "&lt;" to "<",
    "&gt;" to ">",
    "&quot;" to "\"",
    "&apos;" to "'",
    "&nbsp;" to " ",
    "&hellip;" to "…",
    "&mdash;" to "—",
    "&ndash;" to "–",
)

/**
 * Convert a Wikipedia search snippet (HTML with `<span class="searchmatch">` highlights
 * and entities) to plain text for the search list. A regex pass is sufficient for the
 * small, well-formed snippets the search endpoint returns — the full parser in
 * `parser/` is reserved for article bodies.
 */
fun stripSnippetHtml(snippet: String): String {
    val withoutTags = htmlTagRegex.replace(snippet, "")
    val withNamedDecoded = namedEntities.entries.fold(withoutTags) { acc, (entity, repl) ->
        acc.replace(entity, repl)
    }
    val withNumericDecoded = numericEntityRegex.replace(withNamedDecoded) { match ->
        val token = match.groupValues[1]
        val codePoint = if (token.startsWith("x") || token.startsWith("X")) {
            token.substring(1).toIntOrNull(16)
        } else {
            token.toIntOrNull()
        }
        if (codePoint != null && Character.isValidCodePoint(codePoint)) {
            String(Character.toChars(codePoint))
        } else {
            match.value
        }
    }
    return withNumericDecoded.trim().withEllipsisIfTruncated()
}

// Wikipedia's search snippet is a fixed-length excerpt that frequently lands
// mid-sentence. Appending "…" signals truncation so the row doesn't read like
// a complete sentence cut off.
private fun String.withEllipsisIfTruncated(): String {
    if (isEmpty()) return this
    val last = trimEnd().lastOrNull() ?: return this
    val endsCleanly = last == '.' || last == '!' || last == '?' ||
        last == '…' || last == '"' || last == ')' || last == ']'
    return if (endsCleanly) this else "$this…"
}
