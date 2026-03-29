package com.ddgo.app.domain.model

enum class AiAnalysisMode(
    val pathSegment: String
) {
    FAST(pathSegment = "fast"),
    PHYSICS(pathSegment = "physics");

    val apiValue: String
        get() = pathSegment

    companion object {
        fun fromApiValue(value: String?): AiAnalysisMode {
            return entries.firstOrNull { mode ->
                mode.pathSegment.equals(value, ignoreCase = true) ||
                    mode.name.equals(value, ignoreCase = true)
            } ?: FAST
        }
    }
}
