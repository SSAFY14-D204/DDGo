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

/**
 * 분석 화면 전반에서 재사용하는 포맷팅 규칙 모음입니다.
 *
 * 역할:
 * - 날짜, 시간, 퍼센트, 결과 라벨처럼 반복되는 표시 규칙을 한 곳에 모읍니다.
 * - 각 mapper가 같은 표현 규칙을 공유하도록 해 화면마다 말투가 달라지는 문제를 줄입니다.
 */
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
