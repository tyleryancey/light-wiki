package dev.tyler.wiki.parser

/** Tree produced by [HtmlTree]. Pure data; the pipeline walks it. */
sealed interface HtmlNode {
    data class Element(
        val name: String,
        val attrs: Map<String, String> = emptyMap(),
        val children: List<HtmlNode> = emptyList(),
    ) : HtmlNode {
        /**
         * Whitespace-separated `class` attribute as a set; empty when absent.
         * Eager, not a computed get(): membership is tested on every element
         * across five pipeline passes plus extraction, and a per-access
         * split allocated ~100k transient sets on a large article.
         */
        val classes: Set<String> =
            attrs["class"]?.splitToSequence(' ', '\t', '\n', '\r', '\u000C')
                ?.filterTo(LinkedHashSet()) { it.isNotEmpty() } ?: emptySet()
    }

    data class TextNode(val text: String) : HtmlNode
}
