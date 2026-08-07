package dev.tyler.wiki.pipeline

import dev.tyler.wiki.parser.HtmlNode
import dev.tyler.wiki.parser.HtmlNode.Element
import dev.tyler.wiki.parser.HtmlNode.TextNode

/**
 * Tree→tree transforms over parsed `action=parse&prop=text` output, in the
 * May pipeline order: drop appendix → strip links → fix images → strip
 * clutter → reflow infobox. Contract: `rendering-exclusions.md` (v4).
 *
 * All passes are depth-capped (MAX_DEPTH): content nested absurdly deep is
 * dropped rather than recursed into — real article trees are ~30 levels.
 */
object ArticlePipeline {

    private const val MAX_DEPTH = 256

    private val APPENDIX_IDS = setOf(
        "See_also",
        "References",
        "External_links",
        "Further_reading",
        // Measured against this repo's own fixture corpus 2026-07-30, contents
        // read rather than inferred: Citations (great-wave 92 li / 43 cite;
        // fourier short-form refs), General_and_cited_sources (27/27),
        // Works_cited (list-presidents 72/72).
        // NOT here, deliberately: Notes and Explanatory_notes hold
        // author-written prose; Sources is a genuine content heading on
        // river and history articles (Nile#Sources is the headwaters
        // section); and Bibliography, in v1's drop set, is the works-list
        // section on biographies (George Orwell's is the subject's own
        // books) — removed in v1.1. The heading id is the only signal and
        // the text is the only way to tell apparatus from content, so an
        // ambiguous id stays out: rendering apparatus is recoverable noise,
        // deleting content is not.
        "Citations",
        "General_and_cited_sources",
        "Works_cited",
    )

    /** Classes whose elements are dropped wholesale (exclusions §2 + galleries per §1-res.4). */
    private val CLUTTER_CLASSES = setOf(
        "mw-editsection",
        "navbox",
        "vertical-navbox",
        "metadata",
        "mw-empty-elt",
        "noprint",
        "toc",
        "hatnote",
        "dablink",
        "redirectMsg",
        "sidebar",
        "gallery",
    )

    fun process(root: Element): Element =
        reflowInfobox(stripClutter(fixImages(stripLinks(dropAppendixSections(root)))))

    // --- Pass 1: appendix sections -------------------------------------

    private fun dropAppendixSections(root: Element): Element =
        rebuild(root, 0) { el, depth, recurse ->
            val kept = ArrayList<HtmlNode>(el.children.size)
            var dropping = false
            for (child in el.children) {
                if (child is Element && isHeading2Container(child)) {
                    dropping = isAppendixHeading(child)
                    if (dropping) continue
                }
                if (!dropping) kept.add(child)
            }
            el.copy(children = kept.map { recurse(it, depth + 1) })
        }

    /** Bare `<h2>` or `<div class="mw-heading2">` wrapping one — a top-level section boundary. */
    private fun isHeading2Container(el: Element): Boolean {
        if (el.name == "h2") return true
        return el.name == "div" && "mw-heading2" in el.classes &&
            el.children.any { it is Element && it.name == "h2" }
    }

    private fun isAppendixHeading(container: Element): Boolean {
        val h2 = if (container.name == "h2") {
            container
        } else {
            container.children.firstOrNull { it is Element && it.name == "h2" } as Element? ?: return false
        }
        if (canonicalHeadingId(h2.attrs["id"]) in APPENDIX_IDS) return true
        // Legacy shape: id on a nested <span class="mw-headline">.
        return findFirst(h2) {
            it.name == "span" && "mw-headline" in it.classes && canonicalHeadingId(it.attrs["id"]) in APPENDIX_IDS
        } != null
    }

    /**
     * MediaWiki de-duplicates a repeated heading id by appending `_2`, `_3`…
     * Strip that before matching, so a second References section still drops.
     * Safe by construction: a real section like `Season_2` strips to `Season`,
     * which is not an appendix id.
     */
    private fun canonicalHeadingId(id: String?): String? = id?.replace(NUMERIC_ID_SUFFIX, "")

    private val NUMERIC_ID_SUFFIX = Regex("_\\d+$")

    // --- Pass 2: links --------------------------------------------------

    private fun stripLinks(root: Element): Element =
        rebuild(root, 0) { el, depth, recurse ->
            el.copy(
                children = el.children.flatMap { child ->
                    if (child is Element && child.name == "a") {
                        // Unwrap: the anchor's children take its place.
                        (recurse(child, depth + 1) as Element).children
                    } else {
                        listOf(recurse(child, depth + 1))
                    }
                },
            )
        }

    // --- Pass 3: images -------------------------------------------------

    private fun fixImages(root: Element): Element =
        rebuild(root, 0) { el, depth, recurse ->
            el.copy(
                children = el.children.mapNotNull { child ->
                    when {
                        child is Element && child.name == "noscript" ->
                            findFirst(child) { it.name == "img" }?.let { fixImg(it) } // promote or drop
                        child is Element && child.name == "img" -> fixImg(child)
                        else -> recurse(child, depth + 1)
                    }
                },
            )
        }

    private fun fixImg(img: Element): Element {
        val attrs = LinkedHashMap(img.attrs)
        attrs["src"]?.let { if (it.startsWith("//")) attrs["src"] = "https:$it" }
        attrs.remove("srcset")
        attrs.remove("data-srcset")
        attrs.remove("style")
        // width/height deliberately KEPT (03 §1.8): aspect-ratio placeholders.
        return img.copy(attrs = attrs)
    }

    // --- Pass 4: clutter ------------------------------------------------

    private fun stripClutter(root: Element): Element =
        rebuild(root, 0) { el, depth, recurse ->
            el.copy(
                children = el.children.mapNotNull { child ->
                    if (child is Element && isClutter(child)) null else recurse(child, depth + 1)
                },
            )
        }

    private fun isClutter(el: Element): Boolean {
        val classes = el.classes
        if (classes.any { it in CLUTTER_CLASSES || it.startsWith("mw-gallery") }) return true
        return el.name == "sup" && "reference" in classes
    }

    // --- Pass 5: infobox reflow ----------------------------------------

    private fun reflowInfobox(root: Element): Element {
        val infobox = findFirst(root) { it.name == "table" && "infobox" in it.classes } ?: return root
        val container = findFirst(root) { "mw-parser-output" in it.classes } ?: root
        val lead = container.children.firstOrNull {
            it is Element && it.name == "p" && hasNonBlankText(it)
        } as Element? ?: return root

        // Single pass over the ORIGINAL tree: rebuilding invalidates reference
        // identity, so removal and insertion must happen in one traversal.
        return rebuild(root, 0) { el, depth, recurse ->
            val out = ArrayList<HtmlNode>(el.children.size + 1)
            for (child in el.children) {
                if (child === infobox) continue
                out.add(recurse(child, depth + 1))
                if (child === lead) out.add(infobox)
            }
            el.copy(children = out)
        }
    }

    private fun hasNonBlankText(el: Element): Boolean {
        val stack = ArrayDeque<HtmlNode>()
        stack.addLast(el)
        while (stack.isNotEmpty()) {
            when (val n = stack.removeLast()) {
                is TextNode -> if (n.text.isNotBlank()) return true
                is Element -> n.children.forEach(stack::addLast)
            }
        }
        return false
    }

    // --- Shared walkers -------------------------------------------------

    /**
     * Generic depth-capped rebuild. [fn] receives (element, depth, recurse)
     * and returns the rebuilt element; it decides how children are handled
     * using `recurse`, which re-enters [fn] for element children and returns
     * text nodes unchanged. Beyond MAX_DEPTH children are dropped.
     */
    private fun rebuild(
        root: Element,
        depth: Int,
        fn: (Element, Int, (HtmlNode, Int) -> HtmlNode) -> Element,
    ): Element {
        lateinit var recurse: (HtmlNode, Int) -> HtmlNode
        recurse = { node, d ->
            when (node) {
                is TextNode -> node
                is Element -> if (d > MAX_DEPTH) node.copy(children = emptyList()) else fn(node, d, recurse)
            }
        }
        return fn(root, depth, recurse)
    }

    /** First element (document order, iterative) satisfying [predicate], self included. */
    private fun findFirst(root: Element, predicate: (Element) -> Boolean): Element? {
        val stack = ArrayDeque<HtmlNode>()
        stack.addLast(root)
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
