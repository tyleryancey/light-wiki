package dev.tyler.wiki.parser

import dev.tyler.wiki.parser.HtmlToken.EndTag
import dev.tyler.wiki.parser.HtmlToken.StartTag
import dev.tyler.wiki.parser.HtmlToken.Text

/**
 * Tolerant tree builder for the lexer's token stream. Never throws on
 * real-world input: mismatched end tags close through, stray end tags are
 * ignored, unclosed elements close at EOF, unknown tags are ordinary
 * containers.
 */
object HtmlTree {
    /** Synthetic root element name. */
    const val ROOT = "#root"

    /** HTML void elements: never take children, never need an end tag. */
    private val VOID_ELEMENTS = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input",
        "link", "meta", "source", "track", "wbr",
    )

    /** `<x>` auto-closes an open `<x>` when it is the innermost element. */
    private val SELF_NESTING_AUTO_CLOSE = setOf("p", "li", "dt", "dd", "tr", "td", "th", "option")

    private class OpenElement(val name: String, val attrs: Map<String, String>) {
        val children = ArrayList<HtmlNode>()
        fun toElement() = HtmlNode.Element(name, attrs, children)
    }

    fun parse(html: String): HtmlNode.Element = build(HtmlLexer.lex(html))

    fun build(tokens: List<HtmlToken>): HtmlNode.Element {
        val stack = ArrayList<OpenElement>()
        stack.add(OpenElement(ROOT, emptyMap()))
        // Per-name open counts give O(1) rejection of end tags that match
        // nothing (a full-stack scan per stray end tag is O(n²) — M2 review).
        val openCounts = HashMap<String, Int>()

        fun closeTop() {
            val closed = stack.removeAt(stack.size - 1)
            openCounts.merge(closed.name, -1, Int::plus)
            stack.last().children.add(closed.toElement())
        }

        for (token in tokens) {
            when (token) {
                is Text -> stack.last().children.add(HtmlNode.TextNode(token.text))
                is StartTag -> {
                    if (token.name in SELF_NESTING_AUTO_CLOSE && stack.last().name == token.name) {
                        closeTop()
                    }
                    if (token.selfClosing || token.name in VOID_ELEMENTS) {
                        stack.last().children.add(HtmlNode.Element(token.name, token.attrs))
                    } else {
                        stack.add(OpenElement(token.name, token.attrs))
                        openCounts.merge(token.name, 1, Int::plus)
                    }
                }
                is EndTag -> {
                    if (token.name in VOID_ELEMENTS) continue // e.g. stray </br>
                    if ((openCounts[token.name] ?: 0) <= 0) continue // nothing to close
                    // Close through to the matching open element (guaranteed present).
                    val matchIndex = stack.indexOfLast { it.name == token.name }
                    if (matchIndex >= 1) { // never close the synthetic root
                        while (stack.size > matchIndex) closeTop()
                    }
                }
            }
        }
        while (stack.size > 1) closeTop()
        return stack[0].toElement()
    }
}
