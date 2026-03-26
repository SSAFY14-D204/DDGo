package com.ddgo.app.data.repository

import com.ddgo.app.data.remote.challenge.ChallengeApi
import com.ddgo.app.data.remote.challenge.ChallengeListResponseDto
import com.ddgo.app.data.remote.common.GymNameFormatter
import com.ddgo.app.data.remote.common.RemoteDateTimeParser
import com.ddgo.app.domain.model.CalendarEntry
import com.ddgo.app.domain.model.CalendarEntryResult
import com.ddgo.app.domain.repository.CalendarRepository
import java.time.YearMonth
import javax.inject.Inject

class CalendarRepositoryImpl @Inject constructor(
    private val challengeApi: ChallengeApi
) : CalendarRepository {

    override suspend fun getCalendarEntries(yearMonth: YearMonth): Result<List<CalendarEntry>> = runCatching {
        val response = challengeApi.getChallenges()
        val challenges = response.data.takeIf { response.success }
            ?: throw IllegalStateException(response.message.ifBlank { "Failed to load calendar entries." })

        challenges.mapNotNull { challenge ->
            challenge.toCalendarEntry(yearMonth)
        }.sortedWith(
            compareByDescending<CalendarEntry> { it.date }
                .thenByDescending { it.time }
        )
    }

    private fun ChallengeListResponseDto.toCalendarEntry(yearMonth: YearMonth): CalendarEntry? {
        if (doneAttemptCount <= 0) {
            return null
        }
        val startedAt = RemoteDateTimeParser.parse(startedAt ?: createdAt) ?: return null
        if (YearMonth.from(startedAt) != yearMonth) {
            return null
        }

        return CalendarEntry(
            id = id,
            date = startedAt.toLocalDate(),
            title = buildTitle(),
            problemColor = problemColor,
            result = toCalendarEntryResult(),
            venue = GymNameFormatter.sanitize(gymName),
            time = startedAt.toLocalTime(),
            note = buildNote()
        )
    }

    private fun ChallengeListResponseDto.buildTitle(): String {
        val grade = gradeLabel?.takeIf { it.isNotBlank() }
        return if (grade == null) {
            problemColor
        } else {
            "$problemColor / $grade"
        }
    }

    private fun ChallengeListResponseDto.buildNote(): String {
        return when (challengeResult?.uppercase()) {
            "SUCCESS" -> "완등"
            "FAIL" -> "미완등"
            else -> if (challengeStatus.equals("ACTIVE", ignoreCase = true)) {
                "진행 중"
            } else {
                "결과 대기"
            }
        }
    }

    private fun ChallengeListResponseDto.toCalendarEntryResult(): CalendarEntryResult {
        return when (challengeResult?.uppercase()) {
            "SUCCESS" -> CalendarEntryResult.SUCCESS
            "FAIL" -> CalendarEntryResult.FAIL
            "PENDING" -> CalendarEntryResult.PENDING
            else -> when {
                challengeStatus.equals("ACTIVE", ignoreCase = true) -> CalendarEntryResult.ACTIVE
                challengeStatus.equals("CLOSED", ignoreCase = true) -> CalendarEntryResult.PENDING
                else -> CalendarEntryResult.UNKNOWN
            }
        }
    }
}
