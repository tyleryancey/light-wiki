package dev.tyler.wiki.ui.render

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import dev.tyler.wiki.model.Block
import dev.tyler.wiki.model.InlineSpan
import dev.tyler.wiki.model.ListItem

/**
 * ArticleDocument → Compose. The only place block rendering happens; nothing
 * above this file knows about Compose (the renderer seam).
 *
 * M5 renders the text blocks (Heading, Paragraph, ListBlock, Blockquote).
 * Figure / InfoboxCard / SimpleTable / MathImage land in M6 and are skipped
 * silently here. Every color comes from [LightThemeTokens]; sizes AND block
 * spacing follow [ArticleTypography] (the May css em hierarchy) scaled by
 * the A/A percent — spacing is em-derived so the whole page scales together.
 */
@Composable
fun BlockRenderer(
    blocks: List<Block>,
    scalePercent: Int,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val leadIndex = remember(blocks) { ArticleTypography.leadIndex(blocks) }
    LazyColumn(
        state = listState,
        modifier = modifier,
    ) {
        itemsIndexed(blocks, key = { index, _ -> index }) { index, block ->
            when (block) {
                is Block.Heading -> HeadingBlock(block, scalePercent)
                is Block.Paragraph -> ParagraphBlock(block, index == leadIndex, scalePercent)
                is Block.ListBlock -> ListBlockView(block, scalePercent)
                is Block.Blockquote -> BlockquoteView(block, scalePercent)
                else -> Unit // M6: Figure, InfoboxCard, SimpleTable, MathImage
            }
        }
    }
}

// --- Styles -----------------------------------------------------------------

/** An sp quantity as Dp, so em-derived spacing scales exactly with the text. */
@Composable
private fun Float.textDp(): Dp = with(LocalDensity.current) { this@textDp.sp.toDp() }

@Composable
private fun bodySize(scalePercent: Int): Float =
    ArticleTypography.spFor(ArticleTypography.BODY_EM, scalePercent)

@Composable
private fun bodyStyle(em: Float, scalePercent: Int, lineHeight: Float = ArticleTypography.BODY_LINE_HEIGHT): TextStyle {
    val size = ArticleTypography.spFor(em, scalePercent)
    return LightThemeTokens.typography.paragraph.copy(
        fontSize = size.sp,
        lineHeight = (size * lineHeight).sp,
        color = LightThemeTokens.colors.content,
    )
}

@Composable
private fun annotated(spans: List<InlineSpan>): AnnotatedString = buildAnnotatedString {
    for (span in spans) {
        val style = SpanStyle(
            fontWeight = if (span.bold) FontWeight.Bold else null,
            fontStyle = if (span.italic) FontStyle.Italic else null,
        )
        pushStyle(style)
        append(span.text)
        pop()
    }
}

// --- Blocks -----------------------------------------------------------------

@Composable
private fun HeadingBlock(block: Block.Heading, scalePercent: Int) {
    val size = ArticleTypography.spFor(ArticleTypography.headingEm(block.level), scalePercent)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 1f.gridUnitsAsDp())
            .padding(
                top = (size * ArticleTypography.HEADING_TOP_EM).textDp(),
                bottom = (size * ArticleTypography.HEADING_BOTTOM_EM).textDp(),
            ),
    ) {
        Text(
            text = block.text,
            style = LightThemeTokens.typography.paragraph.copy(
                fontSize = size.sp,
                lineHeight = (size * ArticleTypography.HEADING_LINE_HEIGHT).sp,
                fontWeight = FontWeight.Bold,
                color = LightThemeTokens.colors.content,
            ),
        )
        if (block.level == 2) {
            // The May css h2 hairline, from the content token (no color literals).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 0.3f.gridUnitsAsDp())
                    .height(0.05f.gridUnitsAsDp())
                    .alpha(0.35f)
                    .background(LightThemeTokens.colors.contentSecondary),
            )
        }
    }
}

@Composable
private fun ParagraphBlock(block: Block.Paragraph, isLead: Boolean, scalePercent: Int) {
    val em = if (isLead) ArticleTypography.LEAD_EM else ArticleTypography.BODY_EM
    Text(
        text = annotated(block.spans),
        style = bodyStyle(em, scalePercent),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 1f.gridUnitsAsDp())
            .padding(bottom = (bodySize(scalePercent) * ArticleTypography.PARAGRAPH_SPACING_EM).textDp()),
    )
}

@Composable
private fun ListBlockView(block: Block.ListBlock, scalePercent: Int) {
    val body = bodySize(scalePercent)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 1f.gridUnitsAsDp())
            .padding(bottom = (body * ArticleTypography.PARAGRAPH_SPACING_EM).textDp()),
    ) {
        block.items.forEachIndexed { index, item ->
            ListItemRow(item, marker = marker(block.ordered, index), indentEm = 0f, scalePercent)
            item.children.forEachIndexed { childIndex, child ->
                ListItemRow(
                    child,
                    marker = marker(block.ordered, childIndex),
                    indentEm = ArticleTypography.LIST_INDENT_EM,
                    scalePercent,
                )
            }
        }
    }
}

private fun marker(ordered: Boolean, index: Int): String =
    if (ordered) "${index + 1}." else "–"

@Composable
private fun ListItemRow(item: ListItem, marker: String, indentEm: Float, scalePercent: Int) {
    val body = bodySize(scalePercent)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = (body * indentEm).textDp(),
                bottom = (body * ArticleTypography.LIST_ITEM_SPACING_EM).textDp(),
            ),
    ) {
        // Min-width gutter in ems: wide markers ("10.") push text instead of wrapping.
        Text(
            text = marker,
            style = bodyStyle(ArticleTypography.BODY_EM, scalePercent),
            modifier = Modifier.widthIn(min = (body * ArticleTypography.MARKER_GUTTER_EM).textDp()),
        )
        Text(
            text = annotated(item.spans),
            style = bodyStyle(ArticleTypography.BODY_EM, scalePercent),
        )
    }
}

@Composable
private fun BlockquoteView(block: Block.Blockquote, scalePercent: Int) {
    val body = bodySize(scalePercent)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = 1f.gridUnitsAsDp())
            .padding(bottom = (body * ArticleTypography.PARAGRAPH_SPACING_EM).textDp()),
    ) {
        // The May css bar is full-strength ink (`--ink`), not the muted rule color.
        Box(
            modifier = Modifier
                .width(0.15f.gridUnitsAsDp())
                .fillMaxHeight()
                .background(LightThemeTokens.colors.content),
        )
        Text(
            text = annotated(block.spans),
            style = bodyStyle(ArticleTypography.BODY_EM, scalePercent).copy(fontStyle = FontStyle.Italic),
            modifier = Modifier.padding(start = (body * ArticleTypography.BLOCKQUOTE_INSET_EM).textDp()),
        )
    }
}
