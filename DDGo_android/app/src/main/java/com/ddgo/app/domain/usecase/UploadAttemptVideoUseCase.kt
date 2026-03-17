package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.model.UploadedAttemptVideo
import com.ddgo.app.domain.repository.AttemptRepository
import javax.inject.Inject

/** 시도를 시작하고 해당 시도에 영상 1개를 업로드하는 유스케이스입니다. */
class UploadAttemptVideoUseCase @Inject constructor(
    private val attemptRepository: AttemptRepository
) {

    suspend operator fun invoke(
        challengeId: Long,
        videoUri: String
    ): Result<UploadedAttemptVideo> {
        if (challengeId <= 0L) {
            return Result.failure(Exception("Challenge ID is invalid."))
        }
        if (videoUri.isBlank()) {
            return Result.failure(Exception("Video URI is required."))
        }

        return attemptRepository.uploadAttemptVideo(
            challengeId = challengeId,
            videoUri = videoUri
        )
    }
}
