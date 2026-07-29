package dev.tyler.wiki.pipeline

import dev.tyler.wiki.parser.HtmlNode
import dev.tyler.wiki.parser.HtmlTree

/** Shared helpers for pipeline tests. All walkers are iterative (sharp edge: no recursion over untrusted trees). */
object TreeTestSupport {

    fun process(html: String): HtmlNode.Element = ArticlePipeline.process(HtmlTree.parse(html))

    /** All text content, in document order, concatenated. */
    fun flatText(root: HtmlNode): String {
        val sb = StringBuilder()
        val stack = ArrayDeque<HtmlNode>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            when (val n = stack.removeLast()) {
                is HtmlNode.TextNode -> sb.append(n.text)
                is HtmlNode.Element -> for (i in n.children.indices.reversed()) stack.addLast(n.children[i])
            }
        }
        return sb.toString()
    }

    /** Every element in document order. */
    fun allElements(root: HtmlNode): List<HtmlNode.Element> {
        val out = ArrayList<HtmlNode.Element>()
        val stack = ArrayDeque<HtmlNode>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            when (val n = stack.removeLast()) {
                is HtmlNode.TextNode -> {}
                is HtmlNode.Element -> {
                    out.add(n)
                    for (i in n.children.indices.reversed()) stack.addLast(n.children[i])
                }
            }
        }
        return out
    }

    fun elementsNamed(root: HtmlNode, name: String): List<HtmlNode.Element> =
        allElements(root).filter { it.name == name }
}
