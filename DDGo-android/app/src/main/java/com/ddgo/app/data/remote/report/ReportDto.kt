package com.ddgo.app.data.remote.report

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 리포트 응답 DTO */
@Serializable
data class ReportResponseDto(
    @SerialName("climb_id")         val climbId: String,
    @SerialName("video_url")        val videoUrl: String,
    @SerialName("wall_grade")       val wallGrade: String,
    @SerialName("success")          val success: Boolean,
    @SerialName("fail_time_ms")     val failTimeMs: Long? = null,
    @SerialName("hold_count")       val holdCount: Int = 0,
    @SerialName("created_at")       val createdAt: String
)
