package com.noydra.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private const val REFERENCE_WIDTH_DP = 412f
private const val MIN_SCALE = 0.85f
private const val MAX_FONT_SCALE = 1.3f

@Immutable
data class Dimens(
    val scale: Float,
    val compact: Boolean,
    val screenPadding: Dp,
    val cardPadding: Dp,
    val sectionGap: Dp,
    val modalPadding: Dp,
)

val LocalDimens = staticCompositionLocalOf {
    Dimens(scale = 1f, compact = false, screenPadding = 22.dp, cardPadding = 20.dp, sectionGap = 14.dp, modalPadding = 18.dp)
}

val dimens: Dimens
    @Composable get() = LocalDimens.current

@Composable
fun scaledSp(size: Int): TextUnit = (size * LocalDimens.current.scale).sp

@Composable
fun ResponsiveRoot(content: @Composable () -> Unit) {
    BoxWithConstraints {
        val width = maxWidth
        val compact = width < 420.dp
        val scale = (width.value / REFERENCE_WIDTH_DP).coerceIn(MIN_SCALE, 1f)
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(MAX_FONT_SCALE)),
            LocalDimens provides Dimens(
                scale = scale,
                compact = compact,
                screenPadding = if (compact) 16.dp else 22.dp,
                cardPadding = if (compact) 16.dp else 20.dp,
                sectionGap = if (compact) 12.dp else 14.dp,
                modalPadding = if (compact) 16.dp else 18.dp,
            ),
            content = content,
        )
    }
}

@Composable
fun FitText(
    text: String,
    size: Int,
    bold: Boolean = false,
    color: Color = Colors.text,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Center,
    maxLines: Int = 1,
    fontFamily: FontFamily? = null,
) {
    val max = scaledSp(size)
    BasicText(
        text,
        modifier,
        style = LocalTextStyle.current.merge(
            TextStyle(
                color = color,
                fontSize = max,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                textAlign = align,
                lineHeight = 1.25.em,
                fontFamily = fontFamily,
            )
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = (max.value * 0.7f).sp, maxFontSize = max, stepSize = 0.5.sp),
    )
}

@Composable
fun AdaptiveRow(
    modifier: Modifier = Modifier,
    gap: Dp = 12.dp,
    stackedGap: Dp = 8.dp,
    stackedEndAlignment: Alignment.Horizontal = Alignment.Start,
    start: @Composable () -> Unit,
    end: @Composable () -> Unit,
) {
    Layout(contents = listOf({ Box { start() } }, { Box { end() } }), modifier) { measurables, constraints ->
        val startItem = measurables[0].first()
        val endItem = measurables[1].first()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val gapPx = gap.roundToPx()
        val maxWidth = constraints.maxWidth
        val fitsInRow = !constraints.hasBoundedWidth ||
            startItem.maxIntrinsicWidth(Constraints.Infinity) + gapPx + endItem.maxIntrinsicWidth(Constraints.Infinity) <= maxWidth

        if (fitsInRow) {
            val endPlaceable = endItem.measure(loose)
            val startMax = if (constraints.hasBoundedWidth) (maxWidth - endPlaceable.width - gapPx).coerceAtLeast(0) else Constraints.Infinity
            val startPlaceable = startItem.measure(loose.copy(maxWidth = startMax))
            val width = if (constraints.hasBoundedWidth) maxWidth else startPlaceable.width + gapPx + endPlaceable.width
            val height = maxOf(startPlaceable.height, endPlaceable.height)
            layout(width, height) {
                startPlaceable.place(0, (height - startPlaceable.height) / 2)
                endPlaceable.place(width - endPlaceable.width, (height - endPlaceable.height) / 2)
            }
        } else {
            val startPlaceable = startItem.measure(loose)
            val endPlaceable = endItem.measure(loose)
            val stackedGapPx = stackedGap.roundToPx()
            layout(maxWidth, startPlaceable.height + stackedGapPx + endPlaceable.height) {
                startPlaceable.place(0, 0)
                endPlaceable.place(
                    stackedEndAlignment.align(endPlaceable.width, maxWidth, layoutDirection),
                    startPlaceable.height + stackedGapPx,
                )
            }
        }
    }
}

@Composable
fun AdaptiveButtonRow(modifier: Modifier = Modifier, gap: Dp = 8.dp, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val maxWidth = constraints.maxWidth
        val count = measurables.size
        if (count == 0) return@Layout layout(0, 0) {}
        val cell = (maxWidth - gapPx * (count - 1)) / count
        val fitsInRow = measurables.all { it.maxIntrinsicWidth(Constraints.Infinity) <= cell }

        if (fitsInRow) {
            val height = measurables.maxOf { it.minIntrinsicHeight(cell) }
            val placeables = measurables.map { it.measure(Constraints.fixed(cell, height)) }
            layout(maxWidth, height) {
                placeables.forEachIndexed { index, placeable -> placeable.place(index * (cell + gapPx), 0) }
            }
        } else {
            val placeables = measurables.map { it.measure(Constraints(minWidth = maxWidth, maxWidth = maxWidth)) }
            val height = placeables.sumOf { it.height } + gapPx * (count - 1)
            layout(maxWidth, height) {
                var y = 0
                for (placeable in placeables) {
                    placeable.place(0, y)
                    y += placeable.height + gapPx
                }
            }
        }
    }
}
