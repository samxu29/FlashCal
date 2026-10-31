package io.github.samxu29.flashcal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

/** Horizontal wheel with the selected value snapped to the stationary center mark. */
@Composable
fun HorizontalDial(
    label: String,
    values: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    if (values.isEmpty()) return
    val selected = selectedIndex.coerceIn(values.indices)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selected)
    val scope = rememberCoroutineScope()
    val currentOnSelect by rememberUpdatedState(onSelect)
    val haptics = LocalHapticFeedback.current
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val cellWidth = 88.dp * fontScale
    val wheelHeight = 64.dp * fontScale
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
                    currentOnSelect(index)
                }
            }
    }
    // Synchronize changes made outside the wheel without fighting a user's drag.
    LaunchedEffect(selected) {
        if (!listState.isScrollInProgress && centeredIndex != selected) {
            listState.scrollToItem(selected)
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = if (caption == null) label else "$label · $caption",
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        BoxWithConstraints(Modifier.fillMaxWidth().height(wheelHeight)) {
            val edgePadding = ((maxWidth - cellWidth) / 2).coerceAtLeast(0.dp)
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(width = cellWidth, height = wheelHeight - 12.dp)
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
                            .padding(vertical = 6.dp)
                            .clip(selectionShape)
                            .clickable(onClick = confirmationClick {
                                scope.launch { listState.animateScrollToItem(index) }
                            }),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                    ) {
                        Box(
                            Modifier
                                .size(width = 1.dp, height = 8.dp)
                                .background(Color.White.copy(alpha = alpha)),
                        )
                        Text(
                            text = value,
                            color = Color.White.copy(alpha = alpha),
                            fontSize = if (isCentered) 22.sp else 18.sp,
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
                    .size(width = 2.dp, height = 6.dp)
                    .background(Color.White, RoundedCornerShape(1.dp)),
            )
        }
    }
}
