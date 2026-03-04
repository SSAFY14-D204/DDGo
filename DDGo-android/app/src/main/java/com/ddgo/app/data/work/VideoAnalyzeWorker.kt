package com.ddgo.app.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ddgo.app.domain.repository.HoldDetector
import com.ddgo.app.domain.repository.PoseEstimator
import com.ddgo.app.domain.usecase.ExtractFailClipUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * 영상 분석 + 업로드를 담당하는 백그라운드 Worker.
 *
 * ─────────────────────────────────────────────────────────────────
 * 이 Worker는 아키텍처의 핵심 "오케스트라 지휘자"입니다:
 *   1. data/ml (PoseEstimator, HoldDetector)를 호출해 영상 분석
 *   2. domain/usecase (ExtractFailClipUseCase)로 실패 시점 계산
 *   3. data/remote/UploadApi를 통해 서버로 업로드
 *
 * UI(feature/upload/UploadViewModel)는 WorkManager에게 실행만 '요청'하고 빠집니다.
 * ─────────────────────────────────────────────────────────────────
 *
 * @HiltWorker: Worker에 Hilt 의존성을 주입하기 위한 어노테이션.
 * @AssistedInject: WorkerParameters는 WorkManager가 제공하므로 Assisted로 받아야 합니다.
 */
@HiltWorker
class VideoAnalyzeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val poseEstimator: PoseEstimator,
    private val holdDetector: HoldDetector,
    private val extractFailClipUseCase: ExtractFailClipUseCase,
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_VIDEO_URI = "video_uri"
        const val KEY_WALL_GRADE = "wall_grade"
    }

    override suspend fun doWork(): Result {
        val videoUri = inputData.getString(KEY_VIDEO_URI)
            ?: return Result.failure()

        return try {
            // Step 1: AI 분석 (병렬 실행 가능)
            val poses = poseEstimator.estimateFromVideo(videoUri)
            val holds = holdDetector.detectFromVideo(videoUri)

            // Step 2: 실패 시점 계산
            val failTimeMs = extractFailClipUseCase(poses, holds)

            // Step 3: 서버 업로드
            // TODO: UploadRepositoryImpl.uploadVideo() 호출

            Result.success()
        } catch (e: Exception) {
            // 재시도 가능한 실패 (네트워크 오류 등)
            Result.retry()
        }
    }
}
