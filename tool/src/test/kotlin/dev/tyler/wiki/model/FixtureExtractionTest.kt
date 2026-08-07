package dev.tyler.wiki.model

import dev.tyler.wiki.parser.HtmlTree
import dev.tyler.wiki.pipeline.ArticlePipeline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/** M3 end-to-end gate: real fixtures through pipeline + extraction yield sane documents. */
class FixtureExtractionTest {

    private fun extract(slug: String): ArticleDocument {
        val html = javaClass.getResourceAsStream("/fixtures/articles/$slug.html")
            ?.bufferedReader()?.readText() ?: fail("fixture missing: $slug.html")
        return ArticleDocument.from(ArticlePipeline.process(HtmlTree.parse(html)))
    }

    @Test
    fun `mercury element extracts a substantial document with infobox after lead`() {
        val doc = extract("mercury-element")
        assertTrue(doc.blocks.size > 50, "expected substantial block count, got ${doc.blocks.size}")
        val firstPara = doc.blocks.indexOfFirst { it is Block.Paragraph }
        val firstInfobox = doc.blocks.indexOfFirst { it is Block.InfoboxCard }
        assertTrue(firstPara >= 0, "has paragraphs")
        assertTrue(firstInfobox > firstPara, "infobox card after lead paragraph (reflow)")
        assertTrue(doc.blocks.any { it is Block.Heading && it.level == 2 }, "has h2 headings")
        assertTrue(doc.blocks.any { it is Block.Figure }, "has figures")
        val headingTexts = doc.blocks.filterIsInstance<Block.Heading>().map { it.text }
        for (dropped in listOf("References", "External links", "See also", "Further reading")) {
            assertTrue(headingTexts.none { it == dropped }, "appendix '$dropped' dropped")
        }
        val emptyParas = doc.blocks.count { it is Block.Paragraph && it.spans.isEmpty() }
        assertTrue(emptyParas == 0, "no empty paragraphs")
    }

    @Test
    fun `mercury-element yields no empty infobox card`() {
        val doc = extract("mercury-element")
        val cards = doc.blocks.filterIsInstance<Block.InfoboxCard>()
        assertTrue(cards.isNotEmpty(), "the element infobox must still extract")
        assertTrue(cards.none { it.rows.isEmpty() }, "no card may ship with zero rows")
        // A gate that only checks "nonempty, no zero-row cards" can't tell recovery
        // from disappearance: mercury-element's ib-element infobox already had rows
        // before this fix, so if the chembox card vanished entirely (null) instead
        // of recovering its rows, both assertions above would still pass. Pin the
        // recovery itself: both cards present, and the chembox's two-td rows intact.
        assertTrue(
            cards.size >= 2,
            "expected both the ib-element infobox and the recovered chembox card, got ${cards.size}",
        )
        val chembox = cards.firstOrNull { card -> card.rows.any { it.label == "Signal word" } }
            ?: fail("no card carries the recovered chembox rows (e.g. \"Signal word\")")
        assertTrue(
            chembox.rows.size >= 3,
            "expected the recovered chembox card to carry several two-td rows, got ${chembox.rows.size}",
        )
    }

    @Test
    fun `fourier transform extracts math images`() {
        val doc = extract("fourier-transform")
        assertTrue(doc.blocks.any { it is Block.MathImage }, "math-heavy article yields MathImage blocks")
    }

    @Test
    fun `population list extracts a simple table with many rows`() {
        val doc = extract("list-countries-population-un")
        val table = doc.blocks.filterIsInstance<Block.SimpleTable>().maxByOrNull { it.rows.size }
            ?: fail("no table extracted")
        assertTrue(table.rows.size > 100, "expected many rows, got ${table.rows.size}")
    }

    @Test
    fun `marie curie extracts an infobox card with rows`() {
        val doc = extract("marie-curie")
        val card = doc.blocks.filterIsInstance<Block.InfoboxCard>().firstOrNull()
            ?: fail("no infobox card")
        assertTrue(card.rows.size >= 5, "expected several infobox rows, got ${card.rows.size}")
    }

    @Test
    fun `stub article extracts a non-empty document`() {
        val doc = extract("vestmanna")
        assertTrue(doc.blocks.isNotEmpty())
        assertTrue(doc.blocks.any { it is Block.Paragraph })
    }

    @Test
    fun `gettysburg address extracts blockquotes`() {
        val doc = extract("gettysburg-address")
        assertTrue(doc.blocks.any { it is Block.Blockquote }, "speech article yields blockquotes")
    }

    @Test
    fun `no display equation leaks as raw LaTeX body copy`() {
        // fourier-transform carries 128 display-math images, every one a
        // direct child of a <p>, plus 2 table.numblk equation boxes whose
        // math carries the inline class ("numblk" also appears as CSS
        // selector text inside a <style> block the lexer raw-skips — those
        // aren't real tables). v1 rendered every display equation as
        // literal TeX in the prose.
        val doc = extract("fourier-transform")
        // Scoped to paragraphs that are ONLY raw alt text — that shape means
        // a block-level math wrapper fell through extraction. A bare
        // `"{\\displaystyle" in body` check cannot work here: inline-class
        // math legitimately contributes alt text whose TeX source begins
        // with \displaystyle (hundreds of instances in this fixture, e.g.
        // alt="{\displaystyle f(x)}" on an img classed
        // mwe-math-fallback-image-inline) — that's required behavior (see
        // "inline math still contributes its alt text" above), not a leak.
        val soloAltParagraphs = doc.blocks.filterIsInstance<Block.Paragraph>().filter { p ->
            p.spans.joinToString("") { it.text }.trim()
                .let { it.startsWith("{\\displaystyle") && it.endsWith("}") }
        }
        assertEquals(emptyList(), soloAltParagraphs, "display math leaked as paragraph text")
        assertTrue(
            doc.blocks.count { it is Block.MathImage } > 50,
            "the display equations must be extracted as MathImage blocks, not dropped",
        )
    }

    @Test
    fun `numblk equation boxes yield math and never become a table`() {
        // fourier-transform's 2 real table.numblk elements: under v1 each
        // produced a SimpleTable row [raw TeX alt, "", "Eq.1"] — the
        // extractMathWithin branch must take them before extractSimpleTable
        // ever sees them. Assert on the "Eq.N" chrome cell, not on
        // "{\displaystyle" — 246 inline-class math spans legitimately live
        // in <td>s elsewhere in this article and would false-positive.
        val doc = extract("fourier-transform")
        val eqChromeCell = Regex("""Eq\.\d+""")
        val leaked = doc.blocks.filterIsInstance<Block.SimpleTable>()
            .flatMap { it.rows }.flatMap { it }
            .filter { eqChromeCell.matches(it.trim()) }
        assertEquals(emptyList(), leaked, "equation-box chrome must not survive as a table cell")
    }

    @Test
    fun `great-wave drops its citation apparatus`() {
        val doc = extract("great-wave-kanagawa")
        val headings = doc.blocks.filterIsInstance<Block.Heading>().map { it.text }
        assertFalse(headings.any { it.startsWith("Citations") }, "Citations must drop")
        assertFalse(headings.any { it.startsWith("General and cited sources") }, "sources must drop")
        assertTrue(headings.any { it.startsWith("Explanatory notes") }, "explanatory notes are content")

        // The heading surviving is not enough on its own — pin the body under
        // it too, so a change that keeps the heading but drops the list (e.g.
        // an over-eager clutter class) still fails. The notes render as
        // ol.references; the ^ backlink survives stripLinks as plain text.
        val notesIndex = doc.blocks.indexOfFirst { it is Block.Heading && it.text.startsWith("Explanatory notes") }
        val notesList = doc.blocks.drop(notesIndex + 1)
            .takeWhile { it !is Block.Heading }
            .filterIsInstance<Block.ListBlock>()
            .firstOrNull()
        assertTrue(notesList != null, "Explanatory notes heading must be followed by its list body")
        val notesText = notesList.items.joinToString(" ") { item -> item.spans.joinToString("") { it.text } }
        assertTrue("Also known as" in notesText, "the first explanatory note's body must survive extraction")
    }
}
