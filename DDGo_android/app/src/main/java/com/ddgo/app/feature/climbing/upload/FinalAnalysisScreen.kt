package com.ddgo.app.feature.climbing.upload

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.roundToInt

private enum class ProblemAnalysisTab(val label: String) {
    Stats("문제 통계"),
    Stability("안정도"),
    Failure("실패 원인")
}

@Composable
fun FinalAnalysisScreen(
    viewModel: UploadViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToMain: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val totalAttempts = viewModel.allAttemptUris.size.coerceAtLeast(1)
    val totalHolds = viewModel.detectedHolds.size.takeIf { it > 0 } ?: 14
    val attemptSummaries = remember(
        viewModel.allAttemptUris,
        viewModel.analysisPoints,
        viewModel.attemptDummyResults,
        totalHolds
    ) {
        buildAttemptSummaries(
            totalAttempts = viewModel.allAttemptUris.size,
            fallbackPoints = viewModel.analysisPoints,
            dummyResults = viewModel.attemptDummyResults,
            totalHolds = totalHolds
        )
    }
    var selectedAttempt by rememberSaveable {
        mutableIntStateOf(totalAttempts.coerceIn(1, attemptSummaries.size))
    }
    var selectedTab by rememberSaveable { mutableStateOf(ProblemAnalysisTab.Stats) }

    val currentSummary = attemptSummaries[(selectedAttempt - 1).coerceIn(0, attemptSummaries.lastIndex)]
    val averageReachedHolds = remember(attemptSummaries) {
        attemptSummaries.map { it.reachedHolds }.average().roundToInt()
    }
    val averageBalanceRatio = remember(attemptSummaries) {
        attemptSummaries.map { it.balanceRatio }.average().roundToInt()
    }
    val overallSuccess = remember(attemptSummaries) { attemptSummaries.any { it.isSuccess } }
    val combinedTimeline = remember(attemptSummaries) {
        val maxLength = attemptSummaries.maxOfOrNull { it.stabilityTimeline.size } ?: 0
        List(maxLength) { index ->
            attemptSummaries.map { summary ->
                summary.stabilityTimeline.getOrElse(index) {
                    summary.stabilityTimeline.lastOrNull() ?: 0.5f
                }
            }.average().toFloat()
        }
    }
    val focusFraction = remember(selectedAttempt, totalAttempts) {
        if (totalAttempts <= 1) null else (selectedAttempt - 1).toFloat() / (totalAttempts - 1).toFloat()
    }
    val displayDate = remember(viewModel.createdChallenge?.startedAt) {
        formatAnalysisDate(viewModel.createdChallenge?.startedAt)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AnalysisBgColor)
            .statusBarsPadding()
            .verticalScroll(scrollState)
    ) {
        ProblemAnalysisTopBar(onNavigateBack = onNavigateBack)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = viewModel.gymName.ifBlank { "클라이밍장" },
                    color = AnalysisText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = displayDate,
                    color = AnalysisMuted,
                    fontSize = 13.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (viewModel.difficultyLevel.isNotBlank()) {
                        HeaderChip(
                            text = viewModel.difficultyLevel,
                            background = Color.White,
                            contentColor = Color.Black
                        )
                    }
                    if (viewModel.holdColor.isNotBlank()) {
                        HeaderChip(
                            text = viewModel.holdColor,
                            background = holdColorToUiColor(viewModel.holdColor),
                            contentColor = if (viewModel.holdColor == "흰색") Color.Black else Color.White
                        )
                    }
                }
            }

            HoldOverviewPreview(
                bitmap = viewModel.bestFrameBitmap,
                holds = viewModel.detectedHolds,
                modifier = Modifier.size(width = 116.dp, height = 96.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF101114))
        ) {
            HoldOverviewPreview(
                bitmap = viewModel.bestFrameBitmap,
                holds = viewModel.detectedHolds,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(242.dp)
            )

            Text(
                text = "${selectedAttempt}차 시도 ${if (currentSummary.isSuccess) "성공" else "실패"}",
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        AnalysisSectionTabs(
            labels = ProblemAnalysisTab.entries.map { it.label },
            selectedIndex = selectedTab.ordinal,
            onSelected = { selectedTab = ProblemAnalysisTab.entries[it] }
        )

        when (selectedTab) {
            ProblemAnalysisTab.Stats -> {
                ProblemStatsSection(
                    overallSuccess = overallSuccess,
                    averageReachedHolds = averageReachedHolds,
                    totalHolds = totalHolds,
                    averageBalanceRatio = averageBalanceRatio,
                    timeline = combinedTimeline,
                    focusFraction = focusFraction
                )
            }

            ProblemAnalysisTab.Stability -> {
                StabilityDetailSection(
                    currentSummary = currentSummary,
                    timeline = combinedTimeline,
                    focusFraction = focusFraction
                )
            }

            ProblemAnalysisTab.Failure -> {
                FailureSummarySection(
                    summary = currentSummary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        AttemptChipRow(
            attemptCount = totalAttempts,
            selectedAttempt = selectedAttempt,
            onSelect = { selectedAttempt = it.coerceIn(1, totalAttempts) },
            modifier = Modifier.padding(horizontal = 22.dp)
        )

        Spacer(modifier = Modifier.height(22.dp))

        val actionText = if (totalAttempts > 1 && selectedAttempt < totalAttempts) {
            "다음 시도들과 비교분석 하기"
        } else {
            "분석 완료"
        }

        AnalysisGradientButton(
            text = actionText,
            onClick = {
                if (totalAttempts > 1 && selectedAttempt < totalAttempts) {
                    selectedAttempt += 1
                } else {
                    onNavigateToMain()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
        )

        Spacer(
            modifier = Modifier
                .height(24.dp)
                .navigationBarsPadding()
        )
    }
}

@Composable
private fun ProblemAnalysisTopBar(
    onNavigateBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .align(Alignment.CenterStart)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onNavigateBack
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "뒤로가기",
                tint = Color.White
            )
        }

        Text(
            text = "문제 분석",
            modifier = Modifier.align(Alignment.Center),
            color = AnalysisText,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ProblemStatsSection(
    overallSuccess: Boolean,
    averageReachedHolds: Int,
    totalHolds: Int,
    averageBalanceRatio: Int,
    timeline: List<Float>,
    focusFraction: Float?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MetricHeadline(
            title = "문제 풀이 여부",
            value = if (overallSuccess) "성공" else "실패",
            valueColor = if (overallSuccess) AnalysisSuccess else AnalysisFailure
        )

        Spacer(modifier = Modifier.height(34.dp))

        MetricHeadline(
            title = "평균 도달 홀드",
            value = "$averageReachedHolds",
            suffix = "/${totalHolds}번"
        )

        Spacer(modifier = Modifier.height(34.dp))

        MetricHeadline(
            caption = "평균 안정도",
            title = "무게중심 안정 비율",
            value = "$averageBalanceRatio%"
        )

        Spacer(modifier = Modifier.height(18.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(AnalysisPanelColor)
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            StabilityLineChart(
                data = timeline,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(184.dp),
                focusFraction = focusFraction
            )
        }
    }
}

@Composable
private fun StabilityDetailSection(
    currentSummary: AnalysisAttemptSummary,
    timeline: List<Float>,
    focusFraction: Float?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 26.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DetailStatCard(
                title = "현재 시도 안정도",
                value = "${currentSummary.balanceRatio}%",
                modifier = Modifier.weight(1f)
            )
            DetailStatCard(
                title = "분석 포인트",
                value = "${currentSummary.analysisPoints.size}개",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(AnalysisPanelColor)
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            Column {
                Text(
                    text = "시도 흐름",
                    color = AnalysisText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                StabilityLineChart(
                    data = timeline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(208.dp),
                    focusFraction = focusFraction
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = if (currentSummary.isSuccess) {
                "선택한 시도는 후반 안정도가 평균보다 높고 마지막 동작의 흔들림이 적었습니다."
            } else {
                "선택한 시도는 중반 이후 급격한 흔들림이 커졌습니다. 실패 원인 탭에서 해당 포인트를 확인해보세요."
            },
            color = AnalysisMuted,
            fontSize = 14.sp,
            lineHeight = 22.sp
        )
    }
}

@Composable
private fun FailureSummarySection(
    summary: AnalysisAttemptSummary
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 26.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "${summary.attemptNo}차 시도",
                color = AnalysisText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (summary.isSuccess) "성공" else "실패",
                color = if (summary.isSuccess) AnalysisSuccess else AnalysisFailure,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(AnalysisPanelColor)
                .padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (summary.analysisPoints.isEmpty()) {
                    Text(
                        text = "현재 시도에는 분석 포인트가 아직 없어요.",
                        color = AnalysisMuted,
                        fontSize = 14.sp
                    )
                } else {
                    summary.analysisPoints.forEach { point ->
                        Text(
                            text = buildAnnotatedString {
                                withStyle(
                                    SpanStyle(
                                        color = AnalysisPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                ) {
                                    append("%02d:%02d ".format(point.timeMs / 60_000L, (point.timeMs / 1_000L) % 60L))
                                }
                                append(point.description.replace("\n", " "))
                            },
                            color = AnalysisText,
                            fontSize = 15.sp,
                            lineHeight = 23.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (summary.isSuccess) {
                "완등한 시도이지만 반복해서 잘 먹힌 리듬을 다음 시도에도 유지해보세요."
            } else {
                "같은 포인트에서 반복해서 흔들리면 미션 탭의 교정 포인트를 먼저 적용하는 편이 좋습니다."
            },
            color = AnalysisMuted,
            fontSize = 14.sp,
            lineHeight = 22.sp
        )
    }
}

@Composable
private fun MetricHeadline(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    suffix: String? = null,
    valueColor: Color = AnalysisText
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        caption?.let {
            Text(
                text = it,
                color = AnalysisMuted,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        Text(
            text = title,
            color = AnalysisText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                color = valueColor,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold
            )
            suffix?.let {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = it,
                    color = AnalysisMuted,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun DetailStatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(AnalysisPanelColor)
            .padding(horizontal = 16.dp, vertical = 18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                color = AnalysisMuted,
                fontSize = 13.sp
            )
            Text(
                text = value,
                color = AnalysisText,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun HeaderChip(
    text: String,
    background: Color,
    contentColor: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = contentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
