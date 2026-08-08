package dev.tyler.wiki.pipeline

import dev.tyler.wiki.parser.HtmlEntities

private val htmlTagRegex = Regex("<[^>]+>")

/**
 * Convert a Wikipedia search snippet (HTML with `<span class="searchmatch">` highlights
 * and entities) to plain text for the search list. A regex pass is sufficient for the
 * small, well-formed snippets the search endpoint returns — the full parser in
 * `parser/` is reserved for article bodies; entity decoding is the parser's
 * shared table, so snippets and articles render the same glyphs.
 */
fun stripSnippetHtml(snippet: String): String {
    val withoutTags = htmlTagRegex.replace(snippet, "")
    // The shared table decodes &nbsp; to U+00A0; a snippet is a one-line list
    // row where a non-breaking space would defeat trim and the ellipsis
    // heuristic, so normalize it to a plain space.
    val decoded = HtmlEntities.decodeAll(withoutTags).replace('\u00A0', ' ')
    return decoded.trim().withEllipsisIfTruncated()
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
