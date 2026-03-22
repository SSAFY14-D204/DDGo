package com.ddgo.app.data.ml.mediapipe

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
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.model.PrePoseVideoAnalysisResult
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

@Singleton
class UploadOptimizedPrePoseVideoAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var lastTimestampMs: Long = -1L

    suspend fun analyze(
        videoUri: String,
        analysisFpsLimit: Int
    ): PrePoseVideoAnalysisResult = withContext(Dispatchers.IO) {
        coroutineContext.ensureActive()
        lastTimestampMs = -1L

        val safeUri = prepareSafeUri(videoUri)
        try {
            analyzeInternal(
                sourceVideoUri = videoUri,
                decodeUri = safeUri,
                analysisFpsLimit = analysisFpsLimit,
                cancellationCheckpoint = { coroutineContext.ensureActive() }
            )
        } finally {
            cleanupSafeUri(safeUri)
        }
    }

    private fun analyzeInternal(
        sourceVideoUri: String,
        decodeUri: Uri,
        analysisFpsLimit: Int,
        cancellationCheckpoint: () -> Unit
    ): PrePoseVideoAnalysisResult {
        val poseLandmarker = createPoseLandmarker()
            ?: throw IllegalStateException("Optimized upload pre-pose landmarker is unavailable.")

        try {
            return analyzeWithSurface(
                sourceVideoUri = sourceVideoUri,
                decodeUri = decodeUri,
                poseLandmarker = poseLandmarker,
                analysisFpsLimit = analysisFpsLimit,
                cancellationCheckpoint = cancellationCheckpoint
            ).toPrePoseVideoAnalysisResult()
        } finally {
            poseLandmarker.close()
        }
    }

    private fun analyzeWithSurface(
        sourceVideoUri: String,
        decodeUri: Uri,
        poseLandmarker: PoseLandmarker,
        analysisFpsLimit: Int,
        cancellationCheckpoint: () -> Unit
    ): PoseSequenceAnalysisResult {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var handlerThread: HandlerThread? = null
        var imageReader: ImageReader? = null
        var eglWindow: EglWindow? = null
        var textureRenderer: TextureRenderer? = null
        var surfaceTexture: SurfaceTexture? = null
        var decoderSurface: Surface? = null

        try {
            require(setExtractorDataSource(extractor, decodeUri)) {
                "Could not open the selected video."
            }

            val videoTrackIndex = findVideoTrackIndex(extractor)
            if (videoTrackIndex == -1) {
                Log.w(TAG, "No video track found for optimized upload pre-pose.")
                return emptyPrePoseAnalysisResult(
                    videoUri = sourceVideoUri,
                    analysisFpsLimit = analysisFpsLimit,
                    generator = TAG
                )
            }

            extractor.selectTrack(videoTrackIndex)
            val trackFormat = extractor.getTrackFormat(videoTrackIndex)
            val mimeType = trackFormat.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalStateException("Missing video mime type.")
            val durationUs = trackFormat.getLongOrZero(MediaFormat.KEY_DURATION)
            val sourceFrameWidth = trackFormat.getIntOrZero(MediaFormat.KEY_WIDTH)
            val sourceFrameHeight = trackFormat.getIntOrZero(MediaFormat.KEY_HEIGHT)
            val rotationDegrees = trackFormat.getIntOrZero(MediaFormat.KEY_ROTATION)
            val frameRate = trackFormat.getIntOrNull(MediaFormat.KEY_FRAME_RATE)

            val isRotated = rotationDegrees == 90 || rotationDegrees == 270
            val referenceWidthPx = if (isRotated) sourceFrameHeight else sourceFrameWidth
            val referenceHeightPx = if (isRotated) sourceFrameWidth else sourceFrameHeight
            val maxDimension = max(referenceWidthPx, referenceHeightPx)
            val scale = if (maxDimension > MAX_INFERENCE_DIMENSION_PX) {
                MAX_INFERENCE_DIMENSION_PX.toFloat() / maxDimension.toFloat()
            } else {
                1f
            }
            val targetWidth = (referenceWidthPx * scale).toInt().coerceAtLeast(1)
            val targetHeight = (referenceHeightPx * scale).toInt().coerceAtLeast(1)
            val normalizedAnalysisFpsLimit = analysisFpsLimit.coerceAtLeast(1)
            val minProcessFrameGapUs = 1_000_000L / normalizedAnalysisFpsLimit

            handlerThread = HandlerThread("UploadPrePoseSurfaceTexture").apply { start() }
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
                sourceVideoUri = sourceVideoUri,
                mimeType = mimeType,
                durationUs = durationUs,
                rotationDegrees = rotationDegrees,
                sourceFrameWidth = sourceFrameWidth,
                sourceFrameHeight = sourceFrameHeight,
                referenceWidthPx = referenceWidthPx,
                referenceHeightPx = referenceHeightPx,
                frameRate = frameRate,
                analysisFpsLimit = normalizedAnalysisFpsLimit,
                minProcessFrameGapUs = minProcessFrameGapUs,
                frameSyncObject = frameSyncObject,
                isFrameAvailable = { frameAvailable },
                setFrameAvailable = { frameAvailable = it },
                surfaceTexture = surfaceTexture,
                textureRenderer = textureRenderer,
                eglWindow = eglWindow,
                cancellationCheckpoint = cancellationCheckpoint
            )
        } finally {
            runCatching { decoder?.stop() }
                .onFailure { error -> Log.w(TAG, "Failed to stop optimized decoder.", error) }
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
        sourceVideoUri: String,
        mimeType: String,
        durationUs: Long,
        rotationDegrees: Int,
        sourceFrameWidth: Int,
        sourceFrameHeight: Int,
        referenceWidthPx: Int,
        referenceHeightPx: Int,
        frameRate: Int?,
        analysisFpsLimit: Int,
        minProcessFrameGapUs: Long,
        frameSyncObject: Object,
        isFrameAvailable: () -> Boolean,
        setFrameAvailable: (Boolean) -> Unit,
        surfaceTexture: SurfaceTexture,
        textureRenderer: TextureRenderer,
        eglWindow: EglWindow,
        cancellationCheckpoint: () -> Unit
    ): PoseSequenceAnalysisResult {
        val bufferInfo = MediaCodec.BufferInfo()
        val poses = ArrayList<Pose>()
        val aiFrames = ArrayList<com.ddgo.app.domain.model.AiPoseFrame>()
        val bitmapExtractor = RgbaBitmapExtractor(imageReader.width, imageReader.height)
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
        var decodedFrameCount = 0
        var processedFrameCount = 0
        var skippedFrameCount = 0
        var lastProcessedPresentationTimeUs = Long.MIN_VALUE

        while (!outputEnded) {
            cancellationCheckpoint()

            if (!inputEnded) {
                val inputIndex = decoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                if (inputIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputIndex)
                        ?: throw IllegalStateException("Failed to obtain optimized decoder input buffer.")
                    inputBuffer.clear()
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
                    Log.d(TAG, "Optimized decoder output format changed: ${decoder.outputFormat}")
                }

                else -> {
                    if (outputIndex < 0) continue

                    val isEndOfStream =
                        bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    val isCodecConfig =
                        bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0

                    if (!isCodecConfig && bufferInfo.presentationTimeUs >= 0L) {
                        decodedFrameCount++
                        decoder.releaseOutputBuffer(outputIndex, true)

                        if (
                            !awaitFrame(
                                frameSyncObject = frameSyncObject,
                                isFrameAvailable = isFrameAvailable,
                                setFrameAvailable = setFrameAvailable
                            )
                        ) {
                            Log.w(TAG, "Timed out waiting for optimized SurfaceTexture frame.")
                            if (isEndOfStream) {
                                outputEnded = true
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
                            val currentFrameIndex = processedFrameCount
                            processedFrameCount++

                            surfaceTexture.getTransformMatrix(texMatrix)
                            textureRenderer.draw(texMatrix, mvpMatrix)
                            eglWindow.swapBuffers()

                            val image = imageReader.acquireLatestImage()
                            if (image != null) {
                                var frameBitmap: Bitmap? = null
                                try {
                                    frameBitmap = bitmapExtractor.copyToBitmap(image)
                                    runCatching {
                                        inferPoseCaptureFromBitmap(
                                            poseLandmarker = poseLandmarker,
                                            frameBitmap = frameBitmap,
                                            frameTimeMs = currentTimestampMs,
                                            frameIndex = currentFrameIndex,
                                            referenceWidthPx = referenceWidthPx,
                                            referenceHeightPx = referenceHeightPx
                                        )
                                    }.onFailure { error ->
                                        Log.e(
                                            TAG,
                                            "Optimized pose inference failed at ptsUs=${bufferInfo.presentationTimeUs}",
                                            error
                                        )
                                    }.getOrNull()?.let { capture ->
                                        aiFrames.add(capture.frame)
                                        capture.pose?.let(poses::add)
                                    }
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
                    }
                }
            }
        }

        Log.d(
            TAG,
            "Optimized upload pre-pose complete: decoded=$decodedFrameCount, processed=$processedFrameCount, skipped=$skippedFrameCount, poses=${poses.size}, durationUs=$durationUs"
        )

        return buildPoseSequenceAnalysisResult(
            sourceUri = Uri.parse(sourceVideoUri),
            generator = TAG,
            mimeType = mimeType,
            frameWidth = sourceFrameWidth,
            frameHeight = sourceFrameHeight,
            frameRate = frameRate,
            analysisFpsLimit = analysisFpsLimit,
            rotationDegrees = rotationDegrees,
            decodedFrameCount = decodedFrameCount,
            processedFrameCount = processedFrameCount,
            skippedFrameCount = skippedFrameCount,
            aiFrames = aiFrames,
            poses = poses
        )
    }

    private fun prepareSafeUri(videoUri: String): Uri {
        val uri = Uri.parse(videoUri)
        if (uri.scheme == "file") return uri

        return try {
            val tempFile = File(
                context.cacheDir,
                "upload_pre_pose_optimized_${System.currentTimeMillis()}.mp4"
            )
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Uri.fromFile(tempFile)
        } catch (error: Exception) {
            Log.w(TAG, "Failed to create temp upload pre-pose file. Using source URI directly.", error)
            uri
        }
    }

    private fun cleanupSafeUri(uri: Uri) {
        val path = uri.path ?: return
        if (uri.scheme == "file" && path.contains("upload_pre_pose_optimized_")) {
            runCatching { File(path).delete() }
                .onFailure { Log.w(TAG, "Failed to delete temp upload pre-pose file: $path", it) }
        }
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

    private fun createPoseLandmarker(): PoseLandmarker? {
        return try {
            runCatching { createPoseLandmarker(delegate = Delegate.GPU) }
                .onFailure { error ->
                    Log.w(TAG, "GPU delegate unavailable for optimized upload pre-pose. Falling back to CPU.", error)
                }
                .getOrElse { createPoseLandmarker(delegate = null) }
        } catch (error: UnsatisfiedLinkError) {
            Log.w(TAG, "Optimized upload pre-pose MediaPipe is unavailable on this device.", error)
            null
        } catch (error: Exception) {
            Log.e(TAG, "Failed to create optimized upload pre-pose landmarker.", error)
            null
        }
    }

    private fun createPoseLandmarker(delegate: Delegate?): PoseLandmarker {
        val baseOptionsBuilder = BaseOptions.builder()
            .setModelAssetPath(POSE_MODEL_PATH)
        if (delegate != null) {
            baseOptionsBuilder.setDelegate(delegate)
        }

        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptionsBuilder.build())
            .setRunningMode(RunningMode.VIDEO)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(0.5f)
            .setMinPosePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .build()

        return PoseLandmarker.createFromOptions(context, options)
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
            Log.e(TAG, "Failed to set optimized upload pre-pose data source.", error)
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
        internal const val MAX_INFERENCE_DIMENSION_PX = 384

        private const val TAG = "UploadOptimizedPrePoseVideoAnalyzer"
        private const val POSE_MODEL_PATH = "models/pose_landmarker_lite.task"
        private const val DEQUEUE_TIMEOUT_US = 10_000L
        private const val FRAME_WAIT_TIMEOUT_MS = 500L
        private const val PIXEL_STRIDE = 4
    }
}
