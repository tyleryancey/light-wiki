package dev.tyler.wiki.parser

import dev.tyler.wiki.parser.HtmlNode.Element

/**
 * Iterative helpers over [HtmlNode] trees, shared by the pipeline and the
 * document model. Sharp edge: never recurse over an untrusted tree —
 * [Element] is a data class whose equals/hashCode/toString already recurse,
 * so every walker here carries its own explicit stack.
 */
object HtmlNodes {

    /**
     * First element in document order satisfying [predicate].
     * [includeSelf] tests [root] itself (callers whose target may BE the
     * root need it); [skip] prunes an element and its whole subtree before
     * the predicate sees it.
     */
    fun findFirst(
        root: Element,
        includeSelf: Boolean = false,
        skip: (Element) -> Boolean = NO_SKIP,
        predicate: (Element) -> Boolean,
    ): Element? {
        val stack = ArrayDeque<HtmlNode>()
        if (includeSelf) {
            stack.addLast(root)
        } else {
            for (i in root.children.indices.reversed()) stack.addLast(root.children[i])
        }
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            if (n !is Element) continue
            if (skip(n)) continue
            if (predicate(n)) return n
            for (i in n.children.indices.reversed()) stack.addLast(n.children[i])
        }
        return null
    }

    /** The `div.mw-parser-output` content root, or [root] itself when absent. */
    fun contentRoot(root: Element): Element =
        findFirst(root, includeSelf = true) { "mw-parser-output" in it.classes } ?: root

    /**
     * True when [el] is one of MediaWiki's two heading shapes: a bare
     * `<h2>`/`<h3>`/`<h4>`, or the `<div class="mw-heading…">` wrapper.
     * The wrapper counts even without a heading child — such a div is
     * chrome to consume, not content to recurse into.
     */
    fun isHeadingShape(el: Element): Boolean =
        el.name in HEADING_NAMES ||
            (el.name == "div" && el.classes.any { it.startsWith("mw-heading") })

    /**
     * The heading element of either shape: [el] itself when it is a bare
     * heading, the first heading child when [el] is the wrapper div, null
     * for anything else.
     */
    fun headingOf(el: Element): Element? {
        if (el.name in HEADING_NAMES) return el
        if (el.name != "div" || el.classes.none { it.startsWith("mw-heading") }) return null
        return el.children.firstOrNull { it is Element && it.name in HEADING_NAMES } as Element?
    }

    private val HEADING_NAMES = setOf("h2", "h3", "h4")
    private val NO_SKIP: (Element) -> Boolean = { false }
}
