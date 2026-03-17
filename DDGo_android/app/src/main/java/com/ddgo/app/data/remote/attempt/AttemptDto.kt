package com.ddgo.app.data.remote.attempt

import kotlinx.serialization.Serializable

/** 시도 시작 응답 DTO입니다. */
@Serializable
data class AttemptStartResponseDto(
    val attemptId: Long,
    val attemptNo: Int
)

/** presigned URL 발급 요청 DTO입니다. */
@Serializable
data class GenerateVideoUrlRequestDto(
    val originalFileName: String,
    val contentType: String,
    val fileSize: Long
)

/** presigned URL 발급 응답 DTO입니다. */
@Serializable
data class GenerateVideoUrlResponseDto(
    val videoUrl: String,
    val objectKey: String
)
