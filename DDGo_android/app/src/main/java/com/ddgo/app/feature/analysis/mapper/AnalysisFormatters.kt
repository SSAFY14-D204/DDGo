package com.ddgo.app.feature.analysis.mapper

import com.ddgo.app.domain.model.AnalysisChallengeResult
import com.ddgo.app.domain.model.AnalysisChallengeSnapshot
import com.ddgo.app.domain.model.AnalysisChallengeStatus
import com.ddgo.app.feature.analysis.AnalysisStrings
import com.ddgo.app.feature.analysis.model.AnalysisBadgeTone
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

internal object AnalysisFormatters {

    private val shortDateFormatter = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREA)
    private val fullDateFormatter = DateTimeFormatter.ofPattern("M월 d일 HH:mm", Locale.KOREA)

    fun formatDate(dateTime: LocalDateTime): String = dateTime.format(shortDateFormatter)

    fun formatFullDate(dateTime: LocalDateTime): String = dateTime.format(fullDateFormatter)

    fun formatDuration(milliseconds: Long): String {
        val totalSeconds = (milliseconds / 1_000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return if (minutes > 0L) {
            "${minutes}분 ${seconds}초"
        } else {
            "${seconds}초"
        }
    }

    fun formatPercent(value: Float): String {
        return "${(value.coerceIn(0f, 1f) * 100f).roundToInt()}%"
    }

    fun formatEventCount(value: Int): String {
        return "${value}회"
    }

    fun formatAverageEventCount(value: Float): String {
        val rounded = ((value * 10f).roundToInt() / 10f)
        return if (rounded % 1f == 0f) {
            "${rounded.toInt()}회"
        } else {
            String.format(Locale.US, "%.1f회", rounded)
        }
    }

    fun resultLabel(result: AnalysisChallengeResult): String {
        return when (result) {
            AnalysisChallengeResult.SUCCESS -> AnalysisStrings.ResultSuccess
            AnalysisChallengeResult.FAIL -> AnalysisStrings.ResultFail
            AnalysisChallengeResult.UNKNOWN -> AnalysisStrings.ResultUnknown
        }
    }

    fun resultTone(result: AnalysisChallengeResult): AnalysisBadgeTone {
        return when (result) {
            AnalysisChallengeResult.SUCCESS -> AnalysisBadgeTone.Success
            AnalysisChallengeResult.FAIL -> AnalysisBadgeTone.Danger
            AnalysisChallengeResult.UNKNOWN -> AnalysisBadgeTone.Neutral
        }
    }

    fun statusLabel(status: AnalysisChallengeStatus): String {
        return when (status) {
            AnalysisChallengeStatus.CLOSED -> AnalysisStrings.StatusClosed
            AnalysisChallengeStatus.ACTIVE -> AnalysisStrings.StatusActive
        }
    }

    fun statusTone(status: AnalysisChallengeStatus): AnalysisBadgeTone {
        return when (status) {
            AnalysisChallengeStatus.CLOSED -> AnalysisBadgeTone.Neutral
            AnalysisChallengeStatus.ACTIVE -> AnalysisBadgeTone.Warning
        }
    }

    fun challengeSubtitle(challenge: AnalysisChallengeSnapshot): String {
        return buildString {
            append(challenge.problemColor)
            challenge.gradeLabel?.takeIf { it.isNotBlank() }?.let {
                append(" / ")
                append(it)
            }
        }
    }
}
