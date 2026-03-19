package com.ddgo.app.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

data class CalendarEntry(
    val id: Long,
    val date: LocalDate,
    val startedAt: LocalDateTime?,
    val endedAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    val gymId: Long?,
    val gymGradeId: Long?,
    val gymName: String,
    val problemColor: String,
    val difficultyLabel: String,
    val difficultyColorHex: String,
    val challengeStatus: String,
    val challengeResult: CalendarChallengeResult,
    val gymLogoUrl: String?,
    val brandLogoUrl: String?
)

enum class CalendarChallengeResult {
    SUCCESS,
    FAIL,
    UNKNOWN;

    companion object {
        fun from(raw: String?): CalendarChallengeResult {
            return entries.firstOrNull { it.name == raw } ?: UNKNOWN
        }
    }
}

data class CalendarMonthSummary(
    val activeDays: Int = 0,
    val totalSessions: Int = 0,
    val longestStreak: Int = 0
)
