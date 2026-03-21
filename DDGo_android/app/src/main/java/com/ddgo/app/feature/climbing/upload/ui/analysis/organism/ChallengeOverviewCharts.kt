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
                title = "\uCD5C\uACE0 \uB3C4\uB2EC",
                value = summary.bestReachedHoldsText + summary.bestReachedHoldsSuffix.orEmpty(),
                modifier = Modifier.weight(1f)
            )
            ChallengeMetricCard(
                title = "\uD3C9\uADE0 \uB3C4\uB2EC",
                value = summary.averageReachedHoldsText + summary.averageReachedHoldsSuffix.orEmpty(),
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ChallengeMetricCard(
                title = "\uD3C9\uADE0 \uADE0\uD615 \uC720\uC9C0",
                value = summary.averageInsideSupportRatioText,
                modifier = Modifier.weight(1f)
            )
            ChallengeMetricCard(
                title = "\uD3C9\uADE0 \uC190\uBC1C \uC9C0\uC9C0",
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
                text = "\uC2DC\uB3C4\uBCC4 \uBCC0\uD654",
                color = AnalysisText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "\uAC01 \uC2DC\uB3C4\uC5D0\uC11C \uC5BC\uB9C8\uB098 \uB354 \uBA40\uB9AC \uAC14\uACE0, \uADE0\uD615\uACFC \uC190\uBC1C \uC9C0\uC9C0\uAC00 \uC5B4\uB5BB\uAC8C \uBC14\uB00C\uC5C8\uB294\uC9C0 \uBCF4\uB294 \uADF8\uB798\uD504\uC785\uB2C8\uB2E4.",
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
                    label = "\uB3C4\uB2EC \uC9C4\uD589\uB3C4"
                )
                ChartLineLegendItem(
                    color = AnalysisSecondary,
                    label = "\uADE0\uD615 \uC720\uC9C0"
                )
                ChartLineLegendItem(
                    color = ContactTrendColor,
                    label = "\uC190\uBC1C \uC9C0\uC9C0"
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GuideChip(text = "\uC704\uB85C \uAC08\uC218\uB85D \uC88B\uC74C")
                GuideChip(text = "\uC2DC\uB3C4 \uBC88\uD638 \uC21C\uC11C")
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
                text = "\uBC18\uBCF5 \uB09C\uAD6C\uAC04 \uBD84\uD3EC",
                color = AnalysisText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "\uAC01 \uC2DC\uB3C4\uC5D0\uC11C \uAC00\uC7A5 \uBC84\uAC70\uC6E0\uB358 \uD640\uB4DC\uB97C \uC138\uC5B4\uBCF8 \uADF8\uB798\uD504\uC785\uB2C8\uB2E4. \uB9C9\uB300\uAC00 \uB192\uC744\uC218\uB85D \uC5EC\uB7EC \uBC88 \uBC18\uBCF5\uD574\uC11C \uC5B4\uB824\uC6E0\uB358 \uAD6C\uAC04\uC785\uB2C8\uB2E4.",
                color = AnalysisMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
            Spacer(modifier = Modifier.height(18.dp))
            if (distribution.isEmpty()) {
                Text(
                    text = "\uBC18\uBCF5 \uB09C\uAD6C\uAC04 \uB370\uC774\uD130\uAC00 \uCDA9\uBD84\uD558\uC9C0 \uC54A\uC544 \uBD84\uD3EC\uB97C \uADF8\uB9AC\uC9C0 \uBABB\uD588\uC2B5\uB2C8\uB2E4.",
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
                    GuideChip(text = "\uB9C9\uB300\uAC00 \uB192\uC744\uC218\uB85D \uBC18\uBCF5")
                    GuideChip(text = "\uD640\uB4DC \uBC88\uD638 \uAE30\uC900")
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
                    text = "${point.attemptNo}\uCC28",
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
                        text = "${item.count}\uD68C",
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
                        text = "${item.holdNo}\uBC88",
                        color = AnalysisMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
