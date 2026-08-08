package dev.tyler.wiki.ui.render

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
 * Renders every v1 block type except MathImage, which is deliberately
 * skipped (exclusions §10.5: math fallback images are SVG on a third host).
 * Every color comes from [LightThemeTokens]; sizes AND block
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
    // Big tables and lists expand to one lazy item per row, so a 100+-row
    // population table composes as it scrolls into view instead of building
    // hundreds of Texts in a single frame. Rows of one table share one
    // ScrollState, so they scroll horizontally together.
    val items = remember(blocks) { flatten(blocks) }
    val tableScrolls = remember(blocks) { mutableMapOf<Int, ScrollState>() }
    fun tableScroll(blockIndex: Int) = tableScrolls.getOrPut(blockIndex) { ScrollState(0) }
    LazyColumn(
        state = listState,
        modifier = modifier,
    ) {
        items(items, key = { it.key }) { item ->
            when (item) {
                is RenderItem.Whole -> when (val block = item.block) {
                    is Block.Heading -> HeadingBlock(block, scalePercent)
                    is Block.Paragraph -> ParagraphBlock(block, item.blockIndex == leadIndex, scalePercent)
                    is Block.Blockquote -> BlockquoteView(block, scalePercent)
                    is Block.Figure -> FigureView(block, scalePercent)
                    is Block.InfoboxCard -> InfoboxCardView(block, scalePercent)
                    // MathImage: unrenderable in v1 — the fallback images are SVG
                    // (BitmapFactory cannot decode) on a third host (wikimedia.org,
                    // not on the two-host allowlist). Display math drops; inline
                    // math already survives as alt text. See exclusions §10.5.
                    else -> Unit
                }
                is RenderItem.TableRow -> TableRowLine(
                    cells = if (item.row == HEADER_ROW) item.table.headers else item.table.rows[item.row],
                    header = item.row == HEADER_ROW,
                    scalePercent = scalePercent,
                    scroll = tableScroll(item.blockIndex),
                    lastInTable = item.row == item.table.rows.lastIndex,
                )
                is RenderItem.ListRow -> ListRowView(
                    list = item.list,
                    index = item.item,
                    scalePercent = scalePercent,
                    lastInList = item.item == item.list.items.lastIndex,
                )
            }
        }
    }
}

/** Header pseudo-row index for [RenderItem.TableRow]. */
private const val HEADER_ROW = -1

/** One LazyColumn item: a whole block, or one row of a table or list. */
private sealed interface RenderItem {
    val key: String

    data class Whole(val blockIndex: Int, val block: Block) : RenderItem {
        override val key get() = "b$blockIndex"
    }

    data class TableRow(val blockIndex: Int, val table: Block.SimpleTable, val row: Int) : RenderItem {
        override val key get() = "b$blockIndex-r$row"
    }

    data class ListRow(val blockIndex: Int, val list: Block.ListBlock, val item: Int) : RenderItem {
        override val key get() = "b$blockIndex-i$item"
    }
}

private fun flatten(blocks: List<Block>): List<RenderItem> = buildList {
    blocks.forEachIndexed { i, block ->
        when (block) {
            is Block.SimpleTable -> {
                if (block.headers.isNotEmpty()) add(RenderItem.TableRow(i, block, HEADER_ROW))
                block.rows.indices.forEach { r -> add(RenderItem.TableRow(i, block, r)) }
            }
            is Block.ListBlock ->
                block.items.indices.forEach { r -> add(RenderItem.ListRow(i, block, r)) }
            else -> add(RenderItem.Whole(i, block))
        }
    }
}

// --- Styles -----------------------------------------------------------------

/** An sp quantity as Dp, so em-derived spacing scales exactly with the text. */
@Composable
private fun Float.textDp(): Dp = with(LocalDensity.current) { this@textDp.sp.toDp() }

// Pure ArticleTypography arithmetic — reads nothing from composition.
private fun bodySize(scalePercent: Int): Float =
    ArticleTypography.spFor(ArticleTypography.BODY_EM, scalePercent)

/** Immutable and theme-independent — built once, not per recomposition. */
private val GRAYSCALE = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/** The May css 0.05-grid-unit rule, from the content-secondary token (no color literals). */
@Composable
private fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(0.05f.gridUnitsAsDp())
            .alpha(0.35f)
            .background(LightThemeTokens.colors.contentSecondary),
    )
}

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
            Hairline(Modifier.padding(top = 0.3f.gridUnitsAsDp()))
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
private fun ListRowView(list: Block.ListBlock, index: Int, scalePercent: Int, lastInList: Boolean) {
    val body = bodySize(scalePercent)
    val blockEnd = if (lastInList) {
        Modifier.padding(bottom = (body * ArticleTypography.PARAGRAPH_SPACING_EM).textDp())
    } else {
        Modifier
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 1f.gridUnitsAsDp())
            .then(blockEnd),
    ) {
        val item = list.items[index]
        ListItemRow(item, marker = marker(list.ordered, index), indentEm = 0f, scalePercent)
        item.children.forEachIndexed { childIndex, child ->
            ListItemRow(
                child,
                marker = marker(list.ordered, childIndex),
                indentEm = ArticleTypography.LIST_INDENT_EM,
                scalePercent,
            )
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

private sealed interface ImageLoad {
    data object Loading : ImageLoad
    data class Ready(val bitmap: androidx.compose.ui.graphics.ImageBitmap) : ImageLoad
    data object Failed : ImageLoad
}

@Composable
private fun FigureView(block: Block.Figure, scalePercent: Int) {
    val load = androidx.compose.runtime.produceState<ImageLoad>(ImageLoad.Loading, block.src) {
        value = Images.load(block.src)?.let { ImageLoad.Ready(it) } ?: ImageLoad.Failed
    }.value

    // Failure drops the WHOLE figure — image and caption together (§10.8).
    if (load is ImageLoad.Failed) return

    val body = bodySize(scalePercent)
    val aspect = if (block.width != null && block.width!! > 0 && block.height != null && block.height!! > 0) {
        block.width!!.toFloat() / block.height!!.toFloat()
    } else {
        null
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 1f.gridUnitsAsDp())
            .padding(bottom = (body * ArticleTypography.PARAGRAPH_SPACING_EM).textDp()),
    ) {
        when (load) {
            is ImageLoad.Loading -> if (aspect != null) {
                // Aspect-ratio placeholder: correct space reserved, no mid-article gap.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspect),
                )
            }
            is ImageLoad.Ready -> {
                Image(
                    bitmap = load.bitmap,
                    contentDescription = block.caption,
                    contentScale = ContentScale.FillWidth,
                    colorFilter = GRAYSCALE,
                    modifier = Modifier.fillMaxWidth(),
                )
                // Caption only once the image exists — no transient orphan
                // caption while loading or during a slow failure (M6 review).
                block.caption?.let { caption ->
                    Text(
                        text = caption,
                        style = bodyStyle(ArticleTypography.CAPTION_EM, scalePercent)
                            .copy(color = LightThemeTokens.colors.contentSecondary),
                        modifier = Modifier.padding(top = 0.3f.gridUnitsAsDp()),
                    )
                }
            }
            is ImageLoad.Failed -> Unit // unreachable (early return)
        }
    }
}

@Composable
private fun InfoboxCardView(block: Block.InfoboxCard, scalePercent: Int) {
    if (block.title == null && block.rows.isEmpty()) return
    val body = bodySize(scalePercent)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 1f.gridUnitsAsDp())
            .padding(bottom = (body * ArticleTypography.PARAGRAPH_SPACING_EM).textDp()),
    ) {
        Hairline()
        block.title?.let { title ->
            Text(
                text = title,
                style = bodyStyle(ArticleTypography.INFOBOX_EM, scalePercent)
                    .copy(fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 0.4f.gridUnitsAsDp()),
                textAlign = TextAlign.Center,
            )
            Hairline()
        }
        block.rows.forEach { row ->
            Column(modifier = Modifier.padding(vertical = 0.3f.gridUnitsAsDp())) {
                Text(
                    text = row.label,
                    style = bodyStyle(ArticleTypography.CAPTION_EM, scalePercent)
                        .copy(fontWeight = FontWeight.Bold, color = LightThemeTokens.colors.contentSecondary),
                )
                Text(
                    text = row.value,
                    style = bodyStyle(ArticleTypography.INFOBOX_EM, scalePercent),
                )
            }
        }
        Hairline()
    }
}

@Composable
private fun TableRowLine(
    cells: List<String>,
    header: Boolean,
    scalePercent: Int,
    scroll: ScrollState,
    lastInTable: Boolean,
) {
    val body = bodySize(scalePercent)
    val cellMin = (body * ArticleTypography.TABLE_CELL_MIN_EM).textDp()
    val blockEnd = if (lastInTable) {
        Modifier.padding(bottom = (body * ArticleTypography.PARAGRAPH_SPACING_EM).textDp())
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 1f.gridUnitsAsDp())
            .then(blockEnd)
            .horizontalScroll(scroll),
    ) {
        val style = bodyStyle(ArticleTypography.CAPTION_EM, scalePercent)
            .let { if (header) it.copy(fontWeight = FontWeight.Bold) else it }
        cells.forEach { cell ->
            Text(
                text = cell,
                style = style,
                modifier = Modifier
                    .widthIn(min = cellMin)
                    .padding(
                        end = 0.6f.gridUnitsAsDp(),
                        bottom = if (header) 0.2f.gridUnitsAsDp() else 0.15f.gridUnitsAsDp(),
                    ),
            )
        }
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
