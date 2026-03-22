package com.ddgo.app.feature.climbing.upload

internal const val MIN_LOADING_DISPLAY_MILLIS = 1_500L

internal fun remainingLoadingDisplayMillis(
    startedAtMillis: Long,
    nowMillis: Long,
    minimumDisplayMillis: Long = MIN_LOADING_DISPLAY_MILLIS
): Long {
    val elapsedMillis = nowMillis - startedAtMillis
    return (minimumDisplayMillis - elapsedMillis).coerceAtLeast(0L)
}

internal fun toAnalysisLoadingMessage(message: String?): String {
    val trimmed = message?.trim().orEmpty()
    if (trimmed.isBlank()) {
        return "디디고가 자세를 분석하고 있어요"
    }

    return if (trimmed.contains("영상 업로드")) {
        "AI 분석 결과를 정리하고 있어요"
    } else {
        trimmed
    }
}
