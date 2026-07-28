package dev.tyler.wiki.parser

import dev.tyler.wiki.parser.HtmlNode.Element
import dev.tyler.wiki.parser.HtmlNode.TextNode
import kotlin.test.Test
import kotlin.test.assertEquals

class HtmlTreeTest {

    private fun root(vararg children: HtmlNode) = Element(HtmlTree.ROOT, emptyMap(), children.toList())

    @Test
    fun `simple nesting builds a tree`() {
        assertEquals(
            root(
                Element(
                    "div", mapOf("class" to "x"),
                    listOf(Element("b", emptyMap(), listOf(TextNode("hi")))),
                ),
            ),
            HtmlTree.parse("""<div class="x"><b>hi</b></div>"""),
        )
    }

    @Test
    fun `void elements take no children even without a slash`() {
        assertEquals(
            root(
                Element(
                    "p", emptyMap(),
                    listOf(TextNode("a"), Element("br"), TextNode("b")),
                ),
            ),
            HtmlTree.parse("<p>a<br>b</p>"),
        )
    }

    @Test
    fun `self-closing tag takes no children`() {
        assertEquals(
            root(Element("img", mapOf("src" to "x.png")), TextNode("after")),
            HtmlTree.parse("""<img src="x.png"/>after"""),
        )
    }

    @Test
    fun `new p auto-closes an open p`() {
        assertEquals(
            root(
                Element("p", emptyMap(), listOf(TextNode("one"))),
                Element("p", emptyMap(), listOf(TextNode("two"))),
            ),
            HtmlTree.parse("<p>one<p>two"),
        )
    }

    @Test
    fun `new li auto-closes an open li`() {
        assertEquals(
            root(
                Element(
                    "ul", emptyMap(),
                    listOf(
                        Element("li", emptyMap(), listOf(TextNode("a"))),
                        Element("li", emptyMap(), listOf(TextNode("b"))),
                    ),
                ),
            ),
            HtmlTree.parse("<ul><li>a<li>b</ul>"),
        )
    }

    @Test
    fun `mismatched end tag closes intervening elements`() {
        assertEquals(
            root(
                Element(
                    "div", emptyMap(),
                    listOf(Element("b", emptyMap(), listOf(TextNode("x")))),
                ),
                TextNode("y"),
            ),
            HtmlTree.parse("<div><b>x</div>y"),
        )
    }

    @Test
    fun `stray end tag with nothing open is ignored`() {
        assertEquals(
            root(TextNode("x")),
            HtmlTree.parse("</div>x"),
        )
    }

    @Test
    fun `end tag for a void element is ignored`() {
        assertEquals(
            root(Element("p", emptyMap(), listOf(TextNode("a"), Element("br"), TextNode("b")))),
            HtmlTree.parse("<p>a<br></br>b</p>"),
        )
    }

    @Test
    fun `unclosed elements auto-close at end of input`() {
        assertEquals(
            root(
                Element(
                    "div", emptyMap(),
                    listOf(Element("span", emptyMap(), listOf(TextNode("deep")))),
                ),
            ),
            HtmlTree.parse("<div><span>deep"),
        )
    }

    @Test
    fun `unknown tags become ordinary containers`() {
        assertEquals(
            root(
                Element(
                    "math-nonsense", mapOf("x" to "1"),
                    listOf(TextNode("inside")),
                ),
            ),
            HtmlTree.parse("""<math-nonsense x=1>inside</math-nonsense>"""),
        )
    }

    @Test
    fun `classes helper splits the class attribute`() {
        val el = HtmlTree.parse("""<div class="infobox  biography vcard">x</div>""")
            .children.first() as Element
        assertEquals(setOf("infobox", "biography", "vcard"), el.classes)
    }
}
