package dev.tyler.wiki.pipeline

import kotlin.test.Test
import kotlin.test.assertEquals

class SnippetTextTest {

    // --- Tag / entity behavior (inputs end with sentence punctuation so the
    //     truncation-ellipsis branch stays out of the way) ---

    @Test
    fun stripsSearchMatchSpans() {
        val input = "The <span class=\"searchmatch\">cat</span> sat on the mat."
        assertEquals("The cat sat on the mat.", stripSnippetHtml(input))
    }

    @Test
    fun decodesCommonEntities() {
        val input = "Fish &amp; chips, &quot;hot&quot; &#39;n&#39; salty&hellip;"
        assertEquals("Fish & chips, \"hot\" 'n' salty…", stripSnippetHtml(input))
    }

    @Test
    fun stripsArbitraryTags() {
        val input = "<b>Bold</b> and <i>italic</i> with <a href=\"x\">link</a>."
        assertEquals("Bold and italic with link.", stripSnippetHtml(input))
    }

    @Test
    fun trimsWhitespace() {
        assertEquals("hello.", stripSnippetHtml("   <span>hello.</span>   "))
    }

    @Test
    fun emptyInputReturnsEmpty() {
        assertEquals("", stripSnippetHtml(""))
    }

    @Test
    fun decodesPaddedNumericEntities() {
        // Wikipedia sometimes uses &#039; (with leading zero) for an apostrophe.
        assertEquals("Einstein's home.", stripSnippetHtml("Einstein&#039;s home."))
    }

    @Test
    fun decodesHexEntities() {
        assertEquals("A.", stripSnippetHtml("&#x41;."))
    }

    @Test
    fun leavesInvalidNumericEntityAlone() {
        assertEquals("&#abc;.", stripSnippetHtml("&#abc;."))
    }

    // --- Truncation-ellipsis behavior (Task 9) ---

    @Test
    fun appendsEllipsisWhenSnippetCutsMidSentence() {
        // Wikipedia search returns fixed-length excerpts; the typical tail is
        // mid-sentence with no terminating punctuation.
        assertEquals(
            "Albert Einstein was a German-born theoretical physicist best known for…",
            stripSnippetHtml("Albert Einstein was a German-born theoretical physicist best known for"),
        )
    }

    @Test
    fun keepsExistingPunctuationWithoutDoubleEllipsis() {
        assertEquals("Already done.", stripSnippetHtml("Already done."))
        assertEquals("Already truncated…", stripSnippetHtml("Already truncated…"))
        assertEquals("Question?", stripSnippetHtml("Question?"))
        assertEquals("Exclaim!", stripSnippetHtml("Exclaim!"))
    }

    @Test
    fun balancedClosersDoNotGetEllipsis() {
        // "...this (per X)" is a complete-feeling tail.
        assertEquals("ends with quote\"", stripSnippetHtml("ends with quote\""))
        assertEquals("ends with paren)", stripSnippetHtml("ends with paren)"))
        assertEquals("ends with bracket]", stripSnippetHtml("ends with bracket]"))
    }

    @Test
    fun emptyInputDoesNotGetEllipsis() {
        // Pure-whitespace → empty after trim → no decoration.
        assertEquals("", stripSnippetHtml("   "))
    }
}
