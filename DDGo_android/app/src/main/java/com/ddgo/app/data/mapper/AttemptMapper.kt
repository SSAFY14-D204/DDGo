package com.ddgo.app.data.mapper

import com.ddgo.app.data.remote.attempt.AttemptStartResponseDto
import com.ddgo.app.data.remote.attempt.GenerateVideoUrlResponseDto
import com.ddgo.app.domain.model.AttemptUploadTicket
import com.ddgo.app.domain.model.UploadedAttemptVideo

/**
 * Attempt DTO를 Domain 모델로 변환하는 매퍼입니다.
 *
 * 규칙:
 * - 백엔드 응답은 feature 계층에서 바로 쓰지 않고 domain 모델로 변환합니다.
 */
object AttemptMapper {

    /** 시도 시작 응답과 업로드 응답을 UploadedAttemptVideo로 변환합니다. */
    fun toUploadedAttemptVideo(
        challengeId: Long,
        videoUri: String,
        startResponse: AttemptStartResponseDto,
        uploadResponse: GenerateVideoUrlResponseDto
    ): UploadedAttemptVideo = UploadedAttemptVideo(
        challengeId = challengeId,
        attemptId = startResponse.attemptId,
        attemptNo = startResponse.attemptNo,
        videoUri = videoUri,
        objectKey = uploadResponse.objectKey
    )

    /** presigned 업로드 URL 응답을 domain 티켓 모델로 변환합니다. */
    fun GenerateVideoUrlResponseDto.toDomain(
        attemptId: Long
    ): AttemptUploadTicket = AttemptUploadTicket(
        attemptId = attemptId,
        uploadUrl = videoUrl,
        objectKey = objectKey
    )
}
