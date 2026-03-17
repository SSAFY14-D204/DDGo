package com.ddgo.app.domain.repository

import com.ddgo.app.domain.model.UploadedAttemptVideo

/**
 * AttemptRepository 계약입니다.
 *
 * 역할:
 * - 챌린지 아래에서 시도를 생성하고 시도 영상을 업로드합니다.
 */
interface AttemptRepository {

    /** 특정 챌린지에서 시도를 시작하고 영상을 업로드합니다. */
    suspend fun uploadAttemptVideo(
        challengeId: Long,
        videoUri: String
    ): Result<UploadedAttemptVideo>
}
