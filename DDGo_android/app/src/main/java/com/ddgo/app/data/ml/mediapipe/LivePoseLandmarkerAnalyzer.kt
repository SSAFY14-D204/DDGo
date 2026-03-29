package com.ddgo.app.data.ml.mediapipe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import com.ddgo.app.domain.model.AiLandmark3D
import com.ddgo.app.domain.model.AiPoseFrame
import com.ddgo.app.domain.repository.LivePoseAnalysisSummary
import com.ddgo.app.domain.repository.LivePoseAnalyzerRepository
import com.ddgo.app.domain.repository.LivePoseFrameInput
import com.ddgo.app.domain.repository.LivePoseSessionConfig
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.Landmark
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LivePoseLandmarkerAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context
) : LivePoseAnalyzerRepository {

    private val mutex = Mutex()
    private val running = AtomicBoolean(false)
    private val submittedFrameCount = AtomicInteger(0)
    private val detectedFrameCount = AtomicInteger(0)
    private val pendingFrameIndicesByTimestampMs = ConcurrentHashMap<Long, Int>()

    private var poseLandmarker: PoseLandmarker? = null
    private var poseFrameListener: ((AiPoseFrame) -> Unit)? = null
    private var errorListener: ((Throwable) -> Unit)? = null
    private var lastFrameTimestampMs: Long? = null
    private var lastErrorMessage: String? = null

    override suspend fun start(
        config: LivePoseSessionConfig,
        onPoseFrame: (AiPoseFrame) -> Unit,
        onError: (Throwable) -> Unit
    ): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            mutex.withLock {
                if (running.get()) {
                    poseFrameListener = onPoseFrame
                    errorListener = onError
                    return@withLock
                }

                poseLandmarker = createLandmarker(config)
                poseFrameListener = onPoseFrame
                errorListener = onError
                submittedFrameCount.set(0)
                detectedFrameCount.set(0)
                pendingFrameIndicesByTimestampMs.clear()
                lastFrameTimestampMs = null
                lastErrorMessage = null
                running.set(true)
            }
        }
    }

    override suspend fun submitFrame(frame: LivePoseFrameInput): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            if (!running.get()) {
                throw IllegalStateException("Live pose analyzer is not running.")
            }

            val landmarker = poseLandmarker ?: throw IllegalStateException("Pose landmarker was not initialized.")
            val bitmap = frame.toBitmap()
            val mpImage = BitmapImageBuilder(bitmap).build()

            try {
                pendingFrameIndicesByTimestampMs[frame.timestampMs] = frame.frameIndex
                submittedFrameCount.incrementAndGet()
                lastFrameTimestampMs = frame.timestampMs
                landmarker.detectAsync(mpImage, frame.timestampMs)
            } finally {
                mpImage.close()
                bitmap.recycle()
            }
        }.onFailure { throwable ->
            lastErrorMessage = throwable.message ?: throwable.javaClass.simpleName
            errorListener?.invoke(throwable)
            Log.w(TAG, "submitFrame failed", throwable)
        }
    }

    override suspend fun stop(): Result<LivePoseAnalysisSummary> = withContext(Dispatchers.Default) {
        runCatching {
            mutex.withLock {
                running.set(false)
                poseLandmarker?.close()
                poseLandmarker = null
                poseFrameListener = null
                errorListener = null
                pendingFrameIndicesByTimestampMs.clear()
            }

            LivePoseAnalysisSummary(
                submittedFrameCount = submittedFrameCount.get(),
                detectedFrameCount = detectedFrameCount.get(),
                lastFrameTimestampMs = lastFrameTimestampMs,
                endedAtTimestampMs = System.currentTimeMillis(),
                lastErrorMessage = lastErrorMessage
            )
        }
    }

    private fun createLandmarker(config: LivePoseSessionConfig): PoseLandmarker {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET)
            .build()

        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(config.minPoseDetectionConfidence)
            .setMinPosePresenceConfidence(config.minPosePresenceConfidence)
            .setMinTrackingConfidence(config.minTrackingConfidence)
            .setResultListener { result, _ ->
                if (!running.get()) {
                    return@setResultListener
                }

                val frameIndex = pendingFrameIndicesByTimestampMs.remove(result.timestampMs()) ?: -1
                val landmarks = result.landmarks().firstOrNull().orEmpty()
                val worldLandmarks = result.worldLandmarks().firstOrNull().orEmpty()

                if (landmarks.isEmpty()) {
                    poseFrameListener?.invoke(
                        AiPoseFrame(
                            frameIndex = frameIndex,
                            timestampMs = result.timestampMs(),
                            poseDetected = false,
                            poseLandmarks = emptyList(),
                            poseWorldLandmarks = emptyList()
                        )
                    )
                    return@setResultListener
                }

                detectedFrameCount.incrementAndGet()
                poseFrameListener?.invoke(
                    AiPoseFrame(
                        frameIndex = frameIndex,
                        timestampMs = result.timestampMs(),
                        poseDetected = true,
                        poseLandmarks = landmarks.toAiNormalizedLandmarks(),
                        poseWorldLandmarks = worldLandmarks.toAiWorldLandmarks()
                    )
                )
            }
            .setErrorListener { error ->
                lastErrorMessage = error.message ?: error.javaClass.simpleName
                errorListener?.invoke(error)
                Log.e(TAG, "Live pose analyzer error", error)
            }
            .build()

        return PoseLandmarker.createFromOptions(context, options)
    }

    private fun LivePoseFrameInput.toBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(argb8888Bytes).order(ByteOrder.nativeOrder()))
        if (rotationDegrees == 0) {
            return bitmap
        }

        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) {
            bitmap.recycle()
        }
        return rotated
    }

    private fun List<NormalizedLandmark>.toAiNormalizedLandmarks(): List<AiLandmark3D> {
        return mapIndexed { index, landmark ->
            AiLandmark3D(
                index = index,
                x = landmark.x(),
                y = landmark.y(),
                z = landmark.z(),
                visibility = landmark.visibility().toNullable(),
                presence = landmark.presence().toNullable()
            )
        }
    }

    private fun List<Landmark>.toAiWorldLandmarks(): List<AiLandmark3D> {
        return mapIndexed { index, landmark ->
            AiLandmark3D(
                index = index,
                x = landmark.x(),
                y = landmark.y(),
                z = landmark.z(),
                visibility = landmark.visibility().toNullable(),
                presence = landmark.presence().toNullable()
            )
        }
    }

    private fun Optional<Float>.toNullable(): Float? = if (isPresent) get() else null

    companion object {
        private const val TAG = "LivePoseAnalyzer"
        private const val MODEL_ASSET = "models/pose_landmarker_lite.task"
    }
}
