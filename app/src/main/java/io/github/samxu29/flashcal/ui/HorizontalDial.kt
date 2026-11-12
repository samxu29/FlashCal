package io.github.samxu29.flashcal.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Fades the wheel's ends, leaving room for the icon on the left. */
private val EdgeFade = Brush.horizontalGradient(
    0f to Color.Transparent,
    0.1f to Color.Transparent,
    0.24f to Color.Black,
    0.76f to Color.Black,
    0.9f to Color.Transparent,
    1f to Color.Transparent,
)

/** Request to spin the wheel to [index]; a new [token] starts a new spin. */
@Immutable
data class DialSpin(val index: Int, val token: Int)

/**
 * Horizontal wheel with the selected value snapped to the stationary center mark.
 *
 * With an [icon], the icon sits over the left end of a single slim row, the
 * wheel's ends fade out, and [label] is only read out by accessibility
 * services; the wheel still spans the full width, so its center mark is the
 * screen's center. Without one, [label] is a small caption above the wheel.
 *
 * A [spin] animates the wheel to its index and reports [onSelect] once it lands,
 * not for each mark it passes.
 */
@Composable
fun HorizontalDial(
    label: String,
    values: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
    spin: DialSpin? = null,
    icon: ImageVector? = null,
) {
    if (values.isEmpty()) return
    val selected = selectedIndex.coerceIn(values.indices)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selected)
    val scope = rememberCoroutineScope()
    val currentOnSelect by rememberUpdatedState(onSelect)
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val fontScale = density.fontScale.coerceAtLeast(1f)
    val cellWidth = 76.dp * fontScale
    var spinning by remember { mutableStateOf(false) }
    val wheelHeight = 46.dp * fontScale
    val selectionShape = RoundedCornerShape(12.dp)
    val centeredIndex by remember(listState) {
        derivedStateOf {
            val layout = listState.layoutInfo
            val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
            layout.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2f - center) }?.index
                ?: selected
        }
    }

    // Publish the value under the center marker as the wheel moves and snaps.
    LaunchedEffect(listState) {
        var previousIndex = centeredIndex
        snapshotFlow { centeredIndex to listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { (index, scrolling) ->
                if (listState.layoutInfo.totalItemsCount > 0) {
                    if (scrolling && index != previousIndex) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    }
                    previousIndex = index
                    if (!spinning) currentOnSelect(index)
                }
            }
    }
    LaunchedEffect(spin?.token) {
        val target = spin?.index?.coerceIn(values.indices) ?: return@LaunchedEffect
        val distance = (target - centeredIndex) * with(density) { cellWidth.toPx() }
        spinning = true
        try {
            if (distance != 0f) {
                listState.animateScrollBy(distance, tween(durationMillis = 700, easing = FastOutSlowInEasing))
            }
            listState.scrollToItem(target)
        } finally {
            spinning = false
        }
        currentOnSelect(target)
    }
    // Synchronize changes made outside the wheel without fighting a user's drag.
    LaunchedEffect(selected) {
        if (!spinning && !listState.isScrollInProgress && centeredIndex != selected) {
            listState.scrollToItem(selected)
        }
    }

    val wheel: @Composable (Modifier) -> Unit = { wheelModifier ->
        BoxWithConstraints(wheelModifier.height(wheelHeight)) {
            val edgePadding = ((maxWidth - cellWidth) / 2).coerceAtLeast(0.dp)
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(width = cellWidth, height = wheelHeight - 8.dp)
                    .background(Color.White.copy(alpha = 0.12f), selectionShape),
            )
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = edgePadding),
                flingBehavior = rememberSnapFlingBehavior(listState),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .clearAndSetSemantics {
                        contentDescription = label
                        stateDescription = values[selected]
                        progressBarRangeInfo = ProgressBarRangeInfo(
                            selected.toFloat(),
                            0f..values.lastIndex.toFloat(),
                            (values.size - 2).coerceAtLeast(0),
                        )
                        setProgress { value ->
                            val index = value.roundToInt().coerceIn(values.indices)
                            currentOnSelect(index)
                            scope.launch { listState.animateScrollToItem(index) }
                            true
                        }
                    },
            ) {
                itemsIndexed(values) { index, value ->
                    val isCentered = index == centeredIndex
                    val distance = abs(index - centeredIndex)
                    val alpha = when {
                        isCentered -> 1f
                        distance == 1 -> 0.65f
                        else -> 0.35f
                    }
                    Column(
                        Modifier
                            .width(cellWidth)
                            .height(wheelHeight)
                            .padding(vertical = 4.dp)
                            .clip(selectionShape)
                            .clickable(onClick = confirmationClick {
                                scope.launch { listState.animateScrollToItem(index) }
                            }),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                    ) {
                        Box(
                            Modifier
                                .size(width = 1.dp, height = 5.dp)
                                .background(Color.White.copy(alpha = alpha)),
                        )
                        Text(
                            text = value,
                            color = Color.White.copy(alpha = alpha),
                            fontSize = if (isCentered) 19.sp else 15.sp,
                            fontWeight = if (isCentered) FontWeight.SemiBold else FontWeight.Normal,
                            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                            maxLines = 1,
                        )
                    }
                }
            }
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .size(width = 2.dp, height = 5.dp)
                    .background(Color.White, RoundedCornerShape(1.dp)),
            )
        }
    }

    if (icon != null) {
        Box(modifier.fillMaxWidth()) {
            wheel(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(EdgeFade, blendMode = BlendMode.DstIn)
                    },
            )
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 14.dp).size(20.dp),
            )
        }
    } else {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = if (caption == null) label else "$label · $caption",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            wheel(Modifier.fillMaxWidth())
        }
    }
}
