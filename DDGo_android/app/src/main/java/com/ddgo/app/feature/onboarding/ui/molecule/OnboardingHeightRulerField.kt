package com.ddgo.app.feature.onboarding.ui.molecule

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.core.ui.theme.PretendardFamily
import com.ddgo.app.core.ui.tokens.DdgoColorTokens
import com.ddgo.app.feature.onboarding.ui.shared.tokens.OnboardingTokens
import com.ddgo.app.feature.profile.ProfileStrings
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun OnboardingHeightRulerField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    initializeIfBlank: Boolean,
    modifier: Modifier = Modifier,
    valueRange: IntRange = 120..220,
    defaultValue: Int = 170
) {
    val parsedValue = value.toFloatOrNull()?.roundToInt()?.coerceIn(valueRange.first, valueRange.last)
    val resolvedValue = parsedValue ?: defaultValue.coerceIn(valueRange.first, valueRange.last)

    LaunchedEffect(initializeIfBlank, value, resolvedValue, onValueChange) {
        if (initializeIfBlank && value.isBlank()) {
            onValueChange(resolvedValue.toString())
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OnboardingSectionLabel(text = ProfileStrings.BodyProfileFieldLabelHeight)

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = OnboardingTokens.CardFill
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 18.dp)
            ) {
                OnboardingMeasureRulerPicker(
                    value = resolvedValue,
                    onValueChange = { onValueChange(it.toString()) },
                    valueRange = valueRange,
                    unitLabel = "cm",
                    enabled = enabled
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OnboardingMeasureRulerPicker(
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: IntRange,
    unitLabel: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    step: Int = 1,
    majorTickInterval: Int = 5
) {
    val tickValues = remember(valueRange, step) {
        buildList {
            var current = valueRange.first
            while (current <= valueRange.last) {
                add(current)
                current += step
            }
        }
    }
    val coercedValue = value.coerceIn(valueRange.first, valueRange.last)
    val targetIndex = remember(coercedValue, tickValues) {
        tickValues.indexOfClosest(coercedValue)
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = targetIndex)
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val currentValue by rememberUpdatedState(value)
    val onValueChangeState by rememberUpdatedState(onValueChange)
    val hapticFeedback = LocalHapticFeedback.current

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth()
    ) {
        val tickSpacing = 12.dp
        val sidePadding = ((maxWidth - tickSpacing) / 2).coerceAtLeast(0.dp)
        val centeredIndex by remember {
            derivedStateOf {
                val layoutInfo = listState.layoutInfo
                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                layoutInfo.visibleItemsInfo
                    .minByOrNull { item ->
                        abs((item.offset + item.size / 2) - viewportCenter)
                    }
                    ?.index
                    ?.coerceIn(0, tickValues.lastIndex)
                    ?: targetIndex
            }
        }

        LaunchedEffect(targetIndex) {
            snapshotFlow { listState.isScrollInProgress }
                .filter { isScrolling -> !isScrolling }
                .first()

            if (centeredIndex != targetIndex) {
                listState.animateScrollToItem(targetIndex)
            }
        }

        LaunchedEffect(listState, tickValues, enabled) {
            var isFirstEmission = true
            snapshotFlow { centeredIndex to listState.isScrollInProgress }
                .distinctUntilChanged()
                .collect { (index, isScrolling) ->
                    val resolvedValue = tickValues.getOrNull(index) ?: return@collect
                    if (resolvedValue != currentValue) {
                        onValueChangeState(resolvedValue)
                    }
                    if (!isFirstEmission && enabled && isScrolling) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    isFirstEmission = false
                }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = coercedValue.toString(),
                    color = OnboardingTokens.Graphite,
                    fontFamily = PretendardFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 54.sp,
                    lineHeight = 58.sp
                )
                Text(
                    text = unitLabel,
                    modifier = Modifier.padding(bottom = 7.dp),
                    color = OnboardingTokens.GraphiteMuted,
                    fontFamily = PretendardFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 22.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
            ) {
                LazyRow(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = sidePadding),
                    flingBehavior = snapFlingBehavior,
                    userScrollEnabled = enabled
                ) {
                    items(
                        count = tickValues.size,
                        key = { index -> tickValues[index] }
                    ) { index ->
                        val tickValue = tickValues[index]
                        val isMajorTick = tickValue % majorTickInterval == 0
                        val tickColor = if (isMajorTick) {
                            Color(0xFFC7D2DF)
                        } else {
                            Color(0xFFDDE5EE)
                        }

                        Column(
                            modifier = Modifier.width(tickSpacing),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .height(72.dp)
                                    .width(tickSpacing)
                            ) {
                                val lineHeight = if (isMajorTick) 52.dp.toPx() else 30.dp.toPx()
                                val startY = size.height - lineHeight
                                drawLine(
                                    color = tickColor,
                                    start = Offset(x = size.width / 2f, y = startY),
                                    end = Offset(x = size.width / 2f, y = size.height),
                                    strokeWidth = if (isMajorTick) 2.dp.toPx() else 1.4.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }

                            Box(
                                modifier = Modifier.height(22.dp),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                if (isMajorTick) {
                                    Text(
                                        text = tickValue.toString(),
                                        color = Color(0xFFAEBAC7),
                                        fontFamily = PretendardFamily,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .width(3.dp)
                        .height(88.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    DdgoColorTokens.BrandBlue.copy(alpha = 0.85f),
                                    DdgoColorTokens.BrandBlue
                                )
                            )
                        )
                )
            }
        }
    }
}

private fun List<Int>.indexOfClosest(target: Int): Int {
    return indices.minByOrNull { index -> abs(this[index] - target) } ?: 0
}
