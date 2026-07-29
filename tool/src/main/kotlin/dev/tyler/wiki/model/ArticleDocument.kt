package dev.tyler.wiki.model

import dev.tyler.wiki.parser.HtmlNode
import dev.tyler.wiki.parser.HtmlNode.Element
import dev.tyler.wiki.parser.HtmlNode.TextNode

/** A run of text with inline styling. The only inline styles v1 renders. */
data class InlineSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
)

/** List item: its own text spans plus one level of children (v1 flattens deeper nesting into level 2). */
data class ListItem(
    val spans: List<InlineSpan>,
    val children: List<ListItem> = emptyList(),
)

/** One label/value line of an infobox card. */
data class InfoboxRow(val label: String, val value: String)

/**
 * The renderer's entire input vocabulary. Anything the extractor does not
 * recognize is dropped silently — this is a link-stripped reader, not an
 * archival browser.
 */
sealed interface Block {
    data class Heading(val level: Int, val text: String) : Block
    data class Paragraph(val spans: List<InlineSpan>) : Block
    data class ListBlock(val ordered: Boolean, val items: List<ListItem>) : Block
    data class Blockquote(val spans: List<InlineSpan>) : Block
    data class Figure(val src: String, val width: Int?, val height: Int?, val caption: String?) : Block
    data class InfoboxCard(val title: String?, val rows: List<InfoboxRow>) : Block
    data class SimpleTable(val headers: List<String>, val rows: List<List<String>>) : Block
    data class MathImage(val src: String) : Block
}

/** The parsed, pipeline-cleaned article as an ordered list of blocks. */
data class ArticleDocument(val blocks: List<Block>) {

    companion object {

        /** Containers whose children are extracted in place. */
        private val TRANSPARENT_CONTAINERS = setOf(
            "div", "section", "center", "main", "article", "dl", "dd",
        )

        /** Depth cap for container recursion (real articles nest ~30 levels). */
        private const val MAX_DEPTH = 64

        fun from(root: Element): ArticleDocument {
            val container = findFirst(root) { "mw-parser-output" in it.classes } ?: root
            val blocks = ArrayList<Block>()
            extractBlocks(container.children, blocks, 0)
            return ArticleDocument(blocks)
        }

        private fun extractBlocks(nodes: List<HtmlNode>, out: MutableList<Block>, depth: Int) {
            if (depth > MAX_DEPTH) return
            for (node in nodes) {
                val el = node as? Element ?: continue
                when {
                    el.name in setOf("h2", "h3", "h4") -> {
                        val text = inlineText(el)
                        if (text.isNotEmpty()) out.add(Block.Heading(el.name[1].digitToInt(), text))
                    }
                    el.name == "div" && el.classes.any { it == "mw-heading" || it.startsWith("mw-heading") } -> {
                        val h = el.children.firstOrNull {
                            it is Element && it.name in setOf("h2", "h3", "h4")
                        } as Element?
                        if (h != null) {
                            val text = inlineText(h)
                            if (text.isNotEmpty()) out.add(Block.Heading(h.name[1].digitToInt(), text))
                        }
                    }
                    el.name == "p" -> {
                        val spans = inlineSpans(el)
                        if (spans.isNotEmpty()) out.add(Block.Paragraph(spans))
                    }
                    el.name == "ul" || el.name == "ol" -> {
                        val items = listItems(el, level = 1)
                        if (items.isNotEmpty()) out.add(Block.ListBlock(el.name == "ol", items))
                    }
                    el.name == "blockquote" -> {
                        val spans = inlineSpans(el)
                        if (spans.isNotEmpty()) out.add(Block.Blockquote(spans))
                    }
                    el.name == "figure" -> extractFigure(el)?.let(out::add)
                    el.name == "table" && "infobox" in el.classes -> out.add(extractInfobox(el))
                    el.name == "table" -> extractSimpleTable(el)?.let(out::add)
                    el.name == "span" && "mwe-math-element" in el.classes -> {
                        mathSrc(el)?.let { out.add(Block.MathImage(it)) }
                    }
                    el.name in TRANSPARENT_CONTAINERS -> extractBlocks(el.children, out, depth + 1)
                    // else: unknown block — dropped silently.
                }
            }
        }

        // --- Lists ---

        private fun listItems(list: Element, level: Int): List<ListItem> {
            val items = ArrayList<ListItem>()
            for (child in list.children) {
                if (child !is Element || child.name != "li") continue
                val spans = inlineSpans(child, excludeNestedLists = true)
                val children = if (level == 1) {
                    // Level 2 collects the li's nested lists; anything deeper
                    // flattens into the same level-2 run.
                    val nested = ArrayList<ListItem>()
                    collectNestedItems(child, nested)
                    nested
                } else {
                    emptyList()
                }
                if (spans.isNotEmpty() || children.isNotEmpty()) {
                    items.add(ListItem(spans, children))
                }
            }
            return items
        }

        /**
         * Flattens every list nested under [li] (any depth) into one level of
         * items, in document order: each item's own descendants are emitted
         * immediately after it, before its next sibling (M3 review finding 1).
         * Recursion is bounded by MAX_DEPTH.
         */
        private fun collectNestedItems(li: Element, out: MutableList<ListItem>, depth: Int = 0) {
            if (depth > MAX_DEPTH) return
            for (child in li.children) {
                if (child !is Element) continue
                if (child.name == "ul" || child.name == "ol") {
                    for (grand in child.children) {
                        if (grand is Element && grand.name == "li") {
                            val spans = inlineSpans(grand, excludeNestedLists = true)
                            if (spans.isNotEmpty()) out.add(ListItem(spans))
                            collectNestedItems(grand, out, depth + 1)
                        }
                    }
                } else {
                    collectNestedItems(child, out, depth + 1)
                }
            }
        }

        // --- Figures, infobox, tables, math ---

        private fun extractFigure(figure: Element): Block.Figure? {
            val img = findFirst(figure) { it.name == "img" } ?: return null
            val src = img.attrs["src"]?.takeIf { it.isNotBlank() } ?: return null
            val caption = figure.children.firstOrNull {
                it is Element && it.name == "figcaption"
            }?.let { inlineText(it as Element).takeIf(String::isNotEmpty) }
            return Block.Figure(
                src = src,
                width = img.attrs["width"]?.toIntOrNull(),
                height = img.attrs["height"]?.toIntOrNull(),
                caption = caption,
            )
        }

        private fun extractInfobox(table: Element): Block.InfoboxCard {
            val title = findFirst(table) { it.name == "caption" }
                ?.let { inlineText(it).takeIf(String::isNotEmpty) }
            val rows = ArrayList<InfoboxRow>()
            for (tr in tableRows(table)) {
                val th = tr.children.firstOrNull { it is Element && it.name == "th" } as Element?
                val td = tr.children.firstOrNull { it is Element && it.name == "td" } as Element?
                if (th != null && td != null) {
                    val label = inlineText(th)
                    val value = inlineText(td)
                    if (label.isNotEmpty() && value.isNotEmpty()) rows.add(InfoboxRow(label, value))
                }
            }
            return Block.InfoboxCard(title, rows)
        }

        private fun extractSimpleTable(table: Element): Block.SimpleTable? {
            val trs = tableRows(table)
            if (trs.isEmpty()) return null
            var headers = emptyList<String>()
            var dataStart = 0
            val firstCells = trs[0].children.filterIsInstance<Element>().filter { it.name == "th" || it.name == "td" }
            if (firstCells.isNotEmpty() && firstCells.all { it.name == "th" }) {
                headers = firstCells.map { inlineText(it) }
                dataStart = 1
            }
            val rows = trs.drop(dataStart).map { tr ->
                tr.children.filterIsInstance<Element>()
                    .filter { it.name == "td" || it.name == "th" }
                    .map { inlineText(it) }
            }.filter { it.isNotEmpty() }
            if (headers.isEmpty() && rows.isEmpty()) return null
            return Block.SimpleTable(headers, rows)
        }

        /** Direct rows of [table]: tr children of the table and of its row-group children. */
        private fun tableRows(table: Element): List<Element> {
            val rows = ArrayList<Element>()
            for (child in table.children) {
                if (child !is Element) continue
                when (child.name) {
                    "tr" -> rows.add(child)
                    "tbody", "thead", "tfoot" -> for (tr in child.children) {
                        if (tr is Element && tr.name == "tr") rows.add(tr)
                    }
                }
            }
            return rows
        }

        private fun mathSrc(mathSpan: Element): String? =
            findFirst(mathSpan) { it.name == "img" }?.attrs?.get("src")?.takeIf { it.isNotBlank() }

        // --- Inline spans ---

        private class RawPiece(val text: String, val bold: Boolean, val italic: Boolean)

        private class Frame(val node: HtmlNode, val bold: Boolean, val italic: Boolean)

        /**
         * Collects styled text pieces under [root] in document order —
         * iterative, with bold/italic state carried on the stack. Inline math
         * contributes its alt text; stray inline images contribute nothing.
         */
        private fun inlineSpans(root: Element, excludeNestedLists: Boolean = false): List<InlineSpan> {
            val pieces = ArrayList<RawPiece>()
            val stack = ArrayDeque<Frame>()

            fun pushChildren(el: Element, bold: Boolean, italic: Boolean) {
                for (i in el.children.indices.reversed()) {
                    stack.addLast(Frame(el.children[i], bold, italic))
                }
            }

            pushChildren(root, bold = false, italic = false)
            while (stack.isNotEmpty()) {
                val frame = stack.removeLast()
                when (val n = frame.node) {
                    is TextNode -> pieces.add(RawPiece(n.text, frame.bold, frame.italic))
                    is Element -> {
                        when {
                            // Hidden microformat spans (bday etc.) must not leak as text.
                            n.attrs["style"]?.replace(WHITESPACE_RUN, "")
                                ?.contains("display:none", ignoreCase = true) == true -> {}
                            n.name == "b" || n.name == "strong" ->
                                pushChildren(n, bold = true, italic = frame.italic)
                            n.name == "i" || n.name == "em" ->
                                pushChildren(n, bold = frame.bold, italic = true)
                            n.name == "span" && "mwe-math-element" in n.classes -> {
                                val alt = findFirst(n) { it.name == "img" }?.attrs?.get("alt")
                                if (!alt.isNullOrBlank()) pieces.add(RawPiece(alt, frame.bold, frame.italic))
                            }
                            n.name == "img" -> {} // stray inline image: contributes nothing
                            n.name == "br" -> pieces.add(RawPiece(" ", frame.bold, frame.italic))
                            excludeNestedLists && (n.name == "ul" || n.name == "ol") -> {}
                            else -> pushChildren(n, frame.bold, frame.italic)
                        }
                    }
                }
            }
            return mergeAndClean(pieces)
        }

        private val WHITESPACE_RUN = Regex("[ \t\n\r]+")

        private fun mergeAndClean(pieces: List<RawPiece>): List<InlineSpan> {
            if (pieces.isEmpty()) return emptyList()
            // Merge adjacent same-style pieces.
            val merged = ArrayList<RawPiece>()
            for (p in pieces) {
                val last = merged.lastOrNull()
                if (last != null && last.bold == p.bold && last.italic == p.italic) {
                    merged[merged.size - 1] = RawPiece(last.text + p.text, p.bold, p.italic)
                } else {
                    merged.add(p)
                }
            }
            // Collapse whitespace runs; trim the outer edges of the whole run.
            val spans = merged.map { InlineSpan(WHITESPACE_RUN.replace(it.text, " "), it.bold, it.italic) }
                .toMutableList()
            if (spans.isNotEmpty()) {
                spans[0] = spans[0].copy(text = spans[0].text.trimStart())
                val lastIdx = spans.size - 1
                spans[lastIdx] = spans[lastIdx].copy(text = spans[lastIdx].text.trimEnd())
            }
            // Whitespace on both sides of a style boundary would render as a
            // double space (HTML collapses across elements) — M3 review finding 5.
            for (i in 1 until spans.size) {
                if (spans[i - 1].text.endsWith(" ") && spans[i].text.startsWith(" ")) {
                    spans[i] = spans[i].copy(text = spans[i].text.trimStart())
                }
            }
            return spans.filter { it.text.isNotEmpty() }
        }

        /** Single trimmed, whitespace-collapsed text of [el]'s inline content. */
        private fun inlineText(el: Element): String =
            inlineSpans(el).joinToString("") { it.text }.trim()

        private fun findFirst(root: Element, predicate: (Element) -> Boolean): Element? {
            val stack = ArrayDeque<HtmlNode>()
            for (i in root.children.indices.reversed()) stack.addLast(root.children[i])
            while (stack.isNotEmpty()) {
                val n = stack.removeLast()
                if (n is Element) {
                    if (predicate(n)) return n
                    for (i in n.children.indices.reversed()) stack.addLast(n.children[i])
                }
            }
            return null
        }
    }
}
