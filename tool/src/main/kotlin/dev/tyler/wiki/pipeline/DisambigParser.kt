package dev.tyler.wiki.pipeline

import dev.tyler.wiki.parser.HtmlNode
import dev.tyler.wiki.parser.HtmlNode.Element
import dev.tyler.wiki.parser.HtmlNode.TextNode

/**
 * A single linked entry on a disambiguation page (e.g. "Mercury (element)").
 * [title] re-enters the normal article flow; [description] is the plain-text
 * gloss following the link, when present.
 */
data class DisambiguationEntry(
    val title: String,
    val description: String?,
)

/**
 * Entries grouped under section headings ("Companies", "Music"). Leading
 * pre-heading entries form a section with [heading] = null.
 */
data class DisambiguationSection(
    val heading: String?,
    val entries: List<DisambiguationEntry>,
)

/**
 * Extracts linked entries from a disambiguation page. Runs against the RAW
 * parsed tree — disambig pages skip [ArticlePipeline] entirely, because the
 * link targets it needs are exactly what the pipeline unwraps. The chooser
 * this feeds is the only navigation surface in the app.
 * Exclusion rules: `rendering-exclusions.md` §8.
 */
object DisambigParser {

    private val NON_ENTRY_CLASSES = setOf(
        "sistersitebox", "side-box", "navbox", "vertical-navbox",
        "toc", "tocright", "metadata", "noprint", "thumb", "mw-editsection",
    )

    private val NON_ARTICLE_NAMESPACES = setOf(
        "File", "Image", "Media", "Wikipedia", "WP", "Help",
        "Category", "Template", "Special", "Portal", "Talk", "User",
        "User_talk", "Wikipedia_talk", "Template_talk", "Category_talk",
        "MediaWiki", "MediaWiki_talk", "Module",
    )

    fun parse(root: Element): List<DisambiguationSection> {
        val container = findFirst(root) { "mw-parser-output" in it.classes } ?: root

        val sections = ArrayList<DisambiguationSection>()
        var currentHeading: String? = null
        var currentEntries = ArrayList<DisambiguationEntry>()

        fun flush() {
            if (currentEntries.isNotEmpty()) {
                sections.add(DisambiguationSection(currentHeading, currentEntries.toList()))
                currentEntries = ArrayList()
            }
        }

        for (child in container.children) {
            if (child !is Element || isNonEntryChrome(child)) continue
            when {
                isHeading2(child) -> {
                    flush()
                    currentHeading = headingText(child)
                }
                child.name == "ul" || child.name == "ol" -> {
                    for (li in child.children) {
                        if (li is Element && li.name == "li" && !isNonEntryChrome(li)) {
                            extractEntry(li)?.let(currentEntries::add)
                        }
                    }
                }
            }
        }
        flush()
        return sections
    }

    private fun isNonEntryChrome(el: Element): Boolean =
        el.classes.any { it in NON_ENTRY_CLASSES }

    private fun isHeading2(el: Element): Boolean {
        if (el.name == "h2") return true
        return el.name == "div" && "mw-heading2" in el.classes &&
            el.children.any { it is Element && it.name == "h2" }
    }

    private fun headingText(el: Element): String? {
        val h2 = if (el.name == "h2") {
            el
        } else {
            el.children.firstOrNull { it is Element && it.name == "h2" } as Element? ?: return null
        }
        return flatText(h2).trim().takeIf { it.isNotEmpty() }
    }

    /**
     * An `<li>` becomes an entry iff its first usable `<a>` is an article
     * link. "Usable" excludes external interwiki (`extiw`), red links
     * (`new`), and MediaWiki non-article namespaces. Chrome inside the li
     * (e.g. a nested sister-site box) never contributes links.
     */
    private fun extractEntry(li: Element): DisambiguationEntry? {
        val link = findFirst(li, skipChrome = true) { it.name == "a" && isArticleLink(it) } ?: return null

        val linkText = flatText(link).trim()
        val title = (link.attrs["title"]?.takeIf { it.isNotBlank() } ?: linkText).trim()
        if (title.isEmpty()) return null

        val description = flatText(li).trim()
            .removePrefix(linkText)
            .trimStart(',', ' ', ':', '–', '-', ' ')
            .trim()
            .takeIf { it.isNotEmpty() }

        return DisambiguationEntry(title = title, description = description)
    }

    private fun isArticleLink(a: Element): Boolean {
        val classes = a.classes
        if ("extiw" in classes || "new" in classes) return false
        val href = a.attrs["href"] ?: return false
        if (!href.startsWith("/wiki/")) return false
        val path = href.removePrefix("/wiki/").substringBefore('#')
        val firstColon = path.indexOf(':')
        if (firstColon < 0) return true
        return path.substring(0, firstColon) !in NON_ARTICLE_NAMESPACES
    }

    // --- Iterative walkers (sharp edge: no recursion over untrusted trees) ---

    private fun findFirst(
        root: Element,
        skipChrome: Boolean = false,
        predicate: (Element) -> Boolean,
    ): Element? {
        val stack = ArrayDeque<HtmlNode>()
        for (i in root.children.indices.reversed()) stack.addLast(root.children[i])
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            if (n is Element) {
                if (skipChrome && isNonEntryChrome(n)) continue
                if (predicate(n)) return n
                for (i in n.children.indices.reversed()) stack.addLast(n.children[i])
            }
        }
        return null
    }

    /**
     * Text content with chrome elements skipped throughout — the donor
     * removed `NON_ENTRY_SELECTORS` globally before extracting any text, so
     * chrome nested inside headings or entries must not leak (M3 review
     * finding 2).
     */
    private fun flatText(root: Element): String {
        val sb = StringBuilder()
        val stack = ArrayDeque<HtmlNode>()
        for (i in root.children.indices.reversed()) stack.addLast(root.children[i])
        while (stack.isNotEmpty()) {
            when (val n = stack.removeLast()) {
                is TextNode -> sb.append(n.text)
                is Element -> {
                    if (isNonEntryChrome(n)) continue
                    for (i in n.children.indices.reversed()) stack.addLast(n.children[i])
                }
            }
        }
        return sb.toString()
    }
}
