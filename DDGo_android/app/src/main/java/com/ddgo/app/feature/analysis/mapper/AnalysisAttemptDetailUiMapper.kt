package com.ddgo.app.feature.analysis.mapper

import com.ddgo.app.domain.model.AnalysisAttemptSnapshot
import com.ddgo.app.domain.model.AnalysisChallengeResult
import com.ddgo.app.domain.model.AnalysisChallengeSnapshot
import com.ddgo.app.feature.analysis.AnalysisStrings
import com.ddgo.app.feature.analysis.model.AnalysisAttemptDetailUiModel
import com.ddgo.app.feature.analysis.model.AnalysisAttemptStabilityGraphUiModel
import com.ddgo.app.feature.analysis.model.AnalysisBadgeTone
import com.ddgo.app.feature.analysis.model.AnalysisBadgeUiModel
import com.ddgo.app.feature.analysis.model.AnalysisCoachCardUiModel
import com.ddgo.app.feature.analysis.model.AnalysisOverviewStatUiModel
import com.ddgo.app.feature.analysis.model.AnalysisTimelineItemUiModel
import com.ddgo.app.feature.climbing.record.presentation.HeartRatePoint
import kotlin.math.abs
import kotlin.math.roundToInt

internal object AnalysisAttemptDetailUiMapper {

    fun build(
        challenge: AnalysisChallengeSnapshot,
        attempt: AnalysisAttemptSnapshot
    ): AnalysisAttemptDetailUiModel {
        val maxHoldInChallenge = challenge.attempts.maxOfOrNull { it.maxHoldNo }?.coerceAtLeast(1) ?: 1

        return AnalysisAttemptDetailUiModel(
            title = "${attempt.attemptNo}차 시도",
            subtitle = "${challenge.gymName} | ${AnalysisFormatters.challengeSubtitle(challenge)}",
            resultBadge = AnalysisBadgeUiModel(
                label = AnalysisFormatters.resultLabel(attempt.attemptResult),
                tone = AnalysisFormatters.resultTone(attempt.attemptResult)
            ),
            headline = headlineFor(attempt),
            videoUrl = attempt.videoUrl,
            overallMovementScore = attempt.overallMovementScore,
            attemptResultLabel = AnalysisFormatters.resultLabel(attempt.attemptResult),
            reachedHoldLabel = "${attempt.maxHoldNo}번",
            reachedHoldSuffix = "/${maxHoldInChallenge}번",
            cruxHoldLabel = attempt.cruxHoldNo?.let { "${it}번 홀드" } ?: "-",
            stabilityScore = attempt.centerStabilityRatio.coerceIn(0f, 1f),
            recoveryScore = ((attempt.stabilityRecoveryScore ?: 0) / 100f).coerceIn(0f, 1f),
            lowerBodyDriveScore = ((attempt.lowerBodyDriveScore ?: 0) / 100f).coerceIn(0f, 1f),
            stableContactScore = (attempt.stableContactRatio ?: 0f).coerceIn(0f, 1f),
            stabilityValueLabel = AnalysisFormatters.formatPercent(attempt.centerStabilityRatio),
            recoveryValueLabel = attempt.stabilityRecoveryScore?.let { "${it}점" } ?: "-",
            lowerBodyDriveValueLabel = attempt.lowerBodyDriveScore?.let { "${it}점" } ?: "-",
            stableContactValueLabel = attempt.stableContactRatio?.let { AnalysisFormatters.formatPercent(it) } ?: "-",
            loadFocusLabel = attempt.loadFocusLabel ?: "-",
            metricCards = listOf(
                AnalysisOverviewStatUiModel(
                    label = AnalysisStrings.AttemptDurationLabel,
                    value = AnalysisFormatters.formatDuration(attempt.durationMs)
                ),
                AnalysisOverviewStatUiModel(
                    label = "종합 점수",
                    value = attempt.overallMovementScore?.let { "${it}점" } ?: "-"
                ),
                AnalysisOverviewStatUiModel(
                    label = "안정 접촉 비율",
                    value = attempt.stableContactRatio?.let { AnalysisFormatters.formatPercent(it) } ?: "-"
                ),
                AnalysisOverviewStatUiModel(
                    label = "부담 집중 부위",
                    value = attempt.loadFocusLabel ?: "-"
                )
            ),
            stabilityGraph = buildStabilityGraph(attempt),
            timelineItems = buildTimelineItems(attempt),
            coachCards = buildCoachCards(attempt)
        )
    }

    private fun buildStabilityGraph(
        attempt: AnalysisAttemptSnapshot
    ): AnalysisAttemptStabilityGraphUiModel? {
        val insight = attempt.insight ?: return null
        val stabilityTimeline = insight.stabilityTimeline.takeIf { it.size >= 2 } ?: return null
        val durationMs = insight.videoDurationMs
            ?.takeIf { it > 0L }
            ?: attempt.durationMs.takeIf { it > 0L }
            ?: 30_000L
        val lowestPointFraction = stabilityTimeline.lowestPointFraction()
        val cruxRange = buildCruxRange(
            durationMs = durationMs,
            focusFraction = insight.stabilityFocusFraction,
            cruxDurationMs = attempt.cruxDurationMs?.toInt()
        )

        return AnalysisAttemptStabilityGraphUiModel(
            stabilityTimeline = stabilityTimeline,
            durationMs = durationMs,
            dangerFractions = buildDangerFractions(
                stabilityTimeline = stabilityTimeline,
                dangerEventCount = attempt.dangerEventCount
            ),
            cruxStartFraction = cruxRange?.first,
            cruxEndFraction = cruxRange?.second,
            failureFraction = if (attempt.attemptResult == AnalysisChallengeResult.FAIL) {
                lowestPointFraction
            } else {
                null
            },
            heartRateSeries = insight.heartRateSeries.map { point ->
                HeartRatePoint(
                    timestampMs = point.timestampMs,
                    bpm = point.bpm
                )
            }
        )
    }

    private fun headlineFor(attempt: AnalysisAttemptSnapshot): String {
        return when (attempt.attemptResult) {
            AnalysisChallengeResult.SUCCESS -> "완등까지 이어진 움직임 품질과 핵심 지표를 한 번에 다시 확인할 수 있어요."
            AnalysisChallengeResult.FAIL -> "멈춘 구간과 부족했던 움직임 패턴을 업로드 분석 지표 기준으로 다시 볼 수 있어요."
            AnalysisChallengeResult.UNKNOWN -> "시도 전체 흐름과 핵심 지표를 다시 보며 기준점을 잡을 수 있어요."
        }
    }

    private fun buildTimelineItems(attempt: AnalysisAttemptSnapshot): List<AnalysisTimelineItemUiModel> {
        val retention = (attempt.centerStabilityRatio * 100f).roundToInt()
        val recovery = attempt.stabilityRecoveryScore
        val drive = attempt.lowerBodyDriveScore

        return listOf(
            AnalysisTimelineItemUiModel(
                title = "안정성 유지",
                description = when {
                    retention >= 75 -> "시도 전반에서 중심이 비교적 안정적으로 유지되며 큰 흔들림이 적었어요."
                    retention >= 60 -> "전반 흐름은 유지됐지만 동작 전환 구간에서 중심 보정이 몇 번 필요했어요."
                    else -> "중심이 자주 흔들려 다음 동작으로 넘어가기 전에 자세 보정이 많이 필요했어요."
                },
                tone = if (retention >= 70) AnalysisBadgeTone.Accent else AnalysisBadgeTone.Warning
            ),
            AnalysisTimelineItemUiModel(
                title = "안정성 회복력",
                description = when {
                    recovery == null -> "흔들린 뒤 회복 흐름을 충분히 읽을 만큼의 구간 데이터가 부족했어요."
                    recovery >= 70 -> "흔들린 뒤에도 다시 자세를 회복해 흐름을 이어가는 장면이 비교적 또렷했어요."
                    recovery >= 55 -> "회복은 했지만 다시 안정권으로 돌아오는 데 시간이 조금 걸렸어요."
                    else -> "흔들린 뒤 자세를 다시 세우는 데 시간이 길어 다음 동작 연결이 끊겼어요."
                },
                tone = when {
                    recovery == null -> AnalysisBadgeTone.Neutral
                    recovery >= 65 -> AnalysisBadgeTone.Success
                    else -> AnalysisBadgeTone.Warning
                }
            ),
            AnalysisTimelineItemUiModel(
                title = "하체 주도성",
                description = when {
                    drive == null -> "하체 주도성을 충분히 계산할 수 있는 보조 데이터가 아직 없었어요."
                    drive >= 70 -> "다리와 골반이 먼저 중심을 만들고 팔은 연결해 주는 흐름이 비교적 분명했어요."
                    drive >= 55 -> "하체 사용은 있었지만 구간에 따라 팔 의존이 함께 나타난 시도였어요."
                    else -> "팔이 먼저 버티고 하체 연결이 늦는 장면이 자주 나타났어요."
                },
                tone = when {
                    drive == null -> AnalysisBadgeTone.Neutral
                    drive >= 65 -> AnalysisBadgeTone.Success
                    else -> AnalysisBadgeTone.Warning
                }
            ),
            AnalysisTimelineItemUiModel(
                title = "부담 집중 부위",
                description = attempt.loadFocusLabel
                    ?.takeIf { it.isNotBlank() }
                    ?.let { "${it}에 상대적으로 부담이 더 실린 시도로 읽혔어요." }
                    ?: "특정 부위에만 부담이 뚜렷하게 몰린 장면은 제한적이었어요.",
                tone = when {
                    attempt.loadFocusLabel.isNullOrBlank() -> AnalysisBadgeTone.Neutral
                    attempt.loadFocusLabel.contains("팔") || attempt.loadFocusLabel.contains("손") -> AnalysisBadgeTone.Danger
                    else -> AnalysisBadgeTone.Accent
                }
            )
        )
    }

    private fun buildCoachCards(attempt: AnalysisAttemptSnapshot): List<AnalysisCoachCardUiModel> {
        return listOf(
            AnalysisCoachCardUiModel(
                title = AnalysisStrings.CoachFailureTitle,
                body = attempt.failureReason ?: AnalysisStrings.EmptyCoachMessage,
                tone = AnalysisBadgeTone.Warning
            ),
            AnalysisCoachCardUiModel(
                title = AnalysisStrings.CoachRiskTitle,
                body = formatRiskBody(attempt.riskAlert) ?: AnalysisStrings.EmptyCoachMessage,
                tone = AnalysisBadgeTone.Danger
            ),
            AnalysisCoachCardUiModel(
                title = AnalysisStrings.CoachMissionTitle,
                body = attempt.nextMission ?: AnalysisStrings.EmptyCoachMessage,
                tone = AnalysisBadgeTone.Accent
            )
        )
    }

    private fun formatRiskBody(raw: String?): String? {
        val text = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val hold = extractHoldLabel(text)
        val loadFocus = extractRiskField(text, "부담 집중 부위")
        val rootCause = extractRiskField(text, "핵심 원인")
        val dominantMotion = extractRiskField(text, "주요 동작")

        val subject = when {
            hold != null && loadFocus != null -> "${hold}에서 ${loadFocus} 부담이 커졌어요"
            loadFocus != null -> "${loadFocus} 부담이 커졌어요"
            hold != null -> "${hold}에서 리스크가 커졌어요"
            else -> "리스크가 커진 구간이 있었어요"
        }

        val detail = when {
            rootCause != null && dominantMotion != null ->
                "${rootCause} 때문에 ${dominantMotion} 동작이 두드러졌어요"
            rootCause != null ->
                "${rootCause} 때문에 흐름이 흔들렸어요"
            dominantMotion != null ->
                "${dominantMotion} 동작이 두드러졌어요"
            else -> null
        }

        return listOfNotNull(subject, detail)
            .joinToString(". ")
            .let { if (it.endsWith(".")) it else "$it." }
    }

    private fun extractHoldLabel(raw: String): String? =
        Regex("""\d+번 홀드""").find(raw)?.value

    private fun extractRiskField(raw: String, label: String): String? {
        val markers = listOf(
            "부담 집중 부위:",
            "핵심 원인:",
            "주요 사용 부위:",
            "주요 동작:",
            "균형 이탈",
            "안정 접촉 비율"
        )
        val marker = "$label:"
        val start = raw.indexOf(marker)
        if (start < 0) return null

        val valueStart = start + marker.length
        val nextIndex = markers
            .mapNotNull { nextMarker ->
                raw.indexOf(nextMarker, valueStart)
                    .takeIf { it >= 0 }
            }
            .filter { it > start }
            .minOrNull()
            ?: raw.length

        return raw
            .substring(valueStart, nextIndex)
            .trim()
            .trim(',', '.', '·')
            .takeIf { it.isNotBlank() }
    }
    private fun buildDangerFractions(
        stabilityTimeline: List<Float>,
        dangerEventCount: Int
    ): List<Float> {
        val desiredCount = dangerEventCount.coerceIn(0, 2)
        if (desiredCount == 0 || stabilityTimeline.size < 3) return emptyList()

        val candidates = buildList {
            for (index in 1 until stabilityTimeline.lastIndex) {
                val current = stabilityTimeline[index]
                val previous = stabilityTimeline[index - 1]
                val next = stabilityTimeline[index + 1]
                val localDrop = ((previous + next) / 2f - current).coerceAtLeast(0f)

                if (current <= 0.42f && current <= previous && current <= next && localDrop >= 0.03f) {
                    add(index to current)
                }
            }
        }.sortedBy { it.second }

        val selected = mutableListOf<Float>()
        candidates.forEach { (index, _) ->
            val fraction = index.toFloat() / stabilityTimeline.lastIndex.toFloat()
            if (selected.none { abs(it - fraction) < 0.12f }) {
                selected += fraction
            }
            if (selected.size >= desiredCount) return@forEach
        }

        if (selected.isNotEmpty()) {
            return selected
        }

        val globalMin = stabilityTimeline.minOrNull() ?: return emptyList()
        return if (globalMin <= 0.35f) {
            listOf(stabilityTimeline.indexOf(globalMin).toFloat() / stabilityTimeline.lastIndex.toFloat())
        } else {
            emptyList()
        }
    }

    private fun buildCruxRange(
        durationMs: Long,
        focusFraction: Float?,
        cruxDurationMs: Int?
    ): Pair<Float, Float>? {
        val focus = focusFraction ?: return null
        val durationFraction = ((cruxDurationMs ?: 4_000).toFloat() / durationMs.toFloat())
            .coerceIn(0.08f, 0.28f)
        val start = (focus - durationFraction / 2f).coerceAtLeast(0f)
        val end = (focus + durationFraction / 2f).coerceAtMost(1f)
        return start to end
    }

    private fun List<Float>.lowestPointFraction(): Float? {
        if (size < 2) return null
        val lowestIndex = indices.minByOrNull { this[it] } ?: return null
        return lowestIndex.toFloat() / lastIndex.toFloat()
    }
}
