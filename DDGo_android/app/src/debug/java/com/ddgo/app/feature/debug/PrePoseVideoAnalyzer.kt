// [DEBUG ONLY] 이 파일은 비디오 사전 분석(Pre-Pose)의 표준 구현체(디버그용)입니다.
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
 * 모든 프레임을 순차 디코딩해 MediaPipe Pose 결과를 반환하는 분석기입니다.
 * 지연 없는 오버레이를 위해 최대한 많은 프레임을 분석합니다.
 */
class PrePoseVideoAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var lastCaptureTimeMs: Long = -5_000

    suspend operator fun invoke(
        videoUri: String,
        onProgress: (Float) -> Unit = {}
    ): Result<List<DebugPoseFrameResult>> = withContext(Dispatchers.IO) {
        runCatching { analyzeInternal(videoUri, onProgress) }
    }

    private fun analyzeInternal(
        videoUri: String,
        onProgress: (Float) -> Unit
    ): List<DebugPoseFrameResult> {
        lastCaptureTimeMs = -5_000
        val uri = Uri.parse(videoUri)
        val poseLandmarker = createPoseLandmarker()

        try {
            return analyzeSequentialFrames(
                uri = uri,
                poseLandmarker = poseLandmarker,
                onProgress = onProgress
            )
        } finally {
            poseLandmarker.close()
        }
    }

    private fun analyzeSequentialFrames(
        uri: Uri,
        poseLandmarker: PoseLandmarker,
        onProgress: (Float) -> Unit
    ): List<DebugPoseFrameResult> {
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
            val rotationDegrees = trackFormat.readRotationDegrees()
            val width = trackFormat.readDimension(MediaFormat.KEY_WIDTH)
            val height = trackFormat.readDimension(MediaFormat.KEY_HEIGHT)
            val frameRate = trackFormat.readFrameRateOrNull()
            val decoder = createVideoDecoder(mimeType, trackFormat)

            try {
                return decodeEveryFrame(
                    extractor = extractor,
                    decoder = decoder,
                    poseLandmarker = poseLandmarker,
                    rotationDegrees = rotationDegrees,
                    durationUs = durationUs,
                    width = width,
                    height = height,
                    frameRate = frameRate,
                    onProgress = onProgress
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

    private fun decodeEveryFrame(
        extractor: MediaExtractor,
        decoder: MediaCodec,
        poseLandmarker: PoseLandmarker,
        rotationDegrees: Int,
        durationUs: Long,
        width: Int,
        height: Int,
        frameRate: Int?,
        onProgress: (Float) -> Unit
    ): List<DebugPoseFrameResult> {
        val bufferInfo = MediaCodec.BufferInfo()
        val poses = ArrayList<DebugPoseFrameResult>()
        var inputEnded = false
        var outputEnded = false
        var decodedFrameCount = 0

        Log.d(
            TAG,
            "Pre-Pose 모든 프레임 디코딩 시작: size=${width}x$height, fps=${frameRate ?: "unknown"}, durationUs=$durationUs, rotation=$rotationDegrees"
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
                        
                        // 진행률 업데이트
                        if (durationUs > 0) {
                            onProgress(presentationTimeUs.toFloat() / durationUs.toFloat())
                        }

                        runCatching {
                            inferPoseFromDecodedFrame(
                                decoder = decoder,
                                outputBufferIndex = outputBufferIndex,
                                poseLandmarker = poseLandmarker,
                                presentationTimeUs = presentationTimeUs,
                                rotationDegrees = rotationDegrees
                            )
                        }.onFailure { error ->
                            Log.e(TAG, "ptsUs=$presentationTimeUs pose=inference_error", error)
                        }.getOrNull()?.let(poses::add)
                    }

                    decoder.releaseOutputBuffer(outputBufferIndex, false)

                    if (isEndOfStream) {
                        outputEnded = true
                        onProgress(1.0f)
                    }
                }
            }
        }

        Log.d(
            TAG,
            "Pre-Pose 디코딩 완료: decoded=$decodedFrameCount, detected=${poses.size}"
        )

        return poses
    }

    private fun inferPoseFromDecodedFrame(
        decoder: MediaCodec,
        outputBufferIndex: Int,
        poseLandmarker: PoseLandmarker,
        presentationTimeUs: Long,
        rotationDegrees: Int
    ): DebugPoseFrameResult? {
        val image = decoder.getOutputImage(outputBufferIndex) ?: return null

        try {
            val rawBitmap = image.toBitmap()
            val preparedBitmap = rawBitmap.prepareForInference(rotationDegrees)

            try {
                return inferPose(
                    poseLandmarker = poseLandmarker,
                    frameBitmap = preparedBitmap,
                    frameTimeMs = presentationTimeUs / 1_000L
                )
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
        frameTimeMs: Long
    ): DebugPoseFrameResult? {
        val mpImage = BitmapImageBuilder(frameBitmap).build()

        try {
            val result = poseLandmarker.detectForVideo(mpImage, frameTimeMs)
            val landmarks = result.landmarks().firstOrNull().orEmpty()
            val worldLandmarks = result.worldLandmarks().firstOrNull().orEmpty()
            
            val pose = VisionMapper.toPose(
                frameTimeMs = result.timestampMs(),
                rawLandmarks = landmarks.map { landmark ->
                    Triple(landmark.x(), landmark.y(), landmark.z())
                }
            )

            // 5초 간격으로 이미지 캡처 (디버깅용)
            val currentTimestampMs = result.timestampMs()
            val shouldCapture = currentTimestampMs >= lastCaptureTimeMs + 5_000
            val capturedBitmap = if (shouldCapture) {
                lastCaptureTimeMs = (currentTimestampMs / 5000) * 5000
                Bitmap.createBitmap(frameBitmap)
            } else null

            if (landmarks.isNotEmpty() || capturedBitmap != null) {
                return DebugPoseFrameResult(
                    pose = pose,
                    worldLandmarks = worldLandmarks.mapIndexed { index, landmark ->
                        DebugPoseWorldLandmark(
                            index = index,
                            x = landmark.x(),
                            y = landmark.y(),
                            z = landmark.z()
                        )
                    },
                    capturedBitmap = capturedBitmap
                )
            }
            return null
        } finally {
            mpImage.close()
        }
    }

    private fun createPoseLandmarker(): PoseLandmarker {
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

        return PoseLandmarker.createFromOptions(context, options)
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
        } catch (error: Exception) {
            false
        }
    }

    private fun MediaFormat.readDurationUs(): Long = if (containsKey(MediaFormat.KEY_DURATION)) getLong(MediaFormat.KEY_DURATION) else 0L
    private fun MediaFormat.readRotationDegrees(): Int = if (containsKey(MediaFormat.KEY_ROTATION)) getInteger(MediaFormat.KEY_ROTATION) else 0
    private fun MediaFormat.readDimension(key: String): Int = if (containsKey(key)) getInteger(key) else 0
    private fun MediaFormat.readFrameRateOrNull(): Int? = if (containsKey(MediaFormat.KEY_FRAME_RATE)) getInteger(MediaFormat.KEY_FRAME_RATE) else null

    private fun Image.toBitmap(): Bitmap {
        val cropWidth = cropRect.width()
        val cropHeight = cropRect.height()
        val nv21Bytes = toNv21Bytes()
        val yuvImage = YuvImage(nv21Bytes, ImageFormat.NV21, cropWidth, cropHeight, null)
        val jpegStream = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, cropWidth, cropHeight), 90, jpegStream)
        val jpegBytes = jpegStream.toByteArray()
        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            ?: throw IllegalStateException("Bitmap decode failed")
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
                val length = if (pixelStride == 1 && outputStride == 1) planeWidth else (planeWidth - 1) * pixelStride + 1
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
                if (row < planeHeight - 1) buffer.position(buffer.position() + rowStride - length)
            }
        }
        return output
    }

    private fun Bitmap.prepareForInference(rotationDegrees: Int): Bitmap {
        val rotatedBitmap = if (rotationDegrees == 0) {
            this
        } else {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
        }

        val maxDimension = max(rotatedBitmap.width, rotatedBitmap.height)
        if (maxDimension <= MAX_INFERENCE_DIMENSION_PX) return rotatedBitmap

        val scale = MAX_INFERENCE_DIMENSION_PX.toFloat() / maxDimension.toFloat()
        val scaledWidth = (rotatedBitmap.width * scale).roundToInt().coerceAtLeast(1)
        val scaledHeight = (rotatedBitmap.height * scale).roundToInt().coerceAtLeast(1)
        val scaledBitmap = Bitmap.createScaledBitmap(rotatedBitmap, scaledWidth, scaledHeight, true)
        if (rotatedBitmap !== this) rotatedBitmap.recycle()
        return scaledBitmap
    }

    companion object {
        private const val TAG = "PrePoseVideoAnalyzer"
        private const val POSE_MODEL_PATH = "models/pose_landmarker_lite.task"
        private const val DEQUEUE_TIMEOUT_US = 10_000L
        private const val MAX_INFERENCE_DIMENSION_PX = 640 // 분석 속도를 위해 640으로 조정
    }
}
