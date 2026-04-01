package com.ddgo.app.feature.debug

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.media.Image
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OptimizedPrePoseVideoAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var lastTimestampMs: Long = -1L
    private var lastCaptureTimeMs: Long = -PRE_POSE_CAPTURE_INTERVAL_MS

    suspend operator fun invoke(
        videoUri: String,
        analysisFpsLimit: Int = DEFAULT_ANALYSIS_FPS_LIMIT,
        useGpuAcceleration: Boolean = true,
        onProgress: (Float) -> Unit = {}
    ): Result<List<DebugPoseFrameResult>> = withContext(Dispatchers.IO) {
        runCatching {
            lastTimestampMs = -1L
            lastCaptureTimeMs = -PRE_POSE_CAPTURE_INTERVAL_MS

            val safeUri = prepareSafeUri(videoUri)
            try {
                analyzeInternal(safeUri, analysisFpsLimit, useGpuAcceleration, onProgress)
            } finally {
                cleanupSafeUri(safeUri)
            }
        }
    }

    private fun prepareSafeUri(videoUri: String): Uri {
        val uri = Uri.parse(videoUri)
        if (uri.scheme == "file") return uri

        return try {
            val tempFile = File(
                context.cacheDir,
                "pre_pose_temp_${System.currentTimeMillis()}.mp4"
            )
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "Temporary analysis file created: ${tempFile.absolutePath}")
            Uri.fromFile(tempFile)
        } catch (error: Exception) {
            Log.w(TAG, "Failed to create temporary file, falling back to source URI", error)
            uri
        }
    }

    private fun cleanupSafeUri(uri: Uri) {
        val path = uri.path ?: return
        if (uri.scheme == "file" && path.contains("pre_pose_temp_")) {
            runCatching { File(path).delete() }
                .onFailure { Log.w(TAG, "Failed to delete temp file: $path", it) }
        }
    }

    private fun analyzeInternal(
        uri: Uri,
        analysisFpsLimit: Int,
        useGpuAcceleration: Boolean,
        onProgress: (Float) -> Unit
    ): List<DebugPoseFrameResult> {
        val poseLandmarker = createDebugPoseLandmarker(
            context = context,
            useGpuAcceleration = useGpuAcceleration,
            logTag = TAG
        )
        try {
            return analyzeWithSurface(uri, poseLandmarker, analysisFpsLimit, onProgress)
        } finally {
            poseLandmarker.close()
        }
    }

    private fun analyzeWithSurface(
        uri: Uri,
        poseLandmarker: PoseLandmarker,
        analysisFpsLimit: Int,
        onProgress: (Float) -> Unit
    ): List<DebugPoseFrameResult> {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var handlerThread: HandlerThread? = null
        var imageReader: ImageReader? = null
        var eglWindow: EglWindow? = null
        var textureRenderer: TextureRenderer? = null
        var surfaceTexture: SurfaceTexture? = null
        var decoderSurface: Surface? = null

        try {
            if (!setExtractorDataSource(extractor, uri)) {
                throw IllegalStateException("Could not open the selected video.")
            }

            val videoTrackIndex = findVideoTrackIndex(extractor)
            if (videoTrackIndex == -1) return emptyList()

            extractor.selectTrack(videoTrackIndex)
            val trackFormat = extractor.getTrackFormat(videoTrackIndex)
            val mimeType = trackFormat.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalStateException("Missing video mime type.")
            val durationUs = trackFormat.getLongOrZero(MediaFormat.KEY_DURATION)
            val width = trackFormat.getIntOrZero(MediaFormat.KEY_WIDTH)
            val height = trackFormat.getIntOrZero(MediaFormat.KEY_HEIGHT)
            val rotationDegrees = trackFormat.getIntOrZero(MediaFormat.KEY_ROTATION)
            val sourceFrameRate = trackFormat.getIntOrNull(MediaFormat.KEY_FRAME_RATE)

            val isRotated = rotationDegrees == 90 || rotationDegrees == 270
            val visualWidth = if (isRotated) height else width
            val visualHeight = if (isRotated) width else height
            val maxDim = max(visualWidth, visualHeight)
            val scale = if (maxDim > MAX_INFERENCE_DIM) {
                MAX_INFERENCE_DIM.toFloat() / maxDim.toFloat()
            } else {
                1f
            }
            val targetWidth = (visualWidth * scale).toInt().coerceAtLeast(1)
            val targetHeight = (visualHeight * scale).toInt().coerceAtLeast(1)
            val normalizedAnalysisFpsLimit = analysisFpsLimit.coerceAtLeast(1)
            val minProcessFrameGapUs = 1_000_000L / normalizedAnalysisFpsLimit

            Log.d(
                TAG,
                "Video setup: ${width}x$height -> ${targetWidth}x$targetHeight, " +
                    "rotation=$rotationDegrees, fps=${sourceFrameRate ?: "unknown"}, " +
                    "targetAnalysisFps=$normalizedAnalysisFpsLimit, minGapUs=$minProcessFrameGapUs"
            )

            handlerThread = HandlerThread("PrePoseSurfaceTexture").apply { start() }
            val handler = Handler(handlerThread.looper)

            imageReader = ImageReader.newInstance(
                targetWidth,
                targetHeight,
                PixelFormat.RGBA_8888,
                2
            )

            eglWindow = EglWindow(imageReader.surface)
            eglWindow.makeCurrent()
            GLES20.glViewport(0, 0, targetWidth, targetHeight)

            textureRenderer = TextureRenderer()
            surfaceTexture = SurfaceTexture(textureRenderer.textureId)
            decoderSurface = Surface(surfaceTexture)

            val frameSyncObject = Object()
            var frameAvailable = false

            surfaceTexture.setOnFrameAvailableListener(
                {
                    synchronized(frameSyncObject) {
                        frameAvailable = true
                        frameSyncObject.notifyAll()
                    }
                },
                handler
            )

            decoder = MediaCodec.createDecoderByType(mimeType)
            decoder.configure(trackFormat, decoderSurface, null, 0)
            decoder.start()

            return decodeLoop(
                extractor = extractor,
                decoder = decoder,
                imageReader = imageReader,
                poseLandmarker = poseLandmarker,
                durationUs = durationUs,
                rotationDegrees = rotationDegrees,
                onProgress = onProgress,
                targetWidth = targetWidth,
                targetHeight = targetHeight,
                minProcessFrameGapUs = minProcessFrameGapUs,
                frameSyncObject = frameSyncObject,
                isFrameAvailable = { frameAvailable },
                setFrameAvailable = { frameAvailable = it },
                surfaceTexture = surfaceTexture,
                textureRenderer = textureRenderer,
                eglWindow = eglWindow
            )
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            decoderSurface?.release()
            surfaceTexture?.release()
            textureRenderer?.release()
            eglWindow?.release()
            imageReader?.close()
            handlerThread?.quitSafely()
            extractor.release()
        }
    }

    private fun decodeLoop(
        extractor: MediaExtractor,
        decoder: MediaCodec,
        imageReader: ImageReader,
        poseLandmarker: PoseLandmarker,
        durationUs: Long,
        rotationDegrees: Int,
        onProgress: (Float) -> Unit,
        targetWidth: Int,
        targetHeight: Int,
        minProcessFrameGapUs: Long,
        frameSyncObject: Object,
        isFrameAvailable: () -> Boolean,
        setFrameAvailable: (Boolean) -> Unit,
        surfaceTexture: SurfaceTexture,
        textureRenderer: TextureRenderer,
        eglWindow: EglWindow
    ): List<DebugPoseFrameResult> {
        val bufferInfo = MediaCodec.BufferInfo()
        val poses = ArrayList<DebugPoseFrameResult>()
        val bitmapExtractor = RgbaBitmapExtractor(targetWidth, targetHeight)
        val imageOptions = ImageProcessingOptions.builder()
            .setRotationDegrees(0)
            .build()
        val texMatrix = FloatArray(16)
        val mvpMatrix = FloatArray(16)
        android.opengl.Matrix.setIdentityM(mvpMatrix, 0)

        if (rotationDegrees != 0) {
            android.opengl.Matrix.rotateM(
                mvpMatrix,
                0,
                -rotationDegrees.toFloat() + 90f,
                0f,
                0f,
                1f
            )
        }

        var inputEnded = false
        var outputEnded = false
        var processedFrameCount = 0
        var skippedFrameCount = 0
        var lastProcessedPresentationTimeUs = Long.MIN_VALUE

        while (!outputEnded) {
            if (!inputEnded) {
                val inputIndex = decoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                if (inputIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputIndex)
                        ?: throw IllegalStateException("Failed to obtain decoder input buffer.")
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(
                            inputIndex,
                            0,
                            0,
                            0L,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        inputEnded = true
                    } else {
                        decoder.queueInputBuffer(
                            inputIndex,
                            0,
                            sampleSize,
                            extractor.sampleTime,
                            extractor.sampleFlags
                        )
                        extractor.advance()
                    }
                }
            }

            when (val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    Log.d(TAG, "Decoder output format changed: ${decoder.outputFormat}")
                }

                else -> {
                    if (outputIndex < 0) continue

                    val isEndOfStream =
                        bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    val isCodecConfig =
                        bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0

                    if (!isCodecConfig && bufferInfo.presentationTimeUs >= 0L) {
                        if (durationUs > 0L) {
                            onProgress(bufferInfo.presentationTimeUs.toFloat() / durationUs.toFloat())
                        }

                        decoder.releaseOutputBuffer(outputIndex, true)

                        if (
                            !awaitFrame(
                                frameSyncObject = frameSyncObject,
                                isFrameAvailable = isFrameAvailable,
                                setFrameAvailable = setFrameAvailable
                            )
                        ) {
                            Log.w(TAG, "Timed out waiting for SurfaceTexture frame.")
                            if (isEndOfStream) {
                                outputEnded = true
                                onProgress(1f)
                            }
                            continue
                        }

                        surfaceTexture.updateTexImage()
                        val currentTimestampMs = normalizeTimestampMs(bufferInfo.presentationTimeUs)

                        if (
                            lastProcessedPresentationTimeUs != Long.MIN_VALUE &&
                            bufferInfo.presentationTimeUs - lastProcessedPresentationTimeUs <
                                minProcessFrameGapUs
                        ) {
                            skippedFrameCount++
                        } else {
                            lastProcessedPresentationTimeUs = bufferInfo.presentationTimeUs
                            surfaceTexture.getTransformMatrix(texMatrix)
                            textureRenderer.draw(texMatrix, mvpMatrix)
                            eglWindow.swapBuffers()

                            val image = imageReader.acquireLatestImage()
                            if (image != null) {
                                var frameBitmap: Bitmap? = null
                                try {
                                    frameBitmap = bitmapExtractor.copyToBitmap(image)
                                    val detection = detectDebugPoseFrame(
                                        poseLandmarker = poseLandmarker,
                                        frameBitmap = frameBitmap,
                                        frameTimeMs = currentTimestampMs,
                                        lastCaptureTimeMs = lastCaptureTimeMs,
                                        imageOptions = imageOptions
                                    )
                                    lastCaptureTimeMs = detection.updatedCaptureTimeMs
                                    processedFrameCount++
                                    detection.frameResult?.let(poses::add)
                                } catch (error: Exception) {
                                    Log.e(
                                        TAG,
                                        "Pose inference failed at ${bufferInfo.presentationTimeUs / 1_000L}ms",
                                        error
                                    )
                                } finally {
                                    frameBitmap?.takeIf { !it.isRecycled }?.recycle()
                                    image.close()
                                }
                            }
                        }
                    } else {
                        decoder.releaseOutputBuffer(outputIndex, false)
                    }

                    if (isEndOfStream) {
                        outputEnded = true
                        onProgress(1f)
                    }
                }
            }
        }

        Log.d(
            TAG,
            "Analysis complete: poses=${poses.size}, processed=$processedFrameCount, skipped=$skippedFrameCount"
        )
        return poses
    }

    private fun awaitFrame(
        frameSyncObject: Object,
        isFrameAvailable: () -> Boolean,
        setFrameAvailable: (Boolean) -> Unit
    ): Boolean {
        synchronized(frameSyncObject) {
            val deadline = System.currentTimeMillis() + FRAME_WAIT_TIMEOUT_MS
            while (!isFrameAvailable()) {
                val waitTime = deadline - System.currentTimeMillis()
                if (waitTime <= 0L) break
                frameSyncObject.wait(waitTime)
            }

            if (!isFrameAvailable()) {
                return false
            }

            setFrameAvailable(false)
            return true
        }
    }

    private fun normalizeTimestampMs(presentationTimeUs: Long): Long {
        var timestampMs = presentationTimeUs / 1_000L
        if (timestampMs <= lastTimestampMs) {
            timestampMs = lastTimestampMs + 1L
        }
        lastTimestampMs = timestampMs
        return timestampMs
    }

    private fun findVideoTrackIndex(extractor: MediaExtractor): Int {
        for (index in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("video/") == true) {
                return index
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
                context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                    extractor.setDataSource(descriptor.fileDescriptor)
                } ?: return false
            }
            true
        } catch (error: Exception) {
            Log.e(TAG, "Failed to set video data source.", error)
            false
        }
    }

    private class EglWindow(surface: Surface) {
        private val eglDisplay: EGLDisplay
        private val eglContext: EGLContext
        private val eglSurface: EGLSurface

        init {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val version = IntArray(2)
            EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

            val configAttributes = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(
                eglDisplay,
                configAttributes,
                0,
                configs,
                0,
                configs.size,
                numConfigs,
                0
            )

            val contextAttributes = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )
            eglContext = EGL14.eglCreateContext(
                eglDisplay,
                configs[0],
                EGL14.EGL_NO_CONTEXT,
                contextAttributes,
                0
            )

            val surfaceAttributes = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreateWindowSurface(
                eglDisplay,
                configs[0],
                surface,
                surfaceAttributes,
                0
            )
        }

        fun makeCurrent() {
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        }

        fun swapBuffers() {
            EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        }

        fun release() {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(
                    eglDisplay,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT
                )
                if (eglSurface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(eglDisplay, eglSurface)
                }
                if (eglContext != EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(eglDisplay, eglContext)
                }
                EGL14.eglTerminate(eglDisplay)
            }
        }
    }

    private class TextureRenderer {
        private val program: Int
        private val positionHandle: Int
        private val texCoordHandle: Int
        private val texMatrixHandle: Int
        private val mvpMatrixHandle: Int
        private val vertexBuffer = ByteBuffer
            .allocateDirect(VERTICES.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(VERTICES)
                position(0)
            }
        private val texCoordBuffer = ByteBuffer
            .allocateDirect(TEX_COORDS.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(TEX_COORDS)
                position(0)
            }
        val textureId: Int

        init {
            val vertexShaderSource = """
                attribute vec4 aPosition;
                attribute vec4 aTexCoord;
                varying vec2 vTexCoord;
                uniform mat4 uTexMatrix;
                uniform mat4 uMvpMatrix;
                void main() {
                    gl_Position = uMvpMatrix * aPosition;
                    vTexCoord = (uTexMatrix * aTexCoord).xy;
                }
            """.trimIndent()

            val fragmentShaderSource = """
                #extension GL_OES_EGL_image_external : require
                precision mediump float;
                varying vec2 vTexCoord;
                uniform samplerExternalOES sTexture;
                void main() {
                    gl_FragColor = texture2D(sTexture, vTexCoord);
                }
            """.trimIndent()

            val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderSource)
            val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderSource)

            program = GLES20.glCreateProgram()
            GLES20.glAttachShader(program, vertexShader)
            GLES20.glAttachShader(program, fragmentShader)
            GLES20.glLinkProgram(program)

            positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
            texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
            texMatrixHandle = GLES20.glGetUniformLocation(program, "uTexMatrix")
            mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMvpMatrix")

            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            textureId = textures[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glTexParameterf(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR.toFloat()
            )
            GLES20.glTexParameterf(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR.toFloat()
            )
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE
            )
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE
            )
        }

        fun draw(texMatrix: FloatArray, mvpMatrix: FloatArray) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glUseProgram(program)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

            vertexBuffer.position(0)
            texCoordBuffer.position(0)

            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(
                positionHandle,
                2,
                GLES20.GL_FLOAT,
                false,
                0,
                vertexBuffer
            )

            GLES20.glEnableVertexAttribArray(texCoordHandle)
            GLES20.glVertexAttribPointer(
                texCoordHandle,
                2,
                GLES20.GL_FLOAT,
                false,
                0,
                texCoordBuffer
            )

            GLES20.glUniformMatrix4fv(texMatrixHandle, 1, false, texMatrix, 0)
            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(positionHandle)
            GLES20.glDisableVertexAttribArray(texCoordHandle)
        }

        fun release() {
            GLES20.glDeleteProgram(program)
            val textures = intArrayOf(textureId)
            GLES20.glDeleteTextures(1, textures, 0)
        }

        private fun loadShader(type: Int, shaderCode: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
            return shader
        }

        companion object {
            private val VERTICES = floatArrayOf(
                -1f, -1f,
                 1f, -1f,
                -1f,  1f,
                 1f,  1f
            )
            private val TEX_COORDS = floatArrayOf(
                0f, 0f,
                1f, 0f,
                0f, 1f,
                1f, 1f
            )
        }
    }

    private class RgbaBitmapExtractor(
        private val width: Int,
        private val height: Int
    ) {
        private var rowBuffer = ByteArray(width * PIXEL_STRIDE)
        private val contiguousBuffer = ByteBuffer
            .allocateDirect(width * height * PIXEL_STRIDE)
            .order(ByteOrder.nativeOrder())

        fun copyToBitmap(image: Image): Bitmap {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val plane = image.planes[0]
            val buffer = plane.buffer
            val rowStride = plane.rowStride

            buffer.rewind()
            if (rowStride == width * PIXEL_STRIDE) {
                bitmap.copyPixelsFromBuffer(buffer)
                return bitmap
            }

            if (rowBuffer.size < rowStride) {
                rowBuffer = ByteArray(rowStride)
            }

            contiguousBuffer.clear()
            for (y in 0 until height) {
                buffer.position(y * rowStride)
                buffer.get(rowBuffer, 0, rowStride)
                contiguousBuffer.put(rowBuffer, 0, width * PIXEL_STRIDE)
            }
            contiguousBuffer.rewind()
            bitmap.copyPixelsFromBuffer(contiguousBuffer)
            return bitmap
        }
    }

    private fun MediaFormat.getIntOrZero(key: String): Int =
        if (containsKey(key)) getInteger(key) else 0

    private fun MediaFormat.getIntOrNull(key: String): Int? =
        if (containsKey(key)) getInteger(key) else null

    private fun MediaFormat.getLongOrZero(key: String): Long =
        if (containsKey(key)) getLong(key) else 0L

    companion object {
        private const val TAG = "OptimizedPrePose"
        // pose_detector.tflite input: 224x224
        // pose_landmarks_detector.tflite input: 256x256
        // Pre-scaling the long edge to 256 keeps us close to the actual task input sizes
        // and avoids paying copy/resize cost on larger intermediate frames.
        private const val MAX_INFERENCE_DIM = 256
        private const val DEFAULT_ANALYSIS_FPS_LIMIT = 30
        private const val DEQUEUE_TIMEOUT_US = 10_000L
        private const val FRAME_WAIT_TIMEOUT_MS = 500L
        private const val PIXEL_STRIDE = 4
    }
}
