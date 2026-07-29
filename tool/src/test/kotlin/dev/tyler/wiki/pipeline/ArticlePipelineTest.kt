package dev.tyler.wiki.pipeline

import dev.tyler.wiki.pipeline.TreeTestSupport.elementsNamed
import dev.tyler.wiki.pipeline.TreeTestSupport.flatText
import dev.tyler.wiki.pipeline.TreeTestSupport.process
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Port of the May HtmlProcessorTest (28 tests) onto the native substrate —
 * semantics preserved, assertions re-expressed against the tree instead of
 * HTML strings. Two WebView-era tests (stylesheet injection, viewport meta)
 * have no counterpart and are retired. The dimension-strip test is REVERSED
 * per 03 §1.8: the model keeps width/height for aspect-ratio placeholders.
 */
class ArticlePipelineTest {

    // --- Appendix dropping ---

    @Test
    fun `drops see-also section until next h2`() {
        val out = process(
            """
            <p>Body para.</p>
            <h2 id="See_also">See also</h2>
            <ul><li>foo</li><li>bar</li></ul>
            <p>Drop me too.</p>
            <h2 id="History">History</h2>
            <p>Keep history.</p>
            """.trimIndent(),
        )
        val text = flatText(out)
        assertFalse("See also" in text, "see-also heading should be gone")
        assertFalse("foo" in text, "see-also list should be gone")
        assertFalse("Drop me too." in text, "paragraph between sections dropped")
        assertTrue("History" in text, "history h2 kept")
        assertTrue("Keep history." in text, "history body kept")
    }

    @Test
    fun `drops appendix when id is on headline span`() {
        val out = process(
            """
            <p>Intro.</p>
            <h2><span class="mw-headline" id="References">References</span></h2>
            <ol class="references"><li>cite 1</li></ol>
            """.trimIndent(),
        )
        val text = flatText(out)
        assertFalse("References" in text)
        assertFalse("cite 1" in text)
        assertTrue("Intro." in text)
    }

    @Test
    fun `keeps notes section`() {
        val out = process(
            """
            <h2 id="Notes">Notes</h2>
            <p>important notes</p>
            <h2 id="See_also">See also</h2>
            <ul><li>x</li></ul>
            """.trimIndent(),
        )
        val text = flatText(out)
        assertTrue("Notes" in text, "notes heading kept")
        assertTrue("important notes" in text, "notes body kept")
        assertFalse("See also" in text, "see-also dropped")
    }

    @Test
    fun `drops all listed appendix ids`() {
        for (id in listOf("See_also", "References", "External_links", "Further_reading", "Bibliography")) {
            val out = process(
                """
                <p>before</p>
                <h2 id="$id">$id label</h2>
                <p>section content for $id</p>
                """.trimIndent(),
            )
            val text = flatText(out)
            assertFalse("section content for $id" in text, "section $id should be dropped")
            assertTrue("before" in text, "before kept for $id")
        }
    }

    // --- Link unwrapping ---

    @Test
    fun `unwraps all anchors to plain text`() {
        val out = process("""<p>Hello <a href="/wiki/World">world</a> and <a href="#x">friends</a>.</p>""")
        assertEquals(emptyList(), elementsNamed(out, "a"), "no anchors survive")
        assertTrue("Hello world and friends." in flatText(out), "text preserved in flow")
    }

    @Test
    fun `anchor wrapping an image becomes bare image`() {
        val out = process("""<p><a href="/wiki/File:Foo.jpg"><img src="//upload.wikimedia.org/x.jpg"></a></p>""")
        assertEquals(emptyList(), elementsNamed(out, "a"))
        assertEquals(1, elementsNamed(out, "img").size)
    }

    // --- Image fixups ---

    @Test
    fun `protocol-relative images get https prefix`() {
        val out = process("""<img src="//upload.wikimedia.org/wikipedia/commons/x.png">""")
        assertEquals(
            "https://upload.wikimedia.org/wikipedia/commons/x.png",
            elementsNamed(out, "img").single().attrs["src"],
        )
    }

    @Test
    fun `absolute image srcs unchanged`() {
        val out = process("""<img src="https://example.com/x.png">""")
        assertEquals("https://example.com/x.png", elementsNamed(out, "img").single().attrs["src"])
    }

    @Test
    fun `srcset attributes removed`() {
        val out = process("""<img src="//x/a.png" srcset="//x/a@2x.png 2x" data-srcset="//x/a@3x.png 3x">""")
        val img = elementsNamed(out, "img").single()
        assertNull(img.attrs["srcset"])
        assertNull(img.attrs["data-srcset"])
        assertEquals("https://x/a.png", img.attrs["src"])
    }

    @Test
    fun `keeps explicit dimensions for aspect-ratio placeholders`() {
        // REVERSED vs May (03 §1.8): the native model needs width/height so a
        // pending image reserves correct space and a failed one drops whole.
        val out = process("""<img src="//x/a.png" width="250" height="271" style="--mw-file-upright: 1">""")
        val img = elementsNamed(out, "img").single()
        assertEquals("250", img.attrs["width"])
        assertEquals("271", img.attrs["height"])
        assertEquals("https://x/a.png", img.attrs["src"])
    }

    @Test
    fun `noscript fallback promotes its img`() {
        val out = process("""<noscript><img src="//x/fallback.png"></noscript>""")
        assertEquals(emptyList(), elementsNamed(out, "noscript"), "no noscript survives")
        assertEquals("https://x/fallback.png", elementsNamed(out, "img").single().attrs["src"])
    }

    @Test
    fun `empty noscript is dropped not promoted`() {
        val out = process("""<p>before</p><noscript></noscript><p>after</p>""")
        assertEquals(emptyList(), elementsNamed(out, "noscript"))
        val text = flatText(out)
        assertTrue("before" in text)
        assertTrue("after" in text)
    }

    // --- Clutter stripping ---

    @Test
    fun `removes editsection spans`() {
        val out = process("""<h2 id="History">History<span class="mw-editsection">[edit]</span></h2><p>x</p>""")
        val text = flatText(out)
        assertFalse("[edit]" in text)
        assertTrue("History" in text)
    }

    @Test
    fun `removes all clutter selectors`() {
        val out = process(
            """
            <div class="navbox">NAVBOX_CONTENT</div>
            <div class="vertical-navbox">VNAVBOX_CONTENT</div>
            <div class="metadata">METADATA_CONTENT</div>
            <div class="mw-empty-elt">EMPTY_CONTENT</div>
            <div class="noprint">NOPRINT_CONTENT</div>
            <div class="toc">TOC_CONTENT</div>
            <p>KEEP_CONTENT</p>
            """.trimIndent(),
        )
        val text = flatText(out)
        for (sentinel in listOf(
            "NAVBOX_CONTENT", "VNAVBOX_CONTENT", "METADATA_CONTENT",
            "EMPTY_CONTENT", "NOPRINT_CONTENT", "TOC_CONTENT",
        )) {
            assertFalse(sentinel in text, "clutter '$sentinel' stripped")
        }
        assertTrue("KEEP_CONTENT" in text)
    }

    @Test
    fun `drops galleries per resolution 4`() {
        // New in v1 (03 §1 resolution 4): galleries are explicitly dropped.
        val out = process(
            """
            <ul class="gallery mw-gallery-traditional"><li>GALLERY_ITEM</li></ul>
            <div class="mw-gallery-packed">PACKED_GALLERY</div>
            <p>BODY_TEXT</p>
            """.trimIndent(),
        )
        val text = flatText(out)
        assertFalse("GALLERY_ITEM" in text)
        assertFalse("PACKED_GALLERY" in text)
        assertTrue("BODY_TEXT" in text)
    }

    // --- Pipeline composition ---

    @Test
    fun `pipeline leaves no anchors and drops appendix`() {
        val out = process(
            """
            <p><a href="/wiki/Cat">Cats</a> are mammals.</p>
            <h2 id="See_also">See also</h2>
            <ul><li><a href="/wiki/Dog">Dog</a></li></ul>
            """.trimIndent(),
        )
        assertEquals(emptyList(), elementsNamed(out, "a"))
        val text = flatText(out)
        assertFalse("See also" in text)
        assertFalse("Dog" in text)
        assertTrue("Cats are mammals." in text)
    }

    @Test
    fun `empty input produces empty root`() {
        val out = process("")
        assertEquals(emptyList(), out.children)
    }

    // --- Modern heading-wrapper structure ---

    @Test
    fun `drops wrapped appendix until next wrapped heading`() {
        val out = process(
            """
            <p>Body para.</p>
            <div class="mw-heading mw-heading2"><h2 id="See_also">See also</h2></div>
            <ul><li>SEE_ALSO_ITEM</li></ul>
            <p>SEE_ALSO_PARA</p>
            <div class="mw-heading mw-heading2"><h2 id="Notes">Notes</h2></div>
            <p>NOTES_PARA</p>
            <div class="mw-heading mw-heading2"><h2 id="External_links">External links</h2></div>
            <ul><li>EXT_LINK_ITEM</li></ul>
            """.trimIndent(),
        )
        val text = flatText(out)
        assertFalse("See also" in text, "see-also wrapper dropped")
        assertFalse("SEE_ALSO_ITEM" in text)
        assertFalse("SEE_ALSO_PARA" in text)
        assertTrue("Notes" in text, "notes kept")
        assertTrue("NOTES_PARA" in text)
        assertFalse("External links" in text, "external-links wrapper dropped")
        assertFalse("EXT_LINK_ITEM" in text)
        assertTrue("Body para." in text)
    }

    @Test
    fun `drops series sidebar chrome`() {
        val out = process(
            """
            <table class="sidebar sidebar-collapse"><tbody>
              <tr><td>SIDEBAR_NAV_CONTENT</td></tr>
            </tbody></table>
            <p>BODY_TEXT</p>
            """.trimIndent(),
        )
        val text = flatText(out)
        assertFalse("SIDEBAR_NAV_CONTENT" in text, "sidebar dropped")
        assertTrue("BODY_TEXT" in text)
    }

    @Test
    fun `drops hatnote and dablink and redirect message`() {
        val out = process(
            """
            <div class="hatnote">For other uses, see X.</div>
            <div class="dablink">DABLINK_TEXT</div>
            <div class="redirectMsg">REDIRECT_TEXT</div>
            <p>BODY_TEXT</p>
            """.trimIndent(),
        )
        val text = flatText(out)
        assertFalse("For other uses" in text, "hatnote dropped")
        assertFalse("DABLINK_TEXT" in text)
        assertFalse("REDIRECT_TEXT" in text)
        assertTrue("BODY_TEXT" in text)
    }

    @Test
    fun `strips inline reference markers`() {
        val out = process(
            """
            <p>Einstein won the Nobel Prize<sup class="reference">[7]</sup> in 1921.</p>
            <p>He also did<sup id="ref-2" class="reference">[ref]</sup> other things.</p>
            """.trimIndent(),
        )
        val text = flatText(out)
        assertFalse("[7]" in text, "inline [7] dropped")
        assertFalse("[ref]" in text, "inline [ref] dropped")
        assertTrue("Einstein won the Nobel Prize" in text)
        assertTrue("in 1921." in text)
    }

    @Test
    fun `non-reference sup tags are preserved`() {
        val out = process("""<p>E = mc<sup>2</sup></p>""")
        val sup = elementsNamed(out, "sup").single()
        assertEquals("2", flatText(sup))
    }

    // --- Infobox reflow ---

    private fun order(root: dev.tyler.wiki.parser.HtmlNode, vararg needles: String): List<Int> {
        val text = flatText(root)
        return needles.map { text.indexOf(it) }
    }

    @Test
    fun `infobox moves below first paragraph`() {
        val out = process(
            """
            <div class="mw-parser-output">
            <table class="infobox"><caption>INFOBOX_CAPTION</caption></table>
            <p>LEAD_PARAGRAPH text.</p>
            <p>Second paragraph.</p>
            </div>
            """.trimIndent(),
        )
        val (lead, infobox, second) = order(out, "LEAD_PARAGRAPH", "INFOBOX_CAPTION", "Second paragraph.")
        assertTrue(lead >= 0, "lead exists")
        assertTrue(infobox >= 0, "infobox exists")
        assertTrue(lead < infobox, "lead before infobox")
        assertTrue(infobox < second, "infobox before second para")
    }

    @Test
    fun `reflow skips blank paragraphs to find real lead`() {
        val out = process(
            """
            <div class="mw-parser-output">
            <table class="infobox"><caption>INFOBOX_CAPTION</caption></table>
            <p>   </p>
            <p>REAL_LEAD content.</p>
            </div>
            """.trimIndent(),
        )
        val (lead, infobox) = order(out, "REAL_LEAD", "INFOBOX_CAPTION")
        assertTrue(lead in 0 until infobox)
    }

    @Test
    fun `reflow is no-op when there is no infobox`() {
        val out = process(
            """
            <div class="mw-parser-output">
            <p>LEAD only.</p>
            </div>
            """.trimIndent(),
        )
        assertTrue("LEAD only." in flatText(out))
        assertEquals(emptyList(), elementsNamed(out, "table"))
    }

    @Test
    fun `reflow is no-op when there is no lead paragraph`() {
        val out = process(
            """
            <div class="mw-parser-output">
            <table class="infobox"><caption>INFOBOX_CAPTION</caption></table>
            </div>
            """.trimIndent(),
        )
        assertTrue("INFOBOX_CAPTION" in flatText(out))
    }

    @Test
    fun `heading3 subsection in appendix is also dropped`() {
        val out = process(
            """
            <div class="mw-heading mw-heading2"><h2 id="References">References</h2></div>
            <div class="mw-heading mw-heading3"><h3 id="Notes_subsec">Notes</h3></div>
            <p>SUBSECTION_BODY</p>
            <div class="mw-heading mw-heading2"><h2 id="Next">Next</h2></div>
            <p>NEXT_BODY</p>
            """.trimIndent(),
        )
        val text = flatText(out)
        assertFalse("References" in text)
        assertFalse("SUBSECTION_BODY" in text)
        assertTrue("Next" in text)
        assertTrue("NEXT_BODY" in text)
    }
}
