package com.ddgo.app.data.repository

import com.ddgo.app.data.remote.challenge.ChallengeApi
import com.ddgo.app.data.remote.challenge.ChallengeListResponseDto
import com.ddgo.app.data.remote.common.GymNameFormatter
import com.ddgo.app.data.remote.common.RemoteDateTimeParser
import com.ddgo.app.domain.model.CalendarEntry
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
        val startedAt = RemoteDateTimeParser.parse(startedAt ?: createdAt) ?: return null
        if (YearMonth.from(startedAt) != yearMonth) {
            return null
        }

        return CalendarEntry(
            id = id,
            date = startedAt.toLocalDate(),
            title = buildTitle(),
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
            "SUCCESS" -> "\uC644\uB4F1"
            "FAIL" -> "\uBBF8\uC644\uB4F1"
            else -> if (challengeStatus.equals("ACTIVE", ignoreCase = true)) {
                "\uC9C4\uD589 \uC911"
            } else {
                "\uACB0\uACFC \uB300\uAE30"
            }
        }
    }
}
