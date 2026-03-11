package com.ddgo.app.feature.debug

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
import com.ddgo.app.data.mapper.VisionMapper
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.model.PoseLandmark
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 디버그 화면에서 선택한 비디오를 순차 디코딩해 MediaPipe Pose 결과를 반환합니다.
 *
 * 핵심 포인트:
 * - Python의 `cap.read()`처럼 처음부터 끝까지 프레임을 순서대로 읽습니다.
 * - 압축 샘플 PTS를 따로 모은 뒤 다시 seek 하지 않고, 디코더가 실제로 출력한 프레임만 처리합니다.
 * - 일부 코덱/컨테이너에서 첫 키프레임으로 반복 스냅되는 문제를 피합니다.
 */
class DebugPoseVideoAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend operator fun invoke(videoUri: String): Result<List<Pose>> = withContext(Dispatchers.IO) {
        runCatching { analyzeInternal(videoUri) }
    }

    private fun analyzeInternal(videoUri: String): List<Pose> {
        val uri = Uri.parse(videoUri)
        val poseLandmarker = createPoseLandmarker()

        try {
            return analyzeSequentialFrames(
                uri = uri,
                poseLandmarker = poseLandmarker
            )
        } finally {
            poseLandmarker.close()
        }
    }

    private fun analyzeSequentialFrames(
        uri: Uri,
        poseLandmarker: PoseLandmarker
    ): List<Pose> {
        val extractor = MediaExtractor()

        try {
            require(setExtractorDataSource(extractor, uri)) {
                "선택한 비디오를 열 수 없습니다."
            }

            val videoTrackIndex = findVideoTrackIndex(extractor)
            if (videoTrackIndex == -1) {
                Log.w(TAG, "비디오 트랙을 찾지 못했습니다.")
                return emptyList()
            }

            extractor.selectTrack(videoTrackIndex)
            val trackFormat = extractor.getTrackFormat(videoTrackIndex)
            val mimeType = trackFormat.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalStateException("비디오 MIME 정보를 읽을 수 없습니다.")
            val durationUs = trackFormat.readDurationUs()
            val sampleStepUs = resolveSampleStepUs(durationUs)
            val rotationDegrees = trackFormat.readRotationDegrees()
            val width = trackFormat.readDimension(MediaFormat.KEY_WIDTH)
            val height = trackFormat.readDimension(MediaFormat.KEY_HEIGHT)
            val frameRate = trackFormat.readFrameRateOrNull()
            val decoder = createVideoDecoder(mimeType, trackFormat)

            try {
                return decodeFramesSequentially(
                    extractor = extractor,
                    decoder = decoder,
                    poseLandmarker = poseLandmarker,
                    sampleStepUs = sampleStepUs,
                    rotationDegrees = rotationDegrees,
                    durationUs = durationUs,
                    width = width,
                    height = height,
                    frameRate = frameRate
                )
            } finally {
                runCatching { decoder.stop() }
                    .onFailure { error -> Log.w(TAG, "디코더 stop 실패", error) }
                decoder.release()
            }
        } finally {
            extractor.release()
        }
    }

    private fun decodeFramesSequentially(
        extractor: MediaExtractor,
        decoder: MediaCodec,
        poseLandmarker: PoseLandmarker,
        sampleStepUs: Long,
        rotationDegrees: Int,
        durationUs: Long,
        width: Int,
        height: Int,
        frameRate: Int?
    ): List<Pose> {
        val bufferInfo = MediaCodec.BufferInfo()
        val poses = ArrayList<Pose>()
        var inputEnded = false
        var outputEnded = false
        var lastProcessedUs = Long.MIN_VALUE
        var decodedFrameCount = 0
        var sampledFrameCount = 0

        Log.d(
            TAG,
            "Pose 순차 디코딩 시작: size=${width}x$height, fps=${frameRate ?: "unknown"}, stepUs=$sampleStepUs, durationUs=$durationUs, rotation=$rotationDegrees"
        )

        while (!outputEnded) {
            if (!inputEnded) {
                val inputBufferIndex = decoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputBufferIndex)
                        ?: throw IllegalStateException("디코더 입력 버퍼를 가져오지 못했습니다.")

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
                    Log.d(TAG, "디코더 출력 포맷 변경: ${decoder.outputFormat}")
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

                        val shouldSample = lastProcessedUs == Long.MIN_VALUE ||
                            presentationTimeUs - lastProcessedUs >= sampleStepUs ||
                            isEndOfStream

                        if (shouldSample) {
                            sampledFrameCount++
                            val frameLabel =
                                "sample[$sampledFrameCount] decoded[$decodedFrameCount] ptsUs=$presentationTimeUs ptsMs=${presentationTimeUs / 1_000L}"
                            Log.d(TAG, "$frameLabel frame=selected")

                            runCatching {
                                inferPoseFromDecodedFrame(
                                    decoder = decoder,
                                    outputBufferIndex = outputBufferIndex,
                                    poseLandmarker = poseLandmarker,
                                    presentationTimeUs = presentationTimeUs,
                                    rotationDegrees = rotationDegrees,
                                    frameLabel = frameLabel
                                )
                            }.onFailure { error ->
                                Log.e(TAG, "$frameLabel pose=inference_error", error)
                            }.getOrNull()?.let(poses::add)
                            lastProcessedUs = presentationTimeUs
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
            "Pose 순차 디코딩 완료: decoded=$decodedFrameCount, sampled=$sampledFrameCount, detected=${poses.size}"
        )

        return poses
    }

    private fun inferPoseFromDecodedFrame(
        decoder: MediaCodec,
        outputBufferIndex: Int,
        poseLandmarker: PoseLandmarker,
        presentationTimeUs: Long,
        rotationDegrees: Int,
        frameLabel: String
    ): Pose? {
        val image = decoder.getOutputImage(outputBufferIndex)
        if (image == null) {
            Log.w(TAG, "$frameLabel frame=image_unavailable")
            return null
        }

        try {
            val rawBitmap = image.toBitmap()
            val preparedBitmap = rawBitmap.prepareForInference(rotationDegrees)
            Log.d(
                TAG,
                "$frameLabel frame=decoded rawBitmap=${rawBitmap.width}x${rawBitmap.height} preparedBitmap=${preparedBitmap.width}x${preparedBitmap.height} rotation=$rotationDegrees"
            )

            try {
                return inferPose(
                    poseLandmarker = poseLandmarker,
                    frameBitmap = preparedBitmap,
                    frameTimeMs = presentationTimeUs / 1_000L,
                    frameLabel = frameLabel
                ).also { pose ->
                    logPoseResult(frameLabel, pose)
                }
            } finally {
                if (preparedBitmap !== rawBitmap) {
                    preparedBitmap.recycle()
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
        frameLabel: String
    ): Pose? {
        val mpImage = BitmapImageBuilder(frameBitmap).build()

        try {
            val result = poseLandmarker.detectForVideo(mpImage, frameTimeMs)
            val landmarks = result.landmarks().firstOrNull().orEmpty()
            if (landmarks.isEmpty()) {
                Log.w(TAG, "$frameLabel pose=not_detected timestampMs=${result.timestampMs()}")
                return null
            }

            return VisionMapper.toPose(
                frameTimeMs = result.timestampMs(),
                rawLandmarks = landmarks.map { landmark ->
                    Triple(landmark.x(), landmark.y(), landmark.z())
                }
            )
        } finally {
            mpImage.close()
        }
    }

    private fun logPoseResult(
        frameLabel: String,
        pose: Pose?
    ) {
        if (pose == null) return

        Log.i(
            TAG,
            "$frameLabel pose=detected landmarks=${pose.landmarks.size} keyJoints=${pose.formatKeyJoints()}"
        )

        pose.landmarks
            .chunked(LANDMARKS_PER_LOG_LINE)
            .forEachIndexed { chunkIndex, chunk ->
                Log.d(
                    TAG,
                    "$frameLabel joints[$chunkIndex]=${chunk.joinToString(separator = " | ") { it.toDebugString() }}"
                )
            }
    }

    private fun createPoseLandmarker(): PoseLandmarker {
        ensureModelAssetExists()

        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(POSE_MODEL_PATH)
            .build()
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.VIDEO)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(MIN_POSE_DETECTION_CONFIDENCE)
            .setMinPosePresenceConfidence(MIN_POSE_PRESENCE_CONFIDENCE)
            .setMinTrackingConfidence(MIN_TRACKING_CONFIDENCE)
            .build()

        return PoseLandmarker.createFromOptions(context, options)
    }

    private fun ensureModelAssetExists() {
        try {
            context.assets.open(POSE_MODEL_PATH).use { }
        } catch (error: FileNotFoundException) {
            throw error
        } catch (error: Exception) {
            throw IllegalStateException("MediaPipe Pose 모델 파일을 열 수 없습니다.", error)
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

    private fun resolveSampleStepUs(durationUs: Long): Long {
        if (durationUs <= 0L) return MIN_SAMPLE_STEP_US

        return (durationUs / TARGET_SAMPLE_COUNT)
            .coerceIn(MIN_SAMPLE_STEP_US, MAX_SAMPLE_STEP_US)
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
            if (uri.scheme == FILE_SCHEME) {
                extractor.setDataSource(uri.path ?: return false)
            } else {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    extractor.setDataSource(pfd.fileDescriptor)
                } ?: return false
            }
            true
        } catch (error: Exception) {
            Log.e(TAG, "MediaExtractor setDataSource 실패", error)
            false
        }
    }

    private fun MediaFormat.readDurationUs(): Long {
        return if (containsKey(MediaFormat.KEY_DURATION)) {
            getLong(MediaFormat.KEY_DURATION)
        } else {
            0L
        }
    }

    private fun MediaFormat.readRotationDegrees(): Int {
        return if (containsKey(MediaFormat.KEY_ROTATION)) {
            getInteger(MediaFormat.KEY_ROTATION)
        } else {
            0
        }
    }

    private fun MediaFormat.readDimension(key: String): Int {
        return if (containsKey(key)) {
            getInteger(key)
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

    private fun Image.toBitmap(): Bitmap {
        val cropWidth = cropRect.width()
        val cropHeight = cropRect.height()
        val nv21Bytes = toNv21Bytes()
        val yuvImage = YuvImage(nv21Bytes, ImageFormat.NV21, cropWidth, cropHeight, null)
        val jpegStream = ByteArrayOutputStream()

        yuvImage.compressToJpeg(Rect(0, 0, cropWidth, cropHeight), JPEG_QUALITY, jpegStream)
        val jpegBytes = jpegStream.toByteArray()

        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            ?: throw IllegalStateException("디코더 출력 프레임을 Bitmap으로 변환하지 못했습니다.")
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

    private fun Bitmap.prepareForInference(rotationDegrees: Int): Bitmap {
        val rotatedBitmap = if (rotationDegrees == 0) {
            this
        } else {
            val matrix = Matrix().apply {
                postRotate(rotationDegrees.toFloat())
            }
            Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
        }

        val maxDimension = max(rotatedBitmap.width, rotatedBitmap.height)
        if (maxDimension <= MAX_INFERENCE_DIMENSION_PX) {
            return rotatedBitmap
        }

        val scale = MAX_INFERENCE_DIMENSION_PX.toFloat() / maxDimension.toFloat()
        val scaledWidth = (rotatedBitmap.width * scale).roundToInt().coerceAtLeast(1)
        val scaledHeight = (rotatedBitmap.height * scale).roundToInt().coerceAtLeast(1)
        val scaledBitmap = Bitmap.createScaledBitmap(
            rotatedBitmap,
            scaledWidth,
            scaledHeight,
            true
        )

        if (rotatedBitmap !== this) {
            rotatedBitmap.recycle()
        }

        return scaledBitmap
    }

    private fun Pose.formatKeyJoints(): String {
        val landmarksByIndex = landmarks.associateBy { it.index }
        return KEY_LANDMARK_INDICES.joinToString(separator = ", ") { index ->
            landmarksByIndex[index]?.toDebugString() ?: "${landmarkName(index)}=missing"
        }
    }

    private fun PoseLandmark.toDebugString(): String {
        return "${landmarkName(index)}=(${formatCoordinate(x)},${formatCoordinate(y)},${formatCoordinate(z)})"
    }

    private fun landmarkName(index: Int): String {
        return LANDMARK_NAMES.getOrElse(index) { "joint$index" }
    }

    private fun formatCoordinate(value: Float): String {
        return String.format(java.util.Locale.US, "%.4f", value)
    }

    private companion object {
        private const val TAG = "DebugPoseVideoAnalyzer"
        private const val FILE_SCHEME = "file"
        private const val POSE_MODEL_PATH = "models/pose_landmarker_lite.task"

        private const val TARGET_SAMPLE_COUNT = 450L
        private const val MIN_SAMPLE_STEP_US = 100_000L
        private const val MAX_SAMPLE_STEP_US = 250_000L

        private const val MIN_POSE_DETECTION_CONFIDENCE = 0.5f
        private const val MIN_POSE_PRESENCE_CONFIDENCE = 0.5f
        private const val MIN_TRACKING_CONFIDENCE = 0.5f

        private const val DEQUEUE_TIMEOUT_US = 10_000L
        private const val JPEG_QUALITY = 90
        private const val MAX_INFERENCE_DIMENSION_PX = 1280
        private const val LANDMARKS_PER_LOG_LINE = 5

        private val KEY_LANDMARK_INDICES = listOf(0, 11, 12, 15, 16, 23, 24, 27, 28)
        private val LANDMARK_NAMES = listOf(
            "nose",
            "leftEyeInner",
            "leftEye",
            "leftEyeOuter",
            "rightEyeInner",
            "rightEye",
            "rightEyeOuter",
            "leftEar",
            "rightEar",
            "mouthLeft",
            "mouthRight",
            "leftShoulder",
            "rightShoulder",
            "leftElbow",
            "rightElbow",
            "leftWrist",
            "rightWrist",
            "leftPinky",
            "rightPinky",
            "leftIndex",
            "rightIndex",
            "leftThumb",
            "rightThumb",
            "leftHip",
            "rightHip",
            "leftKnee",
            "rightKnee",
            "leftAnkle",
            "rightAnkle",
            "leftHeel",
            "rightHeel",
            "leftFootIndex",
            "rightFootIndex"
        )
    }
}
