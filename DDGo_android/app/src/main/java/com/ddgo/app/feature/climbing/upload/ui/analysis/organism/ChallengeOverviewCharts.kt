package com.ddgo.app.feature.climbing.upload.ui.analysis.organism

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.feature.climbing.upload.AnalysisMuted
import com.ddgo.app.feature.climbing.upload.AnalysisPanelColor
import com.ddgo.app.feature.climbing.upload.AnalysisPrimary
import com.ddgo.app.feature.climbing.upload.AnalysisSecondary
import com.ddgo.app.feature.climbing.upload.AnalysisText
import com.ddgo.app.feature.climbing.upload.ChallengeCruxDistributionItem
import com.ddgo.app.feature.climbing.upload.ChallengeFinalAnalysisSummary
import com.ddgo.app.feature.climbing.upload.ChallengeTrendPoint
import com.ddgo.app.feature.climbing.upload.FinalAnalysisUnknownMetricText
import com.ddgo.app.feature.climbing.upload.ui.analysis.molecule.ChartLineLegendItem

private val ContactTrendColor = Color(0xFFFFB84D)

@Composable
internal fun ChallengeMetricOverviewPanel(
    summary: ChallengeFinalAnalysisSummary,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ChallengeMetricCard(
                title = "최고 도달",
                value = summary.bestReachedHoldsText + summary.bestReachedHoldsSuffix.orEmpty(),
                modifier = Modifier.weight(1f)
            )
            ChallengeMetricCard(
                title = "평균 도달",
                value = summary.averageReachedHoldsText + summary.averageReachedHoldsSuffix.orEmpty(),
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ChallengeMetricCard(
                title = "평균 균형 유지",
                value = summary.averageInsideSupportRatioText,
                modifier = Modifier.weight(1f)
            )
            ChallengeMetricCard(
                title = "평균 손발 지지",
                value = summary.averageStableContactRatioText,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun ChallengeAttemptTrendPanel(
    trendPoints: List<ChallengeTrendPoint>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(AnalysisPanelColor)
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Column {
            Text(
                text = "시도별 변화",
                color = AnalysisText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "각 시도에서 얼마나 더 멀리 갔고, 균형과 손발 지지가 어떻게 바뀌었는지 보는 그래프입니다.",
                color = AnalysisMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            AttemptTrendChart(
                trendPoints = trendPoints,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(188.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChartLineLegendItem(
                    color = AnalysisPrimary,
                    label = "도달 진행도"
                )
                ChartLineLegendItem(
                    color = AnalysisSecondary,
                    label = "균형 유지"
                )
                ChartLineLegendItem(
                    color = ContactTrendColor,
                    label = "손발 지지"
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GuideChip(text = "위로 갈수록 좋음")
                GuideChip(text = "시도 번호 순서")
            }
        }
    }
}

@Composable
internal fun ChallengeCruxDistributionPanel(
    distribution: List<ChallengeCruxDistributionItem>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(AnalysisPanelColor)
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Column {
            Text(
                text = "반복 난구간 분포",
                color = AnalysisText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "각 시도에서 가장 버거웠던 홀드를 세어본 그래프입니다. 막대가 높을수록 여러 번 반복해서 어려웠던 구간입니다.",
                color = AnalysisMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
            Spacer(modifier = Modifier.height(18.dp))
            if (distribution.isEmpty()) {
                Text(
                    text = "반복 난구간 데이터가 충분하지 않아 분포를 그리지 못했습니다.",
                    color = AnalysisMuted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            } else {
                CruxDistributionChart(
                    distribution = distribution,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(188.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GuideChip(text = "막대가 높을수록 반복")
                    GuideChip(text = "홀드 번호 기준")
                }
            }
        }
    }
}

@Composable
private fun ChallengeMetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(AnalysisPanelColor)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                color = AnalysisMuted,
                fontSize = 12.sp
            )
            Text(
                text = value.ifBlank { FinalAnalysisUnknownMetricText },
                color = AnalysisText,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun GuideChip(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(AnalysisMuted.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = AnalysisText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun AttemptTrendChart(
    trendPoints: List<ChallengeTrendPoint>,
    modifier: Modifier = Modifier
) {
    val reachedValues = trendPoints.map { it.reachedPercent }
    val balanceValues = trendPoints.map { it.insideSupportPercent }
    val contactValues = trendPoints.map { it.stableContactPercent }

    Column {
        Canvas(modifier = modifier) {
            if (trendPoints.isEmpty()) return@Canvas

            val leftPadding = 10.dp.toPx()
            val topPadding = 14.dp.toPx()
            val bottomPadding = 18.dp.toPx()
            val chartWidth = (size.width - leftPadding).coerceAtLeast(1f)
            val chartHeight = (size.height - topPadding - bottomPadding).coerceAtLeast(1f)

            fun xOf(index: Int): Float {
                if (trendPoints.size == 1) return leftPadding + chartWidth / 2f
                return leftPadding + chartWidth * (index / (trendPoints.size - 1).toFloat())
            }

            fun yOf(value: Int): Float {
                return topPadding + chartHeight * (1f - (value.coerceIn(0, 100) / 100f))
            }

            repeat(5) { step ->
                val fraction = step / 4f
                val y = topPadding + chartHeight * fraction
                drawLine(
                    color = Color.White.copy(alpha = 0.06f),
                    start = Offset(leftPadding, y),
                    end = Offset(leftPadding + chartWidth, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            drawSeries(
                values = reachedValues,
                color = AnalysisPrimary,
                xOf = ::xOf,
                yOf = ::yOf
            )
            drawSeries(
                values = balanceValues,
                color = AnalysisSecondary,
                xOf = ::xOf,
                yOf = ::yOf
            )
            drawSeries(
                values = contactValues,
                color = ContactTrendColor,
                xOf = ::xOf,
                yOf = ::yOf
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (trendPoints.size > 1) {
                Arrangement.SpaceBetween
            } else {
                Arrangement.Center
            }
        ) {
            trendPoints.forEach { point ->
                Text(
                    text = "${point.attemptNo}차",
                    color = AnalysisMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSeries(
    values: List<Int?>,
    color: Color,
    xOf: (Int) -> Float,
    yOf: (Int) -> Float
) {
    val indexedValues = values.mapIndexedNotNull { index, value ->
        value?.let { index to it }
    }
    if (indexedValues.isEmpty()) return

    if (indexedValues.size >= 2) {
        val path = Path().apply {
            moveTo(xOf(indexedValues.first().first), yOf(indexedValues.first().second))
            indexedValues.drop(1).forEach { (index, value) ->
                lineTo(xOf(index), yOf(value))
            }
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
    }

    indexedValues.forEach { (index, value) ->
        drawCircle(
            color = color,
            radius = 4.dp.toPx(),
            center = Offset(xOf(index), yOf(value))
        )
        drawCircle(
            color = Color(0xFF0F1115),
            radius = 2.dp.toPx(),
            center = Offset(xOf(index), yOf(value))
        )
    }
}

@Composable
private fun CruxDistributionChart(
    distribution: List<ChallengeCruxDistributionItem>,
    modifier: Modifier = Modifier
) {
    val maxCount = distribution.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            distribution.forEach { item ->
                val barFraction = item.count / maxCount.toFloat()
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        text = "${item.count}회",
                        color = AnalysisText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height((16f + 88f * barFraction).dp)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                            .background(AnalysisPrimary)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            distribution.forEach { item ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${item.holdNo}번",
                        color = AnalysisMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
