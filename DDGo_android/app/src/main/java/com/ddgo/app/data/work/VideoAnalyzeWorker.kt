package com.ddgo.app.data.work

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ddgo.app.domain.repository.HoldDetector
import com.ddgo.app.domain.repository.PersonDetector
import com.ddgo.app.domain.repository.PoseEstimator
import com.ddgo.app.domain.usecase.ExtractFailClipUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import wseemann.media.FFmpegMediaMetadataRetriever

/**
 * 영상 분석 + 업로드를 담당하는 백그라운드 Worker.
 */
@HiltWorker
class VideoAnalyzeWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val poseEstimator: PoseEstimator,
    private val personDetector: PersonDetector,
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
            // Step 1: 사람이 가장 적은 최적의 프레임 시간 찾기
            val bestFrameTimeUs = personDetector.findBestFrameTime(videoUri)

            // Step 2: 해당 시점의 프레임(Bitmap) 추출
            val bestFrameBitmap = extractFrame(videoUri, bestFrameTimeUs)

            // Step 3: 추출된 프레임에서 홀드 탐지 및 포즈 추정 (병렬 가능)
            val holds = bestFrameBitmap?.let { holdDetector.detectFromFrame(it) } ?: emptyList()
            val poses = poseEstimator.estimateFromVideo(videoUri)

            bestFrameBitmap?.recycle()

            // Step 4: 실패 시점 계산
            val failTimeMs = extractFailClipUseCase(poses, holds)

            // Step 5: 서버 업로드
            // TODO: UploadRepositoryImpl.uploadVideo() 호출

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun extractFrame(videoUri: String, timeUs: Long): Bitmap? {
        val retriever = FFmpegMediaMetadataRetriever()
        return try {
            val uri = Uri.parse(videoUri)
            if (uri.scheme == "file") {
                retriever.setDataSource(uri.path)
            } else {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    retriever.setDataSource(pfd.fileDescriptor)
                }
            }
            retriever.getFrameAtTime(timeUs, FFmpegMediaMetadataRetriever.OPTION_CLOSEST)
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
}
