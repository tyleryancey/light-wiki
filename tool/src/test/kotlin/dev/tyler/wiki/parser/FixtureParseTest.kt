package dev.tyler.wiki.parser

import dev.tyler.wiki.pipeline.TreeTestSupport.allElements
import dev.tyler.wiki.pipeline.TreeTestSupport.flatText
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * M2 fixture gate: every harvested article body must lex + build without
 * error and yield a substantial tree. Fixtures are verbatim
 * `action=parse&prop=text` output — see fixtures/README.md for attribution.
 */
class FixtureParseTest {

    private val articles = listOf(
        "caffeine",
        "fourier-transform",
        "gettysburg-address",
        "great-wave-kanagawa",
        "list-countries-population-un",
        "list-presidents-us",
        "marie-curie",
        "mary-anning",
        "mercury-disambiguation",
        "mercury-element",
        "outline-of-chemistry",
        "vestmanna",
    )

    private fun load(slug: String): String =
        javaClass.getResourceAsStream("/fixtures/articles/$slug.html")
            ?.bufferedReader()?.readText()
            ?: fail("fixture missing: $slug.html")

    @Test
    fun `every fixture article lexes and builds a substantial tree`() {
        for (slug in articles) {
            val html = load(slug)
            val root = try {
                HtmlTree.parse(html)
            } catch (e: Exception) {
                fail("$slug threw ${e::class.simpleName}: ${e.message}")
            }
            val elements = allElements(root).size
            val textLen = flatText(root).length
            assertTrue(elements > 50, "$slug: expected a substantial tree, got $elements elements")
            assertTrue(textLen > 500, "$slug: expected substantial text, got $textLen chars")
        }
    }

    @Test
    fun `largest fixture parses in reasonable time and text survives entity decoding`() {
        val html = load("fourier-transform")
        val start = System.nanoTime()
        val root = HtmlTree.parse(html)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue(flatText(root).length > 10_000, "fourier-transform text unexpectedly small")
        // Generous bound: catches accidental quadratic behavior, not JIT noise.
        assertTrue(ms < 5_000, "fourier-transform took ${ms}ms to parse")
    }

    @Test
    fun `no raw entity references survive in decoded text`() {
        val text = flatText(HtmlTree.parse(load("mercury-element")))
        for (raw in listOf("&amp;", "&lt;", "&gt;", "&nbsp;", "&#160;", "&#91;")) {
            assertTrue(raw !in text, "raw entity $raw survived decoding")
        }
    }
}
