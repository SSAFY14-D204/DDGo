package com.ddgo.app.feature.community.components

import com.ddgo.app.data.remote.common.RemoteDateTimeParser
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CommunityFeedTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN)

private val CommunityFeedDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("M/d", Locale.KOREAN)

internal fun formatCommunityFeedTimestamp(
    createdAt: String,
    today: LocalDate = LocalDate.now()
): String {
    val rawValue = createdAt.trim()
    if (rawValue.isEmpty()) {
        return "-"
    }

    val parsedDateTime = RemoteDateTimeParser.parse(rawValue) ?: return rawValue
    return if (parsedDateTime.toLocalDate() == today) {
        parsedDateTime.format(CommunityFeedTimeFormatter)
    } else {
        parsedDateTime.format(CommunityFeedDateFormatter)
    }
}
