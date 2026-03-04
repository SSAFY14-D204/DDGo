package com.ddgo.app.data.remote.upload

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 업로드 응답 DTO */
@Serializable
data class UploadResponseDto(
    @SerialName("climb_id")     val climbId: String,
    @SerialName("video_url")    val videoUrl: String,
    @SerialName("uploaded_at")  val uploadedAt: String
)

/** 업로드 메타데이터 요청 본문 */
@Serializable
data class UploadMetadataDto(
    @SerialName("wall_grade")   val wallGrade: String,
    @SerialName("gym_name")     val gymName: String,
    val memo: String = ""
)
