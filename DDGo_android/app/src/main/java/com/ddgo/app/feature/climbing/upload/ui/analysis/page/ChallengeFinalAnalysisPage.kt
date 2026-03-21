package com.ddgo.app.feature.climbing.upload.ui.analysis.page

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.domain.model.Hold
import com.ddgo.app.feature.climbing.upload.AnalysisBgColor
import com.ddgo.app.feature.climbing.upload.AnalysisFailure
import com.ddgo.app.feature.climbing.upload.AnalysisGradientButton
import com.ddgo.app.feature.climbing.upload.AnalysisMuted
import com.ddgo.app.feature.climbing.upload.AnalysisPanelColor
import com.ddgo.app.feature.climbing.upload.AnalysisSectionTabs
import com.ddgo.app.feature.climbing.upload.AnalysisSuccess
import com.ddgo.app.feature.climbing.upload.AnalysisText
import com.ddgo.app.feature.climbing.upload.ChallengeAttemptComparisonItem
import com.ddgo.app.feature.climbing.upload.ChallengeFinalAnalysisSummary
import com.ddgo.app.feature.climbing.upload.FinalAnalysisUnknownMetricText
import com.ddgo.app.feature.climbing.upload.HoldOverviewPreview
import com.ddgo.app.feature.climbing.upload.holdColorToUiColor
import com.ddgo.app.feature.climbing.upload.ui.analysis.molecule.AnalysisInsightCard
import com.ddgo.app.feature.climbing.upload.ui.analysis.molecule.HeaderChip
import com.ddgo.app.feature.climbing.upload.ui.analysis.organism.ChallengeAttemptTrendPanel
import com.ddgo.app.feature.climbing.upload.ui.analysis.organism.ChallengeCruxDistributionPanel
import com.ddgo.app.feature.climbing.upload.ui.analysis.organism.ChallengeMetricOverviewPanel

internal enum class ChallengeFinalAnalysisTab(val label: String) {
    Overview("\uC885\uD569"),
    Comparison("\uC2DC\uB3C4 \uBE44\uAD50"),
    Pattern("\uD575\uC2EC \uD328\uD134")
}

internal data class ChallengePreviewHeroState(
    val gymName: String,
    val displayDate: String,
    val difficultyLabel: String,
    val holdColorLabel: String,
    val attemptCount: Int,
    val overallSuccess: Boolean,
    val successAttemptCount: Int,
    val previewBitmap: Bitmap?,
    val previewHolds: List<Hold>
)

internal data class ChallengeFinalAnalysisPageState(
    val heroState: ChallengePreviewHeroState,
    val summary: ChallengeFinalAnalysisSummary
)

@Composable
internal fun ChallengeFinalAnalysisPage(
    state: ChallengeFinalAnalysisPageState,
    selectedTab: ChallengeFinalAnalysisTab,
    onNavigateBack: () -> Unit,
    onTabSelected: (ChallengeFinalAnalysisTab) -> Unit,
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AnalysisBgColor)
            .statusBarsPadding()
            .verticalScroll(scrollState)
    ) {
        ChallengeFinalAnalysisTopBar(onNavigateBack = onNavigateBack)

        ChallengePreviewHero(
            state = state.heroState,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        ChallengeSummaryCard(
            summary = state.summary,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp)
        )

        AnalysisSectionTabs(
            labels = ChallengeFinalAnalysisTab.entries.map { it.label },
            selectedIndex = selectedTab.ordinal,
            onSelected = { onTabSelected(ChallengeFinalAnalysisTab.entries[it]) }
        )

        when (selectedTab) {
            ChallengeFinalAnalysisTab.Overview -> {
                ChallengeOverviewTab(summary = state.summary)
            }

            ChallengeFinalAnalysisTab.Comparison -> {
                ChallengeComparisonTab(summary = state.summary)
            }

            ChallengeFinalAnalysisTab.Pattern -> {
                ChallengePatternTab(summary = state.summary)
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        AnalysisGradientButton(
            text = "\uD648\uC73C\uB85C",
            onClick = onPrimaryAction,
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
private fun ChallengeFinalAnalysisTopBar(
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
                contentDescription = "\uB4A4\uB85C \uAC00\uAE30",
                tint = Color.White
            )
        }

        Text(
            text = "\uCC4C\uB9B0\uC9C0 \uBD84\uC11D \uACB0\uACFC",
            modifier = Modifier.align(Alignment.Center),
            color = AnalysisText,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ChallengePreviewHero(
    state: ChallengePreviewHeroState,
    modifier: Modifier = Modifier
) {
    val holdChipBackground = holdColorToUiColor(state.holdColorLabel)
    val holdChipIsBright =
        (holdChipBackground.red + holdChipBackground.green + holdChipBackground.blue) / 3f > 0.7f

    Column(modifier = modifier) {
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
                    text = state.gymName.ifBlank { "\uD074\uB77C\uC774\uBC0D \uCC4C\uB9B0\uC9C0" },
                    color = AnalysisText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = state.displayDate,
                    color = AnalysisMuted,
                    fontSize = 13.sp
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (state.difficultyLabel.isNotBlank()) {
                        HeaderChip(
                            text = state.difficultyLabel,
                            background = Color.White,
                            contentColor = Color.Black
                        )
                    }
                    if (state.holdColorLabel.isNotBlank()) {
                        HeaderChip(
                            text = state.holdColorLabel,
                            background = holdChipBackground,
                            contentColor = if (holdChipIsBright) Color.Black else Color.White
                        )
                    }
                    HeaderChip(
                        text = "\uCD1D ${state.attemptCount}\uD68C \uC2DC\uB3C4",
                        background = Color(0xFF2B3138),
                        contentColor = Color.White
                    )
                    HeaderChip(
                        text = if (state.overallSuccess) {
                            "\uC644\uB4F1 \uC131\uACF5"
                        } else {
                            "\uC644\uB4F1 \uBBF8\uB2EC"
                        },
                        background = if (state.overallSuccess) {
                            AnalysisSuccess.copy(alpha = 0.24f)
                        } else {
                            AnalysisFailure.copy(alpha = 0.2f)
                        },
                        contentColor = Color.White
                    )
                }
            }

            HoldOverviewPreview(
                bitmap = state.previewBitmap,
                holds = state.previewHolds,
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
                bitmap = state.previewBitmap,
                holds = state.previewHolds,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(242.dp)
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "\uCC4C\uB9B0\uC9C0 \uC885\uD569 \uBD84\uC11D",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (state.overallSuccess) {
                        "\uCD1D ${state.attemptCount}\uD68C \uC2DC\uB3C4 \u00B7 ${state.successAttemptCount}\uD68C \uC644\uB4F1"
                    } else {
                        "\uCD1D ${state.attemptCount}\uD68C \uC2DC\uB3C4 \u00B7 \uC644\uB4F1 \uBBF8\uB2EC"
                    },
                    color = Color.White.copy(alpha = 0.86f),
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun ChallengeSummaryCard(
    summary: ChallengeFinalAnalysisSummary,
    modifier: Modifier = Modifier
) {
    val chipLabels = buildList {
        summary.repeatedCruxHoldLabel?.let { add("\uBC18\uBCF5 \uB09C\uAD6C\uAC04 $it") }
        addAll(summary.repeatedPatternLabels)
        summary.repeatedLoadFocusLabel?.let { add("$it \uBD80\uB2F4") }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AnalysisPanelColor)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "\uBB38\uC81C \uC694\uC57D",
                color = AnalysisMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            if (chipLabels.isNotEmpty()) {
                SummaryChipRow(labels = chipLabels)
            }

            Text(
                text = summary.summaryLine,
                color = AnalysisText,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = summary.completionLine,
                color = AnalysisMuted,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
            Text(
                text = summary.challengeNatureLine,
                color = AnalysisMuted,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun ChallengeOverviewTab(
    summary: ChallengeFinalAnalysisSummary,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        ChallengeMetricOverviewPanel(summary = summary)

        ChallengeAttemptTrendPanel(
            trendPoints = summary.trendPoints
        )

        Spacer(modifier = Modifier.height(16.dp))

        AnalysisInsightCard(
            title = "\uC2DC\uB3C4 \uD750\uB984",
            highlights = summary.trendHighlights,
            modifier = Modifier.padding(horizontal = 22.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        AnalysisInsightCard(
            title = "\uBB38\uC81C \uD574\uC124",
            highlights = listOf(summary.completionLine, summary.challengeNatureLine),
            modifier = Modifier.padding(horizontal = 22.dp)
        )
    }
}

@Composable
private fun ChallengeComparisonTab(
    summary: ChallengeFinalAnalysisSummary,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "\uC2DC\uB3C4\uBCC4 \uBE44\uAD50",
            color = AnalysisText,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )

        summary.attempts.forEach { attempt ->
            AttemptComparisonCard(item = attempt)
        }
    }
}

@Composable
private fun ChallengePatternTab(
    summary: ChallengeFinalAnalysisSummary,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ChallengeCruxDistributionPanel(
            distribution = summary.cruxDistribution,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        PatternKeyCard(
            title = "\uBC18\uBCF5\uB41C \uB09C\uAD6C\uAC04",
            caption = "\uAC00\uC7A5 \uC790\uC8FC \uBC84\uAC70\uC6E0\uB358 \uAD6C\uAC04",
            value = summary.repeatedCruxHoldLabel ?: FinalAnalysisUnknownMetricText,
            modifier = Modifier.padding(horizontal = 22.dp)
        )
        PatternKeyCard(
            title = "\uBC18\uBCF5\uB41C \uC6D0\uC778",
            caption = "\uC2DC\uB3C4 \uC804\uBC18\uC5D0\uC11C \uC790\uC8FC \uBCF4\uC778 \uC2E0\uD638",
            value = summary.repeatedPatternLabels
                .takeIf { it.isNotEmpty() }
                ?.joinToString(", ")
                ?: FinalAnalysisUnknownMetricText,
            modifier = Modifier.padding(horizontal = 22.dp)
        )
        PatternKeyCard(
            title = "\uBD80\uB2F4 \uC9D1\uC911 \uBD80\uC704",
            caption = "\uD798\uC774 \uC790\uC8FC \uB9CE\uC774 \uB4E4\uC5B4\uAC04 \uBD80\uC704",
            value = summary.repeatedLoadFocusLabel ?: FinalAnalysisUnknownMetricText,
            modifier = Modifier.padding(horizontal = 22.dp)
        )

        AnalysisInsightCard(
            title = "\uBC18\uBCF5\uB41C \uD328\uD134 \uD574\uC124",
            highlights = summary.patternHighlights,
            modifier = Modifier.padding(horizontal = 22.dp)
        )
    }
}

@Composable
private fun AttemptComparisonCard(
    item: ChallengeAttemptComparisonItem,
    modifier: Modifier = Modifier
) {
    val tagLabels = buildList {
        addAll(item.tagLabels)
        item.loadFocusLabel?.let { add("$it \uBD80\uB2F4") }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AnalysisPanelColor)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${item.attemptNo}\uBC88\uC9F8 \uC2DC\uB3C4",
                    color = AnalysisText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                StatusChip(
                    text = if (item.isSuccess) "\uC131\uACF5" else "\uC2E4\uD328",
                    background = if (item.isSuccess) {
                        AnalysisSuccess.copy(alpha = 0.22f)
                    } else {
                        AnalysisFailure.copy(alpha = 0.2f)
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AttemptMetricItem(
                    title = "\uB3C4\uB2EC \uD640\uB4DC",
                    value = formatAttemptReachedMetric(item),
                    modifier = Modifier.weight(1f)
                )
                AttemptMetricItem(
                    title = "\uADE0\uD615 \uC720\uC9C0",
                    value = item.insideSupportRatioText,
                    modifier = Modifier.weight(1f)
                )
                AttemptMetricItem(
                    title = "\uC190\uBC1C \uC9C0\uC9C0",
                    value = item.stableContactRatioText,
                    modifier = Modifier.weight(1f)
                )
            }

            if (tagLabels.isNotEmpty()) {
                SummaryChipRow(labels = tagLabels)
            }

            Text(
                text = item.summaryLine,
                color = AnalysisMuted,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun PatternKeyCard(
    title: String,
    caption: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AnalysisPanelColor)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                color = AnalysisText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = caption,
                color = AnalysisMuted,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            Text(
                text = value,
                color = AnalysisText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AttemptMetricItem(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            color = AnalysisMuted,
            fontSize = 12.sp
        )
        Text(
            text = value,
            color = AnalysisText,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun StatusChip(
    text: String,
    background: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SummaryChipRow(
    labels: List<String>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        labels.forEach { label ->
            SummaryChip(text = label)
        }
    }
}

@Composable
private fun SummaryChip(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = AnalysisText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatAttemptReachedMetric(
    item: ChallengeAttemptComparisonItem
): String {
    return if (item.reachedHoldsText == FinalAnalysisUnknownMetricText) {
        item.reachedHoldsText
    } else {
        item.reachedHoldsText + item.reachedHoldsSuffix.orEmpty()
    }
}
