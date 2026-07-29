package dev.tyler.wiki.model

import dev.tyler.wiki.parser.HtmlTree
import dev.tyler.wiki.pipeline.ArticlePipeline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Model-extraction tests per block type (M3). Input is pipeline-cleaned HTML, as in production. */
class ArticleDocumentTest {

    private fun extract(html: String): ArticleDocument =
        ArticleDocument.from(ArticlePipeline.process(HtmlTree.parse(html)))

    // --- Paragraphs & spans ---

    @Test
    fun `paragraph extracts bold and italic spans in order`() {
        val doc = extract("<p>Plain <b>bold</b> and <i>italic</i> and <b><i>both</i></b>.</p>")
        val para = doc.blocks.single() as Block.Paragraph
        assertEquals(
            listOf(
                InlineSpan("Plain "),
                InlineSpan("bold", bold = true),
                InlineSpan(" and "),
                InlineSpan("italic", italic = true),
                InlineSpan(" and "),
                InlineSpan("both", bold = true, italic = true),
                InlineSpan("."),
            ),
            para.spans,
        )
    }

    @Test
    fun `strong and em map to bold and italic`() {
        val doc = extract("<p><strong>S</strong><em>E</em></p>")
        val para = doc.blocks.single() as Block.Paragraph
        assertEquals(listOf(InlineSpan("S", bold = true), InlineSpan("E", italic = true)), para.spans)
    }

    @Test
    fun `blank paragraphs are dropped`() {
        val doc = extract("<p>  </p><p>real</p><p class=\"mw-empty-elt\"></p>")
        assertEquals(1, doc.blocks.size)
        assertEquals(listOf(InlineSpan("real")), (doc.blocks[0] as Block.Paragraph).spans)
    }

    @Test
    fun `whitespace runs collapse within spans`() {
        val doc = extract("<p>a\n  b\t c</p>")
        assertEquals(listOf(InlineSpan("a b c")), (doc.blocks.single() as Block.Paragraph).spans)
    }

    // --- Headings ---

    @Test
    fun `headings h2 to h4 extract with level, h5 drops`() {
        val doc = extract("<h2>Two</h2><h3>Three</h3><h4>Four</h4><h5>Five</h5><p>x</p>")
        val headings = doc.blocks.filterIsInstance<Block.Heading>()
        assertEquals(listOf(2 to "Two", 3 to "Three", 4 to "Four"), headings.map { it.level to it.text })
    }

    @Test
    fun `wrapped mw-heading divs extract their heading`() {
        val doc = extract("""<div class="mw-heading mw-heading2"><h2 id="History">History</h2></div><p>body</p>""")
        assertEquals(Block.Heading(2, "History"), doc.blocks.first())
    }

    // --- Lists ---

    @Test
    fun `unordered and ordered lists extract with flag`() {
        val doc = extract("<ul><li>a</li><li>b</li></ul><ol><li>one</li></ol>")
        val (ul, ol) = doc.blocks.map { it as Block.ListBlock }
        assertEquals(false, ul.ordered)
        assertEquals(listOf("a", "b"), ul.items.map { it.spans.single().text })
        assertEquals(true, ol.ordered)
        assertEquals(listOf("one"), ol.items.map { it.spans.single().text })
    }

    @Test
    fun `list nesting beyond two levels flattens into level two`() {
        val doc = extract(
            """
            <ul>
              <li>top
                <ul>
                  <li>second
                    <ul><li>third-a</li><li>third-b</li></ul>
                  </li>
                </ul>
              </li>
            </ul>
            """.trimIndent(),
        )
        val list = doc.blocks.single() as Block.ListBlock
        val top = list.items.single()
        assertEquals("top", top.spans.single().text)
        // Level 2 holds "second" plus the flattened third-level items.
        assertEquals(listOf("second", "third-a", "third-b"), top.children.map { it.spans.single().text })
        assertTrue(top.children.all { it.children.isEmpty() }, "no third level survives")
    }

    @Test
    fun `flattened nesting preserves document order across siblings`() {
        // M3-review finding 1: sibling sub-runs must not reorder.
        val doc = extract(
            """
            <ul><li>top<ul>
              <li>A<ul><li>A1</li><li>A2</li></ul></li>
              <li>B<ul><li>B1</li></ul></li>
            </ul></li></ul>
            """.trimIndent(),
        )
        val top = (doc.blocks.single() as Block.ListBlock).items.single()
        assertEquals(
            listOf("A", "A1", "A2", "B", "B1"),
            top.children.map { it.spans.single().text },
        )
    }

    @Test
    fun `whitespace at style boundaries collapses to a single space`() {
        // M3-review finding 5: no double space across a style change.
        val doc = extract("<p>foo\n<b>\nbar</b></p>")
        val para = doc.blocks.single() as Block.Paragraph
        assertEquals(listOf(InlineSpan("foo "), InlineSpan("bar", bold = true)), para.spans)
    }

    // --- Blockquote ---

    @Test
    fun `blockquote extracts spans`() {
        val doc = extract("<blockquote><p>Four score and <i>seven</i> years</p></blockquote>")
        val quote = doc.blocks.single() as Block.Blockquote
        assertEquals(listOf(InlineSpan("Four score and "), InlineSpan("seven", italic = true), InlineSpan(" years")), quote.spans)
    }

    // --- Figures ---

    @Test
    fun `figure extracts src, dimensions, caption`() {
        val doc = extract(
            """<figure typeof="mw:File/Thumb"><img src="https://upload.wikimedia.org/x.jpg" width="220" height="140"><figcaption>A caption</figcaption></figure>""",
        )
        assertEquals(Block.Figure("https://upload.wikimedia.org/x.jpg", 220, 140, "A caption"), doc.blocks.single())
    }

    @Test
    fun `figure without caption has null caption and survives`() {
        val doc = extract("""<figure><img src="https://u.org/y.png" width="10" height="20"></figure>""")
        assertEquals(Block.Figure("https://u.org/y.png", 10, 20, null), doc.blocks.single())
    }

    @Test
    fun `figure without img is dropped`() {
        val doc = extract("<figure><figcaption>orphan</figcaption></figure><p>x</p>")
        assertEquals(1, doc.blocks.size)
        assertTrue(doc.blocks.single() is Block.Paragraph)
    }

    // --- Infobox ---

    @Test
    fun `infobox becomes key-value card after lead paragraph`() {
        val doc = extract(
            """
            <div class="mw-parser-output">
            <table class="infobox"><caption>Marie Curie</caption><tbody>
              <tr><th>Born</th><td>7 November 1867</td></tr>
              <tr><th>Fields</th><td>Physics, chemistry</td></tr>
              <tr><td colspan="2">decorative row</td></tr>
            </tbody></table>
            <p>Marie Curie was a physicist.</p>
            </div>
            """.trimIndent(),
        )
        assertEquals(2, doc.blocks.size)
        assertTrue(doc.blocks[0] is Block.Paragraph, "lead paragraph first (reflow)")
        val card = doc.blocks[1] as Block.InfoboxCard
        assertEquals("Marie Curie", card.title)
        assertEquals(
            listOf(InfoboxRow("Born", "7 November 1867"), InfoboxRow("Fields", "Physics, chemistry")),
            card.rows,
        )
    }

    // --- Tables ---

    @Test
    fun `plain table becomes simple table with headers and rows`() {
        val doc = extract(
            """
            <table class="wikitable"><tbody>
              <tr><th>Name</th><th>Value</th></tr>
              <tr><td>a</td><td>1</td></tr>
              <tr><td>b</td><td>2</td></tr>
            </tbody></table>
            """.trimIndent(),
        )
        val table = doc.blocks.single() as Block.SimpleTable
        assertEquals(listOf("Name", "Value"), table.headers)
        assertEquals(listOf(listOf("a", "1"), listOf("b", "2")), table.rows)
    }

    @Test
    fun `table without header row has empty headers`() {
        val doc = extract("<table><tbody><tr><td>x</td><td>y</td></tr></tbody></table>")
        val table = doc.blocks.single() as Block.SimpleTable
        assertEquals(emptyList(), table.headers)
        assertEquals(listOf(listOf("x", "y")), table.rows)
    }

    // --- Math ---

    @Test
    fun `display math becomes a math image block`() {
        val doc = extract(
            """
            <dl><dd><span class="mwe-math-element"><img class="mwe-math-fallback-image-display" src="https://wikimedia.org/api/rest_v1/media/math/render/svg/abc" alt="E=mc^2"></span></dd></dl>
            """.trimIndent(),
        )
        assertEquals(Block.MathImage("https://wikimedia.org/api/rest_v1/media/math/render/svg/abc"), doc.blocks.single())
    }

    @Test
    fun `inline math in a paragraph contributes its alt text`() {
        val doc = extract(
            """<p>Where <span class="mwe-math-element"><img class="mwe-math-fallback-image-inline" src="https://w.org/m.svg" alt="x^2"></span> holds.</p>""",
        )
        val para = doc.blocks.single() as Block.Paragraph
        assertEquals("Where x^2 holds.", para.spans.joinToString("") { it.text })
    }

    // --- Unknowns & containers ---

    @Test
    fun `unknown blocks drop silently but transparent containers recurse`() {
        val doc = extract(
            """
            <div class="mw-parser-output">
            <aside>ASIDE_NOISE</aside>
            <div><p>nested para</p></div>
            <audio src="x.ogg">AUDIO</audio>
            </div>
            """.trimIndent(),
        )
        assertEquals(1, doc.blocks.size)
        assertEquals(listOf(InlineSpan("nested para")), (doc.blocks[0] as Block.Paragraph).spans)
    }

    @Test
    fun `display-none elements contribute no text`() {
        // Wikipedia hides microformat spans (e.g. bday "(1867-11-07)") with
        // inline display:none; the model carries no styles, so they must be
        // skipped at extraction or they leak as duplicate text.
        val doc = extract("""<p>Born <span style="display:none">(1867-11-07)</span>7 November 1867</p>""")
        val para = doc.blocks.single() as Block.Paragraph
        assertEquals("Born 7 November 1867", para.spans.joinToString("") { it.text })
    }

    @Test
    fun `display-none matching is case-insensitive`() {
        val doc = extract("""<p>A<span style="Display : None">HIDDEN</span>B</p>""")
        val para = doc.blocks.single() as Block.Paragraph
        assertEquals("AB", para.spans.joinToString("") { it.text })
    }

    @Test
    fun `sub and sup text is preserved as plain text`() {
        val doc = extract("<p>E = mc<sup>2</sup> and H<sub>2</sub>O</p>")
        val para = doc.blocks.single() as Block.Paragraph
        assertEquals("E = mc2 and H2O", para.spans.joinToString("") { it.text })
    }
}
