package dev.tyler.wiki.pipeline

import dev.tyler.wiki.parser.HtmlTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Port of the May DisambiguationParserTest (12 tests) onto the native
 * substrate — same semantics, parsing the raw tree (the parser needs `<a>`
 * targets, so disambig pages skip ArticlePipeline entirely).
 */
class DisambigParserTest {

    private fun parse(html: String) = DisambigParser.parse(HtmlTree.parse(html))

    @Test
    fun `extracts leading unheaded entries`() {
        val sections = parse(
            """
            <div class="mw-parser-output">
            <p><b>Mercury</b> most commonly refers to:</p>
            <ul>
              <li><a href="/wiki/Mercury_(planet)" title="Mercury (planet)">Mercury (planet)</a>, the closest planet to the Sun</li>
              <li><a href="/wiki/Mercury_(element)" title="Mercury (element)">Mercury (element)</a>, a chemical element</li>
            </ul>
            </div>
            """.trimIndent(),
        )
        assertEquals(1, sections.size)
        assertNull(sections[0].heading, "leading section has no heading")
        assertEquals(2, sections[0].entries.size)
        assertEquals("Mercury (planet)", sections[0].entries[0].title)
        assertEquals("the closest planet to the Sun", sections[0].entries[0].description)
        assertEquals("Mercury (element)", sections[0].entries[1].title)
        assertEquals("a chemical element", sections[0].entries[1].description)
    }

    @Test
    fun `groups entries under headings`() {
        val sections = parse(
            """
            <div class="mw-parser-output">
            <ul>
              <li><a href="/wiki/Lead_in" title="Lead">Lead</a>, the lead</li>
            </ul>
            <div class="mw-heading mw-heading2"><h2 id="Companies">Companies</h2></div>
            <ul>
              <li><a href="/wiki/Foo_Inc" title="Foo Inc">Foo Inc</a>, a company</li>
              <li><a href="/wiki/Bar_Co" title="Bar Co">Bar Co</a>, another company</li>
            </ul>
            <div class="mw-heading mw-heading2"><h2 id="Music">Music</h2></div>
            <ul>
              <li><a href="/wiki/Foo_(band)" title="Foo (band)">Foo (band)</a>, a band</li>
            </ul>
            </div>
            """.trimIndent(),
        )
        assertEquals(3, sections.size)
        assertNull(sections[0].heading)
        assertEquals(1, sections[0].entries.size)
        assertEquals("Companies", sections[1].heading)
        assertEquals(2, sections[1].entries.size)
        assertEquals("Music", sections[2].heading)
        assertEquals(1, sections[2].entries.size)
        assertEquals("Foo (band)", sections[2].entries[0].title)
    }

    @Test
    fun `skips external interwiki links`() {
        val sections = parse(
            """
            <div class="mw-parser-output">
            <ul>
              <li><a href="https://en.wiktionary.org/wiki/foo" class="extiw" title="wikt:foo">foo</a> on Wiktionary</li>
              <li><a href="/wiki/Foo" title="Foo">Foo</a>, an article</li>
            </ul>
            </div>
            """.trimIndent(),
        )
        assertEquals(1, sections.size)
        assertEquals(1, sections[0].entries.size)
        assertEquals("Foo", sections[0].entries[0].title)
    }

    @Test
    fun `skips red links`() {
        val sections = parse(
            """
            <div class="mw-parser-output">
            <ul>
              <li><a href="/wiki/Nonexistent" class="new" title="Nonexistent (page does not exist)">Nonexistent</a>, a non-page</li>
              <li><a href="/wiki/Real" title="Real">Real</a>, real article</li>
            </ul>
            </div>
            """.trimIndent(),
        )
        assertEquals(1, sections[0].entries.size)
        assertEquals("Real", sections[0].entries[0].title)
    }

    @Test
    fun `skips non-article namespaces`() {
        val sections = parse(
            """
            <div class="mw-parser-output">
            <ul>
              <li><a href="/wiki/File:Example.png" title="File:Example.png">An image</a></li>
              <li><a href="/wiki/Wikipedia:About" title="Wikipedia:About">Meta page</a></li>
              <li><a href="/wiki/Category:Foo" title="Category:Foo">Category</a></li>
              <li><a href="/wiki/Mainspace_Page" title="Mainspace Page">Article</a>, a real entry</li>
            </ul>
            </div>
            """.trimIndent(),
        )
        assertEquals(1, sections[0].entries.size)
        assertEquals("Mainspace Page", sections[0].entries[0].title)
    }

    @Test
    fun `strips sister-site box and navboxes`() {
        val sections = parse(
            """
            <div class="mw-parser-output">
            <div class="sistersitebox"><ul><li><a href="/wiki/Should_Not_Appear" title="Should Not Appear">Should Not Appear</a></li></ul></div>
            <ul>
              <li><a href="/wiki/Real" title="Real">Real</a>, kept</li>
            </ul>
            <div class="navbox"><ul><li><a href="/wiki/Also_Skipped" title="Also Skipped">Also Skipped</a></li></ul></div>
            </div>
            """.trimIndent(),
        )
        val titles = sections.flatMap { it.entries.map { e -> e.title } }
        assertTrue("Real" in titles, "real entry present")
        assertTrue("Should Not Appear" !in titles, "sistersitebox entry stripped")
        assertTrue("Also Skipped" !in titles, "navbox entry stripped")
    }

    @Test
    fun `uses title attribute for display title`() {
        val sections = parse(
            """
            <div class="mw-parser-output">
            <ul>
              <li><a href="/wiki/Mercury_(planet)" title="Mercury (planet)">Mercury</a>, a planet</li>
            </ul>
            </div>
            """.trimIndent(),
        )
        assertEquals("Mercury (planet)", sections[0].entries[0].title)
    }

    @Test
    fun `missing description is null`() {
        val sections = parse(
            """
            <div class="mw-parser-output">
            <ul>
              <li><a href="/wiki/Foo" title="Foo">Foo</a></li>
            </ul>
            </div>
            """.trimIndent(),
        )
        assertNull(sections[0].entries[0].description)
    }

    @Test
    fun `empty input produces empty list`() {
        assertEquals(emptyList(), parse(""))
    }

    @Test
    fun `handles container without parser-output class`() {
        val sections = parse(
            """<ul><li><a href="/wiki/Foo" title="Foo">Foo</a>, a thing</li></ul>""",
        )
        assertEquals(1, sections.size)
        assertEquals("Foo", sections[0].entries[0].title)
    }

    @Test
    fun `sister-site box entry does not leak into output`() {
        val sections = parse(
            """
            <div class="mw-parser-output">
            <div class="side-box side-box-right sistersitebox">
              <div class="side-box-text">
                <ul><li>Look up <a href="https://en.wiktionary.org/wiki/mercury" class="extiw" title="wikt:mercury">mercury</a> in Wiktionary</li></ul>
              </div>
            </div>
            <ul>
              <li><a href="/wiki/Mercury_(planet)" title="Mercury (planet)">Mercury (planet)</a>, a planet</li>
            </ul>
            </div>
            """.trimIndent(),
        )
        val titles = sections.flatMap { it.entries.map { e -> e.title } }
        assertTrue("Mercury (planet)" in titles, "planet entry kept")
        assertTrue("mercury" !in titles, "sistersitebox entry absent")
    }

    @Test
    fun `first usable link wins over inline cross-references`() {
        val sections = parse(
            """
            <div class="mw-parser-output">
            <ul>
              <li><a href="/wiki/Primary" title="Primary">Primary</a>, see also <a href="/wiki/Secondary" title="Secondary">Secondary</a></li>
            </ul>
            </div>
            """.trimIndent(),
        )
        assertEquals(1, sections[0].entries.size)
        assertEquals("Primary", sections[0].entries[0].title)
    }
}
