package com.ddgo.app.domain.usecase

data class StallSegmentAnnotation(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val durationMs: Long,
    val score: Float
)
