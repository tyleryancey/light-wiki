package dev.tyler.wiki.parser

/** Tree produced by [HtmlTree]. Pure data; the pipeline walks it. */
sealed interface HtmlNode {
    data class Element(
        val name: String,
        val attrs: Map<String, String> = emptyMap(),
        val children: List<HtmlNode> = emptyList(),
    ) : HtmlNode {
        /** Space-separated `class` attribute as a set; empty when absent. */
        val classes: Set<String>
            get() = attrs["class"]?.split(' ')?.filterTo(LinkedHashSet()) { it.isNotEmpty() } ?: emptySet()
    }

    data class TextNode(val text: String) : HtmlNode
}
