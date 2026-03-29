package com.ddgo.app.data.ml.mediapipe

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.model.PoseLandmark
import com.ddgo.app.domain.repository.PoseEstimator
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Optional
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * MediaPipe Tasks Vision을 사용하는 PoseEstimator 구현체.
 *
 * - `estimateFromFrame()`는 결과 화면의 단일 프레임 오버레이 등에 사용할 수 있는
 *   IMAGE 모드 추론을 유지합니다.
 * - `estimateFromVideo()`는 sequential decode 기반 pre-pose 분석기로 위임해
 *   dense pose 시퀀스를 반환합니다.
 */
class PoseEstimatorImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sequentialPoseVideoAnalyzer: SequentialPoseVideoAnalyzer
) : PoseEstimator {

    private val imageLandmarker: PoseLandmarker? by lazy {
        try {
            PoseLandmarker.createFromOptions(
                context,
                PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(
                        BaseOptions.builder()
                            .setModelAssetPath(MODEL_ASSET)
                            .build()
                    )
                    .setRunningMode(RunningMode.IMAGE)
                    .setNumPoses(1)
                    .setMinPoseDetectionConfidence(0.5f)
                    .setMinPosePresenceConfidence(0.5f)
                    .setMinTrackingConfidence(0.5f)
                    .build()
            )
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "MediaPipe IMAGE mode is unavailable on this device.", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize image landmarker.", e)
            null
        }
    }

    @Volatile
    private var isRunning = false

    override suspend fun estimateFromFrame(bitmap: Bitmap): List<PoseLandmark> =
        withContext(Dispatchers.Default) {
            val landmarker = imageLandmarker ?: return@withContext emptyList()
            if (isRunning) return@withContext emptyList()

            isRunning = true
            val mpImage = BitmapImageBuilder(bitmap).build()
            try {
                val result = landmarker.detect(mpImage)
                if (result.landmarks().isEmpty()) {
                    emptyList()
                } else {
                    result.landmarks()[0].mapIndexed { idx, lm: NormalizedLandmark ->
                        PoseLandmark(
                            index = idx,
                            x = lm.x(),
                            y = lm.y(),
                            z = lm.z(),
                            visibility = lm.visibility().toNullable(),
                            presence = lm.presence().toNullable()
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "estimateFromFrame failed.", e)
                emptyList()
            } finally {
                mpImage.close()
                isRunning = false
            }
        }

    override suspend fun estimateFromVideo(
        videoUri: String,
        analysisFpsLimit: Int
    ) = withContext(Dispatchers.IO) {
        try {
            sequentialPoseVideoAnalyzer(
                videoUri = videoUri,
                analysisFpsLimit = analysisFpsLimit
            )
                .also { Log.d(TAG, "estimateFromVideo completed: ${it.size} poses") }
        } catch (e: Exception) {
            Log.e(TAG, "estimateFromVideo failed.", e)
            emptyList()
        }
    }

    private fun Optional<Float>.toNullable(): Float? = if (isPresent) get() else null

    companion object {
        private const val TAG = "PoseEstimatorImpl"
        private const val MODEL_ASSET = "models/pose_landmarker_lite.task"
    }
}
