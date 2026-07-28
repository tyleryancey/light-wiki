package dev.tyler.wiki.parser

import dev.tyler.wiki.parser.HtmlToken.EndTag
import dev.tyler.wiki.parser.HtmlToken.StartTag
import dev.tyler.wiki.parser.HtmlToken.Text
import kotlin.test.Test
import kotlin.test.assertEquals

class HtmlLexerTest {

    @Test
    fun `plain text produces a single text token`() {
        assertEquals(listOf(Text("hello world")), HtmlLexer.lex("hello world"))
    }

    @Test
    fun `simple element produces start, text, end tokens`() {
        assertEquals(
            listOf(StartTag("b", emptyMap()), Text("bold"), EndTag("b")),
            HtmlLexer.lex("<b>bold</b>"),
        )
    }

    @Test
    fun `tag and attribute names are lowercased`() {
        assertEquals(
            listOf(StartTag("div", mapOf("class" to "Infobox")), EndTag("div")),
            HtmlLexer.lex("""<DIV CLASS="Infobox"></DIV>"""),
        )
    }

    @Test
    fun `named entities decode in text`() {
        assertEquals(
            listOf(Text("Tom & Jerry <3 – “quoted”")),
            HtmlLexer.lex("Tom &amp; Jerry &lt;3&nbsp;&ndash; &ldquo;quoted&rdquo;"),
        )
    }

    @Test
    fun `numeric entities decode in decimal and hex`() {
        assertEquals(
            listOf(Text("A —B")),
            HtmlLexer.lex("&#65;&#160;&#x2014;B"),
        )
    }

    @Test
    fun `unknown or bare ampersands pass through as text`() {
        assertEquals(
            listOf(Text("AT&T &notarealentity; 1 & 2")),
            HtmlLexer.lex("AT&T &notarealentity; 1 & 2"),
        )
    }

    @Test
    fun `double-encoded entity decodes exactly once`() {
        assertEquals(listOf(Text("&amp;")), HtmlLexer.lex("&amp;amp;"))
    }

    @Test
    fun `entities decode inside attribute values`() {
        assertEquals(
            listOf(StartTag("img", mapOf("alt" to "R&D – lab"), selfClosing = false)),
            HtmlLexer.lex("""<img alt="R&amp;D &ndash; lab">"""),
        )
    }

    @Test
    fun `comments and doctype are swallowed`() {
        assertEquals(
            listOf(Text("a"), StartTag("p", emptyMap()), Text("b"), EndTag("p")),
            HtmlLexer.lex("<!DOCTYPE html>a<!-- note <b>not markup</b> --><p>b</p>"),
        )
    }

    @Test
    fun `script and style content is raw-skipped`() {
        assertEquals(
            listOf(Text("a"), Text("b")),
            HtmlLexer.lex("a<script>if (x < y) { alert('<p>'); }</script><style>.c { color: red }</style>b"),
        )
    }

    @Test
    fun `stray angle bracket is text, not markup`() {
        assertEquals(
            listOf(Text("3 < 5 and 7 > 2")),
            HtmlLexer.lex("3 < 5 and 7 > 2"),
        )
    }

    @Test
    fun `truncated tag at end of input becomes text`() {
        assertEquals(
            listOf(Text("x"), Text("<b unfinished")),
            HtmlLexer.lex("x<b unfinished"),
        )
    }

    @Test
    fun `unterminated comment swallows to end of input without throwing`() {
        assertEquals(
            listOf(Text("a")),
            HtmlLexer.lex("a<!-- never closed"),
        )
    }

    @Test
    fun `ampersand flood without semicolons lexes in linear time`() {
        // M2-review finding 1: unbounded indexOf(';') made this O(n²) (~6.5s at 640k).
        val input = "&".repeat(640_000)
        val start = System.nanoTime()
        val tokens = HtmlLexer.lex(input)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertEquals(listOf(Text(input)), tokens)
        kotlin.test.assertTrue(ms < 2_000, "ampersand flood took ${ms}ms — quadratic entity scan regressed")
    }

    @Test
    fun `spaced equals still binds value to attribute`() {
        // M2-review finding 3: `href = "x"` must not produce a junk attribute.
        assertEquals(
            listOf(StartTag("a", mapOf("href" to "x"))),
            HtmlLexer.lex("""<a href = "x">"""),
        )
    }

    @Test
    fun `attributes parse quoted, single-quoted, unquoted, and valueless forms`() {
        assertEquals(
            listOf(
                StartTag(
                    "td",
                    mapOf(
                        "class" to "navbox",
                        "align" to "left",
                        "colspan" to "2",
                        "hidden" to "",
                    ),
                ),
            ),
            HtmlLexer.lex("""<td class="navbox" align='left' colspan=2 hidden>"""),
        )
    }
}
