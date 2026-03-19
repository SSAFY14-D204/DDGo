package com.ddgo.app.data.repository

import com.ddgo.app.BuildConfig
import com.ddgo.app.data.remote.challenge.ChallengeApi
import com.ddgo.app.data.remote.challenge.ChallengeListItemDto
import com.ddgo.app.domain.model.CalendarChallengeResult
import com.ddgo.app.domain.model.CalendarEntry
import com.ddgo.app.domain.repository.CalendarRepository
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import javax.inject.Inject

class CalendarRepositoryImpl @Inject constructor(
    private val challengeApi: ChallengeApi
) : CalendarRepository {

    override suspend fun getCalendarEntries(): Result<List<CalendarEntry>> {
        return try {
            val response = challengeApi.getChallenges()
            if (!response.success) {
                Result.failure(Exception(response.message.ifBlank { "Failed to load calendar entries." }))
            } else {
                val entries = response.data.orEmpty()
                    .mapNotNull(ChallengeListItemDto::toDomainOrNull)
                    .sortedByDescending { it.startedAt ?: it.createdAt }
                Result.success(entries)
            }
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }
}

private fun ChallengeListItemDto.toDomainOrNull(): CalendarEntry? {
    val createdAtDateTime = createdAt.toLocalDateTimeOrNull() ?: return null
    val startedAtDateTime = startedAt.toLocalDateTimeOrNull()
    val endedAtDateTime = endedAt.toLocalDateTimeOrNull()
    val effectiveDateTime = startedAtDateTime ?: createdAtDateTime

    return CalendarEntry(
        id = id,
        date = effectiveDateTime.toLocalDate(),
        startedAt = startedAtDateTime,
        endedAt = endedAtDateTime,
        createdAt = createdAtDateTime,
        gymId = gymId,
        gymGradeId = gymGradeId,
        gymName = gymName.orEmpty(),
        problemColor = problemColor.orEmpty(),
        difficultyLabel = gradeLabel.orEmpty(),
        difficultyColorHex = normalizeDifficultyHex(colorHex, problemColor),
        challengeStatus = challengeStatus,
        challengeResult = CalendarChallengeResult.from(challengeResult),
        gymLogoUrl = buildPublicAssetUrl(gymLogoBucket, gymLogoObjectKey),
        brandLogoUrl = buildPublicAssetUrl(brandLogoBucket, brandLogoObjectKey)
    )
}

private fun String?.toLocalDateTimeOrNull(): LocalDateTime? {
    return this?.takeIf { it.isNotBlank() }?.let { value ->
        runCatching { LocalDateTime.parse(value) }.getOrNull()
    }
}

private fun normalizeDifficultyHex(colorHex: String?, problemColor: String?): String {
    val normalizedHex = colorHex
        ?.trim()
        ?.removePrefix("#")
        ?.takeIf { it.length == 6 }
        ?.uppercase()

    if (normalizedHex != null) {
        return "#$normalizedHex"
    }

    return when (problemColor?.trim()?.lowercase()) {
        "pink", "핑크" -> "#FF56A8"
        "purple", "보라", "보라색" -> "#876FFF"
        "yellow", "노랑", "노란색" -> "#FED500"
        "green", "초록", "초록색" -> "#65B969"
        "orange", "주황", "주황색" -> "#FF7700"
        "blue", "navy", "파랑", "남색", "파란색" -> "#373FD7"
        "skyblue", "sky blue", "하늘", "하늘색" -> "#4396FB"
        "red", "빨강", "빨간색" -> "#FF1208"
        "gray", "grey", "회색" -> "#9FA3AA"
        "black", "검정", "검은색" -> "#2B2B2E"
        "white", "흰색", "하양" -> "#E5E7EB"
        else -> "#9FA3AA"
    }
}

private fun buildPublicAssetUrl(bucket: String?, objectKey: String?): String? {
    if (bucket.isNullOrBlank() || objectKey.isNullOrBlank()) return null

    val encodedObjectKey = objectKey
        .split("/")
        .joinToString("/") { segment ->
            URLEncoder.encode(segment, StandardCharsets.UTF_8.name())
                .replace("+", "%20")
        }

    return "${buildPublicAssetBaseUrl()}/minio/$bucket/$encodedObjectKey"
}

private fun buildPublicAssetBaseUrl(): String {
    val trimmed = BuildConfig.BASE_URL.trimEnd('/')
    return if (trimmed.endsWith("/api")) {
        trimmed.removeSuffix("/api")
    } else {
        trimmed
    }
}
