package com.ddgo.app.data.ml.mediapipe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import com.ddgo.app.domain.model.AiPayloadSource
import com.ddgo.app.domain.model.AiLandmark3D
import com.ddgo.app.domain.model.AiPoseFrame
import com.ddgo.app.domain.model.AiPoseSequence
import com.ddgo.app.domain.model.AiVideoMetadata
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.repository.AiPoseSequenceProvider
import com.ddgo.app.domain.model.PoseLandmark
import com.ddgo.app.domain.model.PosePixelPoint
import com.ddgo.app.domain.model.PoseWorldPoint
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.Landmark
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.Optional
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * MediaPipe Tasks Vision을 사용하는 비디오 포즈 분석기.
 *
 * - `estimateFromVideo()`는 기존처럼 [Pose] 목록만 반환합니다.
 * - `analyzePoseSequence()`는 AI 서버 전송용 리치 시퀀스를 생성합니다.
 */
class SequentialPoseVideoAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context
) : AiPoseSequenceProvider {

    suspend operator fun invoke(
        videoUri: String,
        analysisFpsLimit: Int = DEFAULT_ANALYSIS_FPS_LIMIT
    ): List<Pose> = withContext(Dispatchers.IO) {
        analyzeInternal(
            videoUri = videoUri,
            analysisFpsLimit = analysisFpsLimit,
            cancellationCheckpoint = {
                coroutineContext.ensureActive()
            }
        ).poses
    }

    override suspend fun analyzePoseSequence(
        videoUri: String,
        analysisFpsLimit: Int
    ): AiPoseSequence = withContext(Dispatchers.IO) {
        analyzeInternal(
            videoUri = videoUri,
            analysisFpsLimit = analysisFpsLimit,
            cancellationCheckpoint = {
                coroutineContext.ensureActive()
            }
        ).sequence
    }

    private fun analyzeInternal(
        videoUri: String,
        analysisFpsLimit: Int,
        cancellationCheckpoint: () -> Unit
    ): PoseSequenceAnalysisResult {
        val poseLandmarker = createPoseLandmarker() ?: return emptyAnalysisResult(videoUri, analysisFpsLimit)

        try {
            return analyzeSequentialFrames(
                uri = Uri.parse(videoUri),
                poseLandmarker = poseLandmarker,
                analysisFpsLimit = analysisFpsLimit,
                cancellationCheckpoint = cancellationCheckpoint
            )
        } finally {
            poseLandmarker.close()
        }
    }

    private fun analyzeSequentialFrames(
        uri: Uri,
        poseLandmarker: PoseLandmarker,
        analysisFpsLimit: Int,
        cancellationCheckpoint: () -> Unit
    ): PoseSequenceAnalysisResult {
        val extractor = MediaExtractor()

        try {
            require(setExtractorDataSource(extractor, uri)) {
                "Could not open selected video."
            }

            val videoTrackIndex = findVideoTrackIndex(extractor)
            if (videoTrackIndex == -1) {
                Log.w(TAG, "No video track found.")
                return emptyAnalysisResult(uri.toString(), analysisFpsLimit)
            }

            extractor.selectTrack(videoTrackIndex)
            val trackFormat = extractor.getTrackFormat(videoTrackIndex)
            val mimeType = trackFormat.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalStateException("Missing video mime type.")
            val rotationDegrees = trackFormat.readRotationDegrees()
            val frameRate = trackFormat.readFrameRateOrNull()
            val frameWidth = trackFormat.readVideoSize(MediaFormat.KEY_WIDTH)
            val frameHeight = trackFormat.readVideoSize(MediaFormat.KEY_HEIGHT)
            val decoder = createVideoDecoder(
                mimeType = mimeType,
                trackFormat = trackFormat
            )

            try {
                return decodeEveryFrame(
                    extractor = extractor,
                    decoder = decoder,
                    poseLandmarker = poseLandmarker,
                    analysisFpsLimit = analysisFpsLimit,
                    rotationDegrees = rotationDegrees,
                    frameWidth = frameWidth,
                    frameHeight = frameHeight,
                    frameRate = frameRate,
                    mimeType = mimeType,
                    sourceUri = uri,
                    cancellationCheckpoint = cancellationCheckpoint
                )
            } finally {
                runCatching { decoder.stop() }
                    .onFailure { error -> Log.w(TAG, "Failed to stop decoder.", error) }
                decoder.release()
            }
        } finally {
            extractor.release()
        }
    }

    private fun decodeEveryFrame(
        extractor: MediaExtractor,
        decoder: MediaCodec,
        poseLandmarker: PoseLandmarker,
        analysisFpsLimit: Int,
        rotationDegrees: Int,
        frameWidth: Int,
        frameHeight: Int,
        frameRate: Int?,
        mimeType: String,
        sourceUri: Uri,
        cancellationCheckpoint: () -> Unit
    ): PoseSequenceAnalysisResult {
        val bufferInfo = MediaCodec.BufferInfo()
        val poses = ArrayList<Pose>()
        val aiFrames = ArrayList<AiPoseFrame>()
        val normalizedAnalysisFpsLimit = analysisFpsLimit.coerceAtLeast(1)
        val minProcessFrameGapUs = 1_000_000L / normalizedAnalysisFpsLimit
        var inputEnded = false
        var outputEnded = false
        var decodedFrameCount = 0
        var processedFrameCount = 0
        var skippedFrameCount = 0
        var lastProcessedPresentationTimeUs = Long.MIN_VALUE

        Log.d(
            TAG,
            "Sequential pose decode started: fps=${frameRate ?: "unknown"}, targetAnalysisFps=$normalizedAnalysisFpsLimit"
        )

        while (!outputEnded) {
            cancellationCheckpoint()

            if (!inputEnded) {
                val inputBufferIndex = decoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputBufferIndex)
                        ?: throw IllegalStateException("Could not obtain decoder input buffer.")

                    inputBuffer.clear()
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)

                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(
                            inputBufferIndex,
                            0,
                            0,
                            0L,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        inputEnded = true
                    } else {
                        decoder.queueInputBuffer(
                            inputBufferIndex,
                            0,
                            sampleSize,
                            extractor.sampleTime,
                            extractor.sampleFlags
                        )
                        extractor.advance()
                    }
                }
            }

            when (val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    Log.d(TAG, "Decoder output format changed: ${decoder.outputFormat}")
                }

                else -> {
                    if (outputBufferIndex < 0) continue

                    val presentationTimeUs = bufferInfo.presentationTimeUs
                    val isEndOfStream =
                        bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    val isCodecConfig =
                        bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0

                    if (!isCodecConfig && presentationTimeUs >= 0L) {
                        decodedFrameCount++

                        if (
                            lastProcessedPresentationTimeUs != Long.MIN_VALUE &&
                            presentationTimeUs - lastProcessedPresentationTimeUs < minProcessFrameGapUs
                        ) {
                            skippedFrameCount++
                        } else {
                            lastProcessedPresentationTimeUs = presentationTimeUs
                            val currentFrameIndex = processedFrameCount
                            processedFrameCount++

                            runCatching {
                                inferPoseFromDecodedFrame(
                                    decoder = decoder,
                                    outputBufferIndex = outputBufferIndex,
                                    poseLandmarker = poseLandmarker,
                                    presentationTimeUs = presentationTimeUs,
                                    rotationDegrees = rotationDegrees,
                                    frameIndex = currentFrameIndex
                                )
                            }.onFailure { error ->
                                Log.e(TAG, "Pose inference failed at ptsUs=$presentationTimeUs", error)
                            }.getOrNull()?.let { capture ->
                                aiFrames.add(capture.frame)
                                capture.pose?.let(poses::add)
                            }
                        }
                    }

                    decoder.releaseOutputBuffer(outputBufferIndex, false)

                    if (isEndOfStream) {
                        outputEnded = true
                    }
                }
            }
        }

        Log.d(
            TAG,
            "Sequential pose decode completed: decoded=$decodedFrameCount, processed=$processedFrameCount, skipped=$skippedFrameCount, poses=${poses.size}"
        )

        return PoseSequenceAnalysisResult(
            sequence = AiPoseSequence(
                source = AiPayloadSource(
                    videoUri = sourceUri.toString(),
                    generator = TAG,
                    exportedAtIso = Instant.now().toString(),
                    uri = sourceUri.toString(),
                    displayName = sourceUri.lastPathSegment,
                    mimeType = mimeType,
                    path = if (sourceUri.scheme == "file") sourceUri.path else null,
                    legacySourceFile = sourceUri.toString()
                ),
                videoMetadata = AiVideoMetadata(
                    frameWidth = frameWidth,
                    frameHeight = frameHeight,
                    fps = frameRate?.toFloat(),
                    totalFrames = decodedFrameCount,
                    processedFrames = processedFrameCount,
                    frameStep = normalizedAnalysisFpsLimit,
                    rotationDegrees = rotationDegrees,
                    mimeType = mimeType,
                    analysisFpsLimit = normalizedAnalysisFpsLimit,
                    decodedFrameCount = decodedFrameCount,
                    skippedFrameCount = skippedFrameCount
                ),
                frames = aiFrames
            ),
            poses = poses
        )
    }

    private fun inferPoseFromDecodedFrame(
        decoder: MediaCodec,
        outputBufferIndex: Int,
        poseLandmarker: PoseLandmarker,
        presentationTimeUs: Long,
        rotationDegrees: Int,
        frameIndex: Int
    ): PoseCapture? {
        val image = decoder.getOutputImage(outputBufferIndex) ?: return null

        try {
            val rawBitmap = image.toBitmap()
            val preparedFrame = rawBitmap.prepareForInference(rotationDegrees)

            try {
                return inferPose(
                    poseLandmarker = poseLandmarker,
                    frameBitmap = preparedFrame.bitmap,
                    frameTimeMs = presentationTimeUs / 1_000L,
                    frameIndex = frameIndex,
                    frameWidthPx = preparedFrame.referenceWidthPx,
                    frameHeightPx = preparedFrame.referenceHeightPx
                )
            } finally {
                if (preparedFrame.bitmap !== rawBitmap) {
                    preparedFrame.bitmap.recycle()
                }
                rawBitmap.recycle()
            }
        } finally {
            image.close()
        }
    }

    private fun inferPose(
        poseLandmarker: PoseLandmarker,
        frameBitmap: Bitmap,
        frameTimeMs: Long,
        frameIndex: Int,
        frameWidthPx: Int,
        frameHeightPx: Int
    ): PoseCapture {
        val mpImage = BitmapImageBuilder(frameBitmap).build()

        try {
            val result = poseLandmarker.detectForVideo(mpImage, frameTimeMs)
            val landmarks = result.landmarks().firstOrNull().orEmpty()
            val worldLandmarks = result.worldLandmarks().firstOrNull().orEmpty()

            val pose = if (landmarks.isEmpty()) {
                null
            } else {
                landmarks.toPose(
                frameTimeMs = result.timestampMs(),
                frameWidthPx = frameWidthPx,
                frameHeightPx = frameHeightPx,
                    worldLandmarks = worldLandmarks
                )
            }

            return PoseCapture(
                frame = AiPoseFrame(
                    frameIndex = frameIndex,
                    timestampMs = result.timestampMs(),
                    poseDetected = pose != null,
                    poseLandmarks = landmarks.mapIndexed { index, landmark ->
                        landmark.toAiLandmark(
                            index = index,
                            xSelector = { x() },
                            ySelector = { y() },
                            zSelector = { z() },
                            visibilitySelector = { visibility().toNullable() },
                            presenceSelector = { presence().toNullable() }
                        )
                    },
                    poseWorldLandmarks = worldLandmarks.mapIndexed { index, landmark ->
                        landmark.toAiLandmark(
                            index = index,
                            xSelector = { x() },
                            ySelector = { y() },
                            zSelector = { z() },
                            visibilitySelector = { visibility().toNullable() },
                            presenceSelector = { presence().toNullable() }
                        )
                    }
                ),
                pose = pose
            )
        } finally {
            mpImage.close()
        }
    }

    private fun createPoseLandmarker(): PoseLandmarker? {
        return try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(POSE_MODEL_PATH)
                .build()
            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.VIDEO)
                .setNumPoses(1)
                .setMinPoseDetectionConfidence(0.5f)
                .setMinPosePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .build()

            PoseLandmarker.createFromOptions(context, options)
        } catch (error: UnsatisfiedLinkError) {
            Log.w(TAG, "MediaPipe video analyzer is unavailable on this device.", error)
            null
        } catch (error: Exception) {
            Log.e(TAG, "Failed to create pose landmarker.", error)
            null
        }
    }

    private fun createVideoDecoder(
        mimeType: String,
        trackFormat: MediaFormat
    ): MediaCodec {
        val decoder = MediaCodec.createDecoderByType(mimeType)
        val capabilities = decoder.codecInfo.getCapabilitiesForType(mimeType)
        val supportsFlexibleYuv = capabilities.colorFormats.contains(
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
        )

        if (supportsFlexibleYuv) {
            trackFormat.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            )
        }

        decoder.configure(trackFormat, null, null, 0)
        decoder.start()
        return decoder
    }

    private fun findVideoTrackIndex(extractor: MediaExtractor): Int {
        for (trackIndex in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(trackIndex).getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("video/") == true) {
                return trackIndex
            }
        }
        return -1
    }

    private fun setExtractorDataSource(
        extractor: MediaExtractor,
        uri: Uri
    ): Boolean {
        return try {
            if (uri.scheme == "file") {
                extractor.setDataSource(uri.path ?: return false)
            } else {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    extractor.setDataSource(pfd.fileDescriptor)
                } ?: return false
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun MediaFormat.readRotationDegrees(): Int {
        return if (containsKey(MediaFormat.KEY_ROTATION)) {
            getInteger(MediaFormat.KEY_ROTATION)
        } else {
            0
        }
    }

    private fun MediaFormat.readFrameRateOrNull(): Int? {
        return if (containsKey(MediaFormat.KEY_FRAME_RATE)) {
            getInteger(MediaFormat.KEY_FRAME_RATE)
        } else {
            null
        }
    }

    private fun MediaFormat.readVideoSize(key: String): Int {
        return if (containsKey(key)) {
            getInteger(key)
        } else {
            0
        }
    }

    private fun Image.toBitmap(): Bitmap {
        val cropWidth = cropRect.width()
        val cropHeight = cropRect.height()
        val nv21Bytes = toNv21Bytes()
        val yuvImage = YuvImage(nv21Bytes, ImageFormat.NV21, cropWidth, cropHeight, null)
        val jpegStream = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, cropWidth, cropHeight), 90, jpegStream)
        val jpegBytes = jpegStream.toByteArray()
        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            ?: throw IllegalStateException("Failed to decode frame bitmap.")
    }

    private fun Image.toNv21Bytes(): ByteArray {
        val cropRect = cropRect
        val width = cropRect.width()
        val height = cropRect.height()
        val planes = planes
        val output = ByteArray(width * height * ImageFormat.getBitsPerPixel(format) / 8)
        val rowData = ByteArray(planes.maxOf { it.rowStride })

        for (planeIndex in planes.indices) {
            val plane = planes[planeIndex]
            val buffer = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val shift = if (planeIndex == 0) 0 else 1
            val planeWidth = width shr shift
            val planeHeight = height shr shift
            val outputOffset = when (planeIndex) {
                0 -> 0
                1 -> width * height + 1
                else -> width * height
            }
            val outputStride = if (planeIndex == 0) 1 else 2

            var channelOffset = outputOffset
            buffer.position(
                rowStride * (cropRect.top shr shift) + pixelStride * (cropRect.left shr shift)
            )

            for (row in 0 until planeHeight) {
                val length = if (pixelStride == 1 && outputStride == 1) {
                    planeWidth
                } else {
                    (planeWidth - 1) * pixelStride + 1
                }
                if (pixelStride == 1 && outputStride == 1) {
                    buffer.get(output, channelOffset, length)
                    channelOffset += length
                } else {
                    buffer.get(rowData, 0, length)
                    for (column in 0 until planeWidth) {
                        output[channelOffset] = rowData[column * pixelStride]
                        channelOffset += outputStride
                    }
                }
                if (row < planeHeight - 1) {
                    buffer.position(buffer.position() + rowStride - length)
                }
            }
        }
        return output
    }

    private fun List<NormalizedLandmark>.toPose(
        frameTimeMs: Long,
        frameWidthPx: Int,
        frameHeightPx: Int,
        worldLandmarks: List<Landmark>
    ): Pose = Pose(
        frameTimeMs = frameTimeMs,
        landmarks = mapIndexed { index, landmark ->
            PoseLandmark(
                index = index,
                x = landmark.x(),
                y = landmark.y(),
                z = landmark.z(),
                visibility = landmark.visibility().toNullable(),
                presence = landmark.presence().toNullable()
            )
        },
        landmarksPx = toNamedPixelMap(
            frameWidthPx = frameWidthPx,
            frameHeightPx = frameHeightPx
        ),
        worldLandmarksSample = worldLandmarks.toNamedWorldMap()
    )

    private fun List<NormalizedLandmark>.toNamedPixelMap(
        frameWidthPx: Int,
        frameHeightPx: Int
    ): Map<String, PosePixelPoint> {
        if (isEmpty()) return emptyMap()

        val points = linkedMapOf<String, PosePixelPoint>()
        POSE_DTO_LANDMARK_NAMES_BY_INDEX.forEach { (index, landmarkName) ->
            getOrNull(index)?.let { landmark ->
                points[landmarkName] = PosePixelPoint(
                    x = landmark.x() * frameWidthPx.toFloat(),
                    y = landmark.y() * frameHeightPx.toFloat()
                )
            }
        }
        return points
    }

    private fun List<Landmark>.toNamedWorldMap(): Map<String, PoseWorldPoint> {
        if (isEmpty()) return emptyMap()

        val points = linkedMapOf<String, PoseWorldPoint>()
        POSE_DTO_LANDMARK_NAMES_BY_INDEX.forEach { (index, landmarkName) ->
            getOrNull(index)?.let { landmark ->
                points[landmarkName] = PoseWorldPoint(
                    x = landmark.x(),
                    y = landmark.y(),
                    z = landmark.z()
                )
            }
        }
        return points
    }

    private fun Bitmap.prepareForInference(rotationDegrees: Int): PreparedInferenceBitmap {
        val orientedBitmap = if (rotationDegrees == 0) {
            this
        } else {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
        }

        val referenceWidthPx = orientedBitmap.width
        val referenceHeightPx = orientedBitmap.height
        val maxDimension = max(orientedBitmap.width, orientedBitmap.height)
        if (maxDimension <= MAX_INFERENCE_DIMENSION_PX) {
            return PreparedInferenceBitmap(
                bitmap = orientedBitmap,
                referenceWidthPx = referenceWidthPx,
                referenceHeightPx = referenceHeightPx
            )
        }

        val scale = MAX_INFERENCE_DIMENSION_PX.toFloat() / maxDimension.toFloat()
        val scaledWidth = (orientedBitmap.width * scale).roundToInt().coerceAtLeast(1)
        val scaledHeight = (orientedBitmap.height * scale).roundToInt().coerceAtLeast(1)
        val scaledBitmap = Bitmap.createScaledBitmap(orientedBitmap, scaledWidth, scaledHeight, true)
        if (orientedBitmap !== this) {
            orientedBitmap.recycle()
        }
        return PreparedInferenceBitmap(
            bitmap = scaledBitmap,
            referenceWidthPx = referenceWidthPx,
            referenceHeightPx = referenceHeightPx
        )
    }

    private fun Optional<Float>.toNullable(): Float? = if (isPresent) get() else null

    private inline fun <T> T.toAiLandmark(
        index: Int,
        xSelector: T.() -> Float,
        ySelector: T.() -> Float,
        zSelector: T.() -> Float,
        visibilitySelector: T.() -> Float? = { null },
        presenceSelector: T.() -> Float? = { null }
    ): AiLandmark3D {
        return AiLandmark3D(
            index = index,
            x = xSelector(),
            y = ySelector(),
            z = zSelector(),
            visibility = visibilitySelector(),
            presence = presenceSelector()
        )
    }

    private data class PreparedInferenceBitmap(
        val bitmap: Bitmap,
        val referenceWidthPx: Int,
        val referenceHeightPx: Int
    )

    companion object {
        private const val TAG = "SequentialPoseVideoAnalyzer"
        private const val POSE_MODEL_PATH = "models/pose_landmarker_lite.task"
        private const val DEQUEUE_TIMEOUT_US = 10_000L
        private const val MAX_INFERENCE_DIMENSION_PX = 640
        private const val DEFAULT_ANALYSIS_FPS_LIMIT = 10
        private val POSE_DTO_LANDMARK_NAMES_BY_INDEX = linkedMapOf(
            11 to "left_shoulder",
            12 to "right_shoulder",
            13 to "left_elbow",
            14 to "right_elbow",
            15 to "left_wrist",
            16 to "right_wrist",
            19 to "left_hand_tip",
            20 to "right_hand_tip",
            23 to "left_hip",
            24 to "right_hip",
            25 to "left_knee",
            26 to "right_knee",
            27 to "left_ankle",
            28 to "right_ankle"
        )
    }
}

private data class PoseCapture(
    val frame: AiPoseFrame,
    val pose: Pose?
)

private data class PoseSequenceAnalysisResult(
    val sequence: AiPoseSequence,
    val poses: List<Pose>
)

private fun emptyAnalysisResult(
    videoUri: String,
    analysisFpsLimit: Int
): PoseSequenceAnalysisResult {
    return PoseSequenceAnalysisResult(
        sequence = AiPoseSequence(
            source = AiPayloadSource(
                videoUri = videoUri,
                generator = "SequentialPoseVideoAnalyzer",
                exportedAtIso = Instant.now().toString(),
                uri = videoUri,
                legacySourceFile = videoUri
            ),
            videoMetadata = AiVideoMetadata(
                frameWidth = 0,
                frameHeight = 0,
                fps = null,
                totalFrames = 0,
                processedFrames = 0,
                frameStep = analysisFpsLimit.coerceAtLeast(1),
                rotationDegrees = 0,
                mimeType = null,
                analysisFpsLimit = analysisFpsLimit.coerceAtLeast(1),
                decodedFrameCount = 0,
                skippedFrameCount = 0
            ),
            frames = emptyList()
        ),
        poses = emptyList()
    )
}
