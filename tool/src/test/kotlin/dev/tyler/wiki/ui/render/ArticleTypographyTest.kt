package dev.tyler.wiki.ui.render

import dev.tyler.wiki.model.Block
import dev.tyler.wiki.model.InlineSpan
import kotlin.test.Test
import kotlin.test.assertEquals

class ArticleTypographyTest {

    @Test
    fun `heading em ramp follows the May css`() {
        assertEquals(1.35f, ArticleTypography.headingEm(2))
        assertEquals(1.15f, ArticleTypography.headingEm(3))
        assertEquals(1.02f, ArticleTypography.headingEm(4))
    }

    @Test
    fun `lead body and caption ems follow the May css`() {
        assertEquals(1.05f, ArticleTypography.LEAD_EM)
        assertEquals(1.0f, ArticleTypography.BODY_EM)
        assertEquals(0.85f, ArticleTypography.CAPTION_EM)
    }

    @Test
    fun `sp scales from 18 base by em and reader percent`() {
        assertEquals(18f, ArticleTypography.spFor(1.0f, 100))
        assertEquals(19.8f, ArticleTypography.spFor(1.0f, 110), absoluteTolerance = 0.001f)
        assertEquals(26.73f, ArticleTypography.spFor(1.35f, 110), absoluteTolerance = 0.001f)
        assertEquals(14.4f, ArticleTypography.spFor(1.0f, 80), absoluteTolerance = 0.001f)
    }

    @Test
    fun `scale steps by ten and clamps at both bounds`() {
        assertEquals(120, ArticleTypography.stepUp(110))
        assertEquals(180, ArticleTypography.stepUp(180))
        assertEquals(180, ArticleTypography.stepUp(175))
        assertEquals(100, ArticleTypography.stepDown(110))
        assertEquals(80, ArticleTypography.stepDown(80))
        assertEquals(80, ArticleTypography.stepDown(85))
    }

    @Test
    fun `lead index is the first paragraph block`() {
        val blocks = listOf(
            Block.Heading(2, "H"),
            Block.Paragraph(listOf(InlineSpan("lead"))),
            Block.Paragraph(listOf(InlineSpan("second"))),
        )
        assertEquals(1, ArticleTypography.leadIndex(blocks))
        assertEquals(-1, ArticleTypography.leadIndex(listOf(Block.Heading(2, "only"))))
    }
}
