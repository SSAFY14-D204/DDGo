// [DEBUG ONLY] 이 파일은 디버그 모드에서의 비디오 포즈 추출 가속화를 위해 작성되었습니다.
package com.ddgo.app.feature.debug

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
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
import com.ddgo.app.data.mapper.VisionMapper
import com.google.mediapipe.framework.image.MediaImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import kotlin.math.max

/**
 * 최종 최적화 Pre-Pose 분석기 (OpenGL EGL Hardware Scaling 적용 - 대안 1)
 * - URI 안정성 확보 (임시 파일 복사)
 * - MediaCodec + OpenGL EGL(OES Texture)를 활용해 원본(1080p) -> 최적화(640px) GPU 가속 스케일링 수행
 * - ImageReader(RGBA_8888)에서 추출한 android.media.Image를 MediaImageBuilder로 직접 전달 (Zero-Copy 지향)
 */
class OptimizedPrePoseVideoAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var lastTimestampMs: Long = -1
    private var lastCaptureTimeMs: Long = -10_000 // 첫 프레임(0초) 캡처를 위해 -10초로 초기화

    suspend operator fun invoke(
        videoUri: String,
        onProgress: (Float) -> Unit = {}
    ): Result<List<DebugPoseFrameResult>> = withContext(Dispatchers.IO) {
        runCatching {
            lastTimestampMs = -1
            lastCaptureTimeMs = -5_000
            val safeUri = prepareSafeUri(videoUri)
            try {
                analyzeInternal(safeUri, onProgress)
            } finally {
                cleanupSafeUri(safeUri)
            }
        }
    }

    private fun prepareSafeUri(videoUri: String): Uri {
        val uri = Uri.parse(videoUri)
        if (uri.scheme == "file") return uri

        return try {
            val tempFile = File(context.cacheDir, "pre_pose_temp_${System.currentTimeMillis()}.mp4")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "📋 분석용 임시 파일 복사 완료: ${tempFile.absolutePath}")
            Uri.fromFile(tempFile)
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 임시 파일 복사 실패, 원본 URI 사용: ${e.message}")
            uri
        }
    }

    private fun cleanupSafeUri(uri: Uri) {
        if (uri.scheme == "file" && uri.path?.contains("pre_pose_temp_") == true) {
            File(uri.path!!).delete()
            Log.d(TAG, "🗑️ 임시 파일 삭제 완료")
        }
    }

    private fun analyzeInternal(
        uri: Uri,
        onProgress: (Float) -> Unit
    ): List<DebugPoseFrameResult> {
        val poseLandmarker = createPoseLandmarker()
        try {
            return analyzeWithSurface(uri, poseLandmarker, onProgress)
        } finally {
            poseLandmarker.close()
        }
    }

    private fun analyzeWithSurface(
        uri: Uri,
        poseLandmarker: PoseLandmarker,
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
                throw IllegalStateException("비디오 소스를 열 수 없습니다.")
            }

            val videoTrackIndex = findVideoTrackIndex(extractor)
            if (videoTrackIndex == -1) return emptyList()

            extractor.selectTrack(videoTrackIndex)
            val trackFormat = extractor.getTrackFormat(videoTrackIndex)
            val mimeType = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
            val durationUs = if (trackFormat.containsKey(MediaFormat.KEY_DURATION)) trackFormat.getLong(MediaFormat.KEY_DURATION) else 0L
            val width = trackFormat.getInteger(MediaFormat.KEY_WIDTH)
            val height = trackFormat.getInteger(MediaFormat.KEY_HEIGHT)
            val rotationDegrees = if (trackFormat.containsKey(MediaFormat.KEY_ROTATION)) trackFormat.getInteger(MediaFormat.KEY_ROTATION) else 0

            // 1. 시각적 해상도 결정 (회전 반영: 90, 270이면 너비/높이 스왑)
            val isRotated = rotationDegrees == 90 || rotationDegrees == 270
            val visualW = if (isRotated) height else width
            val visualH = if (isRotated) width else height

            // 분석 효율을 위해 해상도 결정 (최대 640px)
            val maxDim = max(visualW, visualH)
            val scale = if (maxDim > MAX_INFERENCE_DIM) MAX_INFERENCE_DIM.toFloat() / maxDim else 1.0f
            val targetW = (visualW * scale).toInt()
            val targetH = (visualH * scale).toInt()

            Log.d(TAG, "디코딩 설정: 원본 ${width}x${height}, 시각적 ${visualW}x${visualH} -> 타겟 ${targetW}x${targetH}, rotation=$rotationDegrees")

            // SurfaceTexture의 onFrameAvailable 콜백을 받기 위한 스레드
            handlerThread = HandlerThread("SurfaceTextureThread").apply { start() }
            val handler = Handler(handlerThread.looper)

            // ImageReader를 RGBA_8888 포맷으로 생성하여 스케일링된 픽셀을 받음
            imageReader = ImageReader.newInstance(targetW, targetH, PixelFormat.RGBA_8888, 2)
            
            // ImageReader의 Surface를 EGL Window로 래핑하여 OpenGL 컨텍스트 생성
            eglWindow = EglWindow(imageReader.surface)
            eglWindow.makeCurrent()
            GLES20.glViewport(0, 0, targetW, targetH)
            
            textureRenderer = TextureRenderer()
            surfaceTexture = SurfaceTexture(textureRenderer.textureId)
            decoderSurface = Surface(surfaceTexture)

            val frameSyncObject = Object()
            var frameAvailable = false

            surfaceTexture.setOnFrameAvailableListener({
                synchronized(frameSyncObject) {
                    frameAvailable = true
                    frameSyncObject.notifyAll()
                }
            }, handler)

            decoder = MediaCodec.createDecoderByType(mimeType)
            // 디코더는 OpenGL OES 텍스처(surfaceTexture)에 원본 해상도로 그림을 그림
            decoder.configure(trackFormat, decoderSurface, null, 0)
            decoder.start()

            return decodeLoop(
                extractor, decoder, imageReader, poseLandmarker, 
                durationUs, rotationDegrees, onProgress, 
                targetW, targetH,
                frameSyncObject, { frameAvailable }, { frameAvailable = it },
                surfaceTexture, textureRenderer, eglWindow
            )

        } finally {
            try { decoder?.stop(); decoder?.release() } catch (_: Exception) {}
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
        targetW: Int,
        targetH: Int,
        frameSyncObject: Object,
        isFrameAvailable: () -> Boolean,
        setFrameAvailable: (Boolean) -> Unit,
        surfaceTexture: SurfaceTexture,
        textureRenderer: TextureRenderer,
        eglWindow: EglWindow
    ): List<DebugPoseFrameResult> {
        val bufferInfo = MediaCodec.BufferInfo()
        val poses = ArrayList<DebugPoseFrameResult>()
        var inputEnded = false
        var outputEnded = false
        val TIMEOUT_US = 10_000L

        // GPU에서 이미 시각적 방향으로 회전시켜 ImageReader에 그릴 것이므로,
        // MediaPipe에는 회전 정보를 0으로 넘겨 중복 회전을 방지합니다.
        val imageOptions = ImageProcessingOptions.builder()
            .setRotationDegrees(0)
            .build()
            
        val texMatrix = FloatArray(16)
        val mvpMatrix = FloatArray(16)
        android.opengl.Matrix.setIdentityM(mvpMatrix, 0)
        
        // 시각적 정렬을 위해 MVP 행렬에 회전 적용
        // "현재 상태에서 왼쪽으로 90도 회전(+90f)"을 요청하셨으므로 90도를 더해줍니다.
        if (rotationDegrees != 0) {
            android.opengl.Matrix.rotateM(mvpMatrix, 0, -rotationDegrees.toFloat() + 90f, 0f, 0f, 1f)
        }
        
        while (!outputEnded) {
            if (!inputEnded) {
                val inputIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                if (inputIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputIndex)!!
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputEnded = true
                    } else {
                        decoder.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            if (outputIndex >= 0) {
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputEnded = true
                
                if (bufferInfo.presentationTimeUs >= 0 && bufferInfo.size > 0) {
                    if (durationUs > 0) onProgress(bufferInfo.presentationTimeUs.toFloat() / durationUs.toFloat())
                    
                    // 디코더 텍스처로 렌더링 호출
                    decoder.releaseOutputBuffer(outputIndex, true)

                    var awaitSuccess = false
                    synchronized(frameSyncObject) {
                        val deadline = System.currentTimeMillis() + 500
                        while (!isFrameAvailable()) {
                            val waitTime = deadline - System.currentTimeMillis()
                            if (waitTime <= 0) break // Time out
                            frameSyncObject.wait(waitTime)
                        }
                        if (isFrameAvailable()) {
                            setFrameAvailable(false)
                            awaitSuccess = true
                        }
                    }

                    if (awaitSuccess) {
                        surfaceTexture.updateTexImage()
                        surfaceTexture.getTransformMatrix(texMatrix)
                        
                        // OES 텍스처를 시각적 규격(targetW x targetH)에 맞춰 회전 및 렌더링
                        textureRenderer.draw(texMatrix, mvpMatrix)
                        eglWindow.swapBuffers()

                        // ImageReader에서 축소된 RGBA 이미지 획득
                        val image = imageReader.acquireLatestImage()
                        if (image != null) {
                            try {
                                // 타임스탬프 단조 증가 보정
                                var currentTimestampMs = bufferInfo.presentationTimeUs / 1000
                                if (currentTimestampMs <= lastTimestampMs) {
                                    currentTimestampMs = lastTimestampMs + 1
                                }
                                lastTimestampMs = currentTimestampMs

                                // 메모리 안정성을 위해 Bitmap으로 변환 후 MediaPipe 전달
                                val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                                val plane = image.planes[0]
                                val buffer = plane.buffer
                                val rowStride = plane.rowStride
                                val pixelStride = 4 

                                buffer.rewind()
                                if (rowStride == targetW * pixelStride) {
                                    bitmap.copyPixelsFromBuffer(buffer)
                                } else {
                                    val rowBuffer = ByteArray(rowStride)
                                    val pixelBuffer = ByteBuffer.allocateDirect(targetW * targetH * pixelStride)
                                        .order(ByteOrder.nativeOrder())
                                    for (y in 0 until targetH) {
                                        buffer.position(y * rowStride)
                                        buffer.get(rowBuffer, 0, rowStride)
                                        pixelBuffer.put(rowBuffer, 0, targetW * pixelStride)
                                    }
                                    pixelBuffer.rewind()
                                    bitmap.copyPixelsFromBuffer(pixelBuffer)
                                }

                                val mpImage = com.google.mediapipe.framework.image.BitmapImageBuilder(bitmap).build()
                                
                                // 5초 간격으로 이미지 캡처 (디버깅용)
                                val shouldCapture = currentTimestampMs >= lastCaptureTimeMs + 5_000
                                val capturedBitmap = if (shouldCapture) {
                                    lastCaptureTimeMs = (currentTimestampMs / 5000) * 5000
                                    Bitmap.createBitmap(bitmap)
                                } else null

                                // GPU Delegate 추론 수행
                                val result = poseLandmarker.detectForVideo(mpImage, imageOptions, currentTimestampMs)
                                
                                val landmarks = result.landmarks().firstOrNull().orEmpty()
                                val pose = VisionMapper.toPose(
                                    frameTimeMs = result.timestampMs(),
                                    rawLandmarks = landmarks.map { Triple(it.x(), it.y(), it.z()) }
                                )
                                
                                // 포즈가 검출되었거나, 디버깅용 캡처 이미지가 있는 경우 결과에 추가
                                if (landmarks.isNotEmpty() || capturedBitmap != null) {
                                    poses.add(DebugPoseFrameResult(
                                        pose = pose,
                                        worldLandmarks = result.worldLandmarks().firstOrNull().orEmpty().mapIndexed { i, l ->
                                            DebugPoseWorldLandmark(i, l.x(), l.y(), l.z())
                                        },
                                        capturedBitmap = capturedBitmap
                                    ))
                                }
                                mpImage.close()
                            } catch (e: Exception) {
                                Log.e(TAG, "추론 실패 (PTS: ${bufferInfo.presentationTimeUs / 1000}): ${e.message}")
                            } finally {
                                image.close()
                            }
                        }
                    }
                } else {
                    decoder.releaseOutputBuffer(outputIndex, false)
                }
            }
        }
        Log.d(TAG, "분석 완료: 총 ${poses.size}개의 포즈 검출")
        return poses
    }

    private fun createPoseLandmarker(): PoseLandmarker {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("models/pose_landmarker_lite.task")
            .setDelegate(Delegate.GPU)
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

    private fun findVideoTrackIndex(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("video/") == true) return i
        }
        return -1
    }

    private fun setExtractorDataSource(extractor: MediaExtractor, uri: Uri): Boolean {
        return try {
            if (uri.scheme == "file") {
                extractor.setDataSource(uri.path!!)
            } else {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { 
                    extractor.setDataSource(it.fileDescriptor) 
                }
            }
            true
        } catch (e: Exception) { 
            Log.e(TAG, "DataSource 설정 실패: ${e.message}")
            false 
        }
    }

    // --- EGL 보조 클래스 (하드웨어 스케일러) ---

    private class EglWindow(val surface: Surface) {
        var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

        init {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val version = IntArray(2)
            EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

            val attribList = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.size, numConfigs, 0)

            val contextAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )
            eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0)

            val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], surface, surfaceAttribs, 0)
        }

        fun makeCurrent() {
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        }

        fun swapBuffers() {
            EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        }

        fun release() {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglTerminate(eglDisplay)
            }
        }
    }

    private class TextureRenderer {
        private var program = 0
        private var positionHandle = 0
        private var texCoordHandle = 0
        private var texMatrixHandle = 0
        private var mvpMatrixHandle = 0
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
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        }

        private fun loadShader(type: Int, shaderCode: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
            return shader
        }

        fun draw(texMatrix: FloatArray, mvpMatrix: FloatArray) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glUseProgram(program)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

            val vertices = floatArrayOf(
                -1f, -1f,
                 1f, -1f,
                -1f,  1f,
                 1f,  1f
            )
            val texCoords = floatArrayOf(
                0f, 0f,
                1f, 0f,
                0f, 1f,
                1f, 1f
            )

            val vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            vertexBuffer.put(vertices).position(0)

            val texCoordBuffer = ByteBuffer.allocateDirect(texCoords.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            texCoordBuffer.put(texCoords).position(0)

            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

            GLES20.glEnableVertexAttribArray(texCoordHandle)
            GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

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
    }

    companion object {
        private const val TAG = "OptimizedPrePose"
        private const val MAX_INFERENCE_DIM = 640
    }
}
