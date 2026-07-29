package dev.tyler.wiki.ui.render

import dev.tyler.wiki.model.Block

/**
 * Pure-JVM typography decisions for the article renderer, carrying the May
 * `article.css` hierarchy onto native text (base 18 / 1.6, headings 1.25,
 * h2 1.35em · h3 1.15em · h4 1.02em, lead 1.05em, captions 0.85em) and the
 * A/A reader scale (80–180 step 10, default 110).
 */
object ArticleTypography {

    const val BASE_SP = 18f
    const val BODY_LINE_HEIGHT = 1.6f
    const val HEADING_LINE_HEIGHT = 1.25f

    const val SCALE_MIN = 80
    const val SCALE_MAX = 180
    const val SCALE_STEP = 10
    const val SCALE_DEFAULT = 110

    fun headingEm(level: Int): Float = when (level) {
        2 -> 1.35f
        3 -> 1.15f
        else -> 1.02f
    }

    const val LEAD_EM = 1.05f
    const val BODY_EM = 1.0f
    const val CAPTION_EM = 0.85f

    fun spFor(em: Float, scalePercent: Int): Float = BASE_SP * em * scalePercent / 100f

    fun stepUp(scalePercent: Int): Int =
        ((scalePercent / SCALE_STEP) * SCALE_STEP + SCALE_STEP).coerceAtMost(SCALE_MAX)

    fun stepDown(scalePercent: Int): Int =
        (((scalePercent + SCALE_STEP - 1) / SCALE_STEP) * SCALE_STEP - SCALE_STEP).coerceAtLeast(SCALE_MIN)

    /** Index of the lead paragraph (the first Paragraph block), or -1. */
    fun leadIndex(blocks: List<Block>): Int = blocks.indexOfFirst { it is Block.Paragraph }

    // Spacing, in ems of the relevant text size — the May css is em-based
    // throughout ("sizes stay relative" under the reader control), so block
    // spacing must scale with A/A too (M5 review findings 1 & 3).
    const val PARAGRAPH_SPACING_EM = 0.9f
    const val HEADING_TOP_EM = 1.5f
    const val HEADING_BOTTOM_EM = 0.45f
    const val LIST_INDENT_EM = 1.35f
    const val LIST_ITEM_SPACING_EM = 0.22f
    const val MARKER_GUTTER_EM = 1.5f
    const val BLOCKQUOTE_INSET_EM = 0.78f
}
