package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.repository.PoseEstimator
import javax.inject.Inject

/**
 * 로컬 비디오 URI를 받아 프레임별 포즈 시퀀스를 추출합니다.
 */
class EstimatePoseFromVideoUseCase @Inject constructor(
    private val poseEstimator: PoseEstimator
) {
    suspend operator fun invoke(videoUri: String): Result<List<Pose>> = runCatching {
        poseEstimator.estimateFromVideo(videoUri)
    }
}
