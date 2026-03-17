package com.ddgo.app.ml

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ddgo.app.data.ml.persondetect.PersonDetectorImpl
import com.ddgo.app.data.ml.yolo.HoldDetectorImpl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import wseemann.media.FFmpegMediaMetadataRetriever
import java.io.File

/**
 * 전체 홀드 감지 파이프라인 통합 테스트.
 */
@RunWith(AndroidJUnit4::class)
class HoldDetectionIntegrationTest {

    companion object {
        private const val TAG            = "HoldDetectionTest"
        private const val TEST_FILE_NAME = "test_climbing.mp4"
    }

    private lateinit var context: android.content.Context
    private lateinit var personDetector: PersonDetectorImpl
    private lateinit var holdDetector: HoldDetectorImpl

    private val testVideoFile: File
        get() = File(context.getExternalFilesDir(null), TEST_FILE_NAME)

    private val testVideoUri: String
        get() = Uri.fromFile(testVideoFile).toString()

    @Before
    fun setUp() {
        context        = InstrumentationRegistry.getInstrumentation().targetContext
        personDetector = PersonDetectorImpl(context)
        holdDetector   = HoldDetectorImpl(context)
    }

    @Test
    fun tflite_person_모델_로드_성공() {
        val interpreter = com.ddgo.app.data.ml.common.TFLiteInferenceUtils
            .createInterpreter(context, "models/person_detect_v0n_320.tflite")
        assertNotNull(interpreter)
        interpreter.close()
    }

    @Test
    fun tflite_hold_모델_로드_성공() {
        val interpreter = com.ddgo.app.data.ml.common.TFLiteInferenceUtils
            .createInterpreter(context, "models/hold_detect_seg_v11n_640_float32.tflite")
        assertNotNull(interpreter)
        interpreter.close()
    }

    @Test
    fun 전체_파이프라인_통합_테스트() = runBlocking {
        skipIfVideoMissing()

        // 1. PersonDetector: 최적 프레임 시간 찾기
        val bestTimeUs = personDetector.findBestFrameTime(testVideoUri)
        assertTrue("최적 프레임 시간을 찾지 못함", bestTimeUs >= 0)

        // 2. 프레임 추출
        val bitmap = extractFrame(testVideoUri, bestTimeUs)
        assertNotNull("프레임 추출 실패", bitmap)

        // 3. HoldDetector: 홀드 탐지
        val holds = holdDetector.detectFromFrame(bitmap!!)
        bitmap.recycle()

        Log.d(TAG, "✅ 감지된 홀드 수: ${holds.size}")
        assertTrue("홀드가 감지되지 않음", holds.isNotEmpty())
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

    private fun skipIfVideoMissing() {
        if (!testVideoFile.exists()) {
            org.junit.Assume.assumeTrue("테스트 영상 없음", false)
        }
    }
}
