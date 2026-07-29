package dev.tyler.wiki.model

import dev.tyler.wiki.parser.HtmlTree
import dev.tyler.wiki.pipeline.ArticlePipeline
import kotlin.test.Test
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
        for (dropped in listOf("References", "External links", "See also", "Further reading", "Bibliography")) {
            assertTrue(headingTexts.none { it == dropped }, "appendix '$dropped' dropped")
        }
        val emptyParas = doc.blocks.count { it is Block.Paragraph && it.spans.isEmpty() }
        assertTrue(emptyParas == 0, "no empty paragraphs")
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
}
