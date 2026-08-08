package dev.tyler.wiki.parser

import dev.tyler.wiki.parser.HtmlToken.EndTag
import dev.tyler.wiki.parser.HtmlToken.StartTag
import dev.tyler.wiki.parser.HtmlToken.Text

/**
 * Single-pass character scanner over MediaWiki `action=parse` HTML output.
 * Tolerant by construction: anything that does not scan as markup is text.
 * Comments, doctypes, and `<script>`/`<style>` elements (including their
 * content) are swallowed — downstream code has no consumer for them.
 */
object HtmlLexer {

    private val RAW_SKIP_TAGS = setOf("script", "style")

    fun lex(input: String): List<HtmlToken> {
        val tokens = ArrayList<HtmlToken>()
        val text = StringBuilder()
        var i = 0
        val n = input.length

        fun flushText() {
            if (text.isNotEmpty()) {
                tokens.add(Text(text.toString()))
                text.clear()
            }
        }

        while (i < n) {
            val c = input[i]
            if (c == '<' && i + 1 < n) {
                val next = input[i + 1]
                when {
                    next == '/' -> {
                        val gt = input.indexOf('>', i + 2)
                        if (gt == -1) {
                            // Truncated end tag at EOF: rest is text.
                            text.append(input, i, n)
                            i = n
                        } else {
                            flushText()
                            tokens.add(EndTag(input.substring(i + 2, gt).trim().lowercase()))
                            i = gt + 1
                        }
                    }
                    next.isLetter() -> {
                        flushText()
                        i = lexStartTag(input, i, tokens)
                    }
                    next == '!' || next == '?' -> {
                        flushText()
                        i = skipDeclaration(input, i)
                    }
                    else -> {
                        text.append(c)
                        i++
                    }
                }
            } else if (c == '&') {
                i = HtmlEntities.appendEntity(input, i, text)
            } else {
                text.append(c)
                i++
            }
        }
        flushText()
        return tokens
    }

    /**
     * Scans a start tag whose '<' is at [lt]. Returns the index to resume at.
     * A tag never closed by '>' is emitted as raw text (tolerance).
     * `<script>`/`<style>` elements are swallowed whole, content included.
     */
    private fun lexStartTag(input: String, lt: Int, tokens: MutableList<HtmlToken>): Int {
        val n = input.length
        var i = lt + 1
        val nameStart = i
        while (i < n && (input[i].isLetterOrDigit() || input[i] == '-')) i++
        val name = input.substring(nameStart, i).lowercase()

        val attrs = LinkedHashMap<String, String>()
        var selfClosing = false
        while (i < n && input[i] != '>') {
            when {
                input[i].isWhitespace() -> i++
                input[i] == '/' -> {
                    selfClosing = true
                    i++
                }
                else -> {
                    selfClosing = false
                    val attrStart = i
                    while (i < n && input[i] != '=' && input[i] != '>' && input[i] != '/' && !input[i].isWhitespace()) i++
                    val attrName = input.substring(attrStart, i).lowercase()
                    var value = ""
                    // Tolerate whitespace around '=' (`href = "x"`).
                    var afterName = i
                    while (afterName < n && input[afterName].isWhitespace()) afterName++
                    if (afterName < n && input[afterName] == '=') {
                        i = afterName + 1
                        while (i < n && input[i].isWhitespace()) i++
                        if (i < n && (input[i] == '"' || input[i] == '\'')) {
                            val quote = input[i]
                            i++
                            val valueStart = i
                            while (i < n && input[i] != quote) i++
                            value = input.substring(valueStart, i)
                            if (i < n) i++ // closing quote
                        } else {
                            val valueStart = i
                            while (i < n && input[i] != '>' && !input[i].isWhitespace()) i++
                            value = input.substring(valueStart, i)
                        }
                    }
                    if (attrName.isNotEmpty()) attrs[attrName] = HtmlEntities.decodeAll(value)
                }
            }
        }
        if (i >= n) {
            // Truncated start tag at EOF: emit the raw slice as text.
            tokens.add(Text(input.substring(lt)))
            return n
        }
        i++ // consume '>'

        if (name in RAW_SKIP_TAGS) {
            // Swallow raw content through the matching close tag (or EOF).
            val close = "</$name"
            val at = input.indexOf(close, i, ignoreCase = true)
            if (at == -1) return n
            val gt = input.indexOf('>', at)
            return if (gt == -1) n else gt + 1
        }

        tokens.add(StartTag(name, attrs, selfClosing))
        return i
    }

    /** Skips `<!-- ... -->`, `<!DOCTYPE ...>`, `<![CDATA[...]]>`, `<? ... >`. Never throws. */
    private fun skipDeclaration(input: String, lt: Int): Int {
        val n = input.length
        if (input.startsWith("<!--", lt)) {
            val end = input.indexOf("-->", lt + 4)
            return if (end == -1) n else end + 3
        }
        val gt = input.indexOf('>', lt + 2)
        return if (gt == -1) n else gt + 1
    }

    // Entity decoding lives in HtmlEntities (shared with the snippet strip).
}
