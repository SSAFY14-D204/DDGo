package com.ddgo.app.ml

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
import java.io.File

/**
 * 전체 홀드 감지 파이프라인 통합 테스트.
 *
 * ────────────────────────────────────────────────────────
 * 사전 준비 (테스트 전 딱 1번만 하면 됨):
 *
 *   앱 전용 외부 저장소에 테스트 영상을 복사합니다.
 *   (스코프 스토리지 정책으로 /sdcard/Download/ 는 직접 접근 불가)
 *
 *   adb push 영상파일.mp4 /sdcard/Android/data/com.ddgo.app/files/test_climbing.mp4
 *
 *   에뮬레이터의 경우:
 *     Android Studio → Device Explorer
 *     → /sdcard/Android/data/com.ddgo.app/files/ 에 영상 드래그&드롭
 *     (files/ 폴더가 없으면 먼저 앱을 한 번 실행해주세요)
 * ────────────────────────────────────────────────────────
 *
 * 실행 방법:
 *   Android Studio → 이 파일 우클릭 → "Run 'HoldDetectionIntegrationTest'"
 *   또는: ./gradlew connectedAndroidTest --tests "*.HoldDetectionIntegrationTest"
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

    /** 앱 전용 외부 저장소의 테스트 영상 파일 (/sdcard/Android/data/com.ddgo.app/files/) */
    private val testVideoFile: File
        get() = File(context.getExternalFilesDir(null), TEST_FILE_NAME)

    /** MediaMetadataRetriever 에 전달할 content:// or file:// URI 문자열 */
    private val testVideoUri: String
        get() = Uri.fromFile(testVideoFile).toString()

    @Before
    fun setUp() {
        context        = InstrumentationRegistry.getInstrumentation().targetContext
        personDetector = PersonDetectorImpl(context)
        holdDetector   = HoldDetectorImpl(context)  // PersonDetector는 내부에서 직접 처리
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 모델 파일 로드 단독 테스트 (영상 없이도 즉시 실행 가능)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun tflite_person_모델_assets에서_로드_성공() {
        val interpreter = try {
            com.ddgo.app.data.ml.common.TFLiteInferenceUtils
                .createInterpreter(context, "models/person_detect_v0n_320.tflite")
        } catch (e: Exception) {
            Log.e(TAG, "❌ person 모델 로드 실패: ${e.message}")
            null
        }

        assertNotNull(
            "person_detect_v0n_320.tflite 로드 실패.\n  assets/models/ 에 파일이 있는지 확인하세요.",
            interpreter
        )

        val inputShape  = interpreter!!.getInputTensor(0).shape()
        val outputShape = interpreter.getOutputTensor(0).shape()
        Log.d(TAG, "✅ person 모델 로드 성공")
        Log.d(TAG, "   입력 shape : ${inputShape.toList()}  (예상: [1, 320, 320, 3])")
        Log.d(TAG, "   출력 shape : ${outputShape.toList()}  (예상: [1, 5, 2100])")

        interpreter.close()
    }

    @Test
    fun tflite_hold_모델_assets에서_로드_성공() {
        val interpreter = try {
            com.ddgo.app.data.ml.common.TFLiteInferenceUtils
                .createInterpreter(context, "models/best_float32_v8_640.tflite")
        } catch (e: Exception) {
            Log.e(TAG, "❌ hold 모델 로드 실패: ${e.message}")
            null
        }

        assertNotNull(
            "best_float32_v8_640.tflite 로드 실패.\n  assets/models/ 에 파일이 있는지 확인하세요.",
            interpreter
        )

        val inputShape  = interpreter!!.getInputTensor(0).shape()
        val outputShape = interpreter.getOutputTensor(0).shape()
        Log.d(TAG, "✅ hold 모델 로드 성공")
        Log.d(TAG, "   입력 shape : ${inputShape.toList()}  (예상: [1, 640, 640, 3])")
        Log.d(TAG, "   출력 shape : ${outputShape.toList()}  (예상: [1, 6, 8400])")

        interpreter.close()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PersonDetector 테스트 (영상 필요)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun personDetector_최적_프레임_타임스탬프_반환() = runBlocking {
        skipIfVideoMissing()

        Log.d(TAG, "▶ PersonDetector 시작: $testVideoUri")

        val startMs     = System.currentTimeMillis()
        val bestFrameUs = personDetector.findBestFrameTime(testVideoUri)
        val elapsedMs   = System.currentTimeMillis() - startMs

        Log.d(TAG, "✅ 최적 프레임: ${bestFrameUs / 1000}ms  (소요: ${elapsedMs}ms)")

        // 0L 이면 영상 접근 실패 또는 모델 오류
        assertTrue(
            "bestFrameUs > 0 이어야 합니다. 0이면:\n" +
            "  1) 영상 경로 확인: ${testVideoFile.absolutePath}\n" +
            "  2) adb push 명령으로 영상을 다시 복사해보세요",
            bestFrameUs > 0L
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // HoldDetector 전체 파이프라인 테스트 (영상 필요)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun holdDetector_전체_파이프라인_홀드_감지_성공() = runBlocking {
        skipIfVideoMissing()

        Log.d(TAG, "▶ HoldDetector 전체 파이프라인 시작")

        val startMs   = System.currentTimeMillis()
        val holds     = holdDetector.detectFromVideo(testVideoUri)
        val elapsedMs = System.currentTimeMillis() - startMs

        Log.d(TAG, "✅ 감지된 홀드 수: ${holds.size}  (소요: ${elapsedMs}ms)")
        holds.forEachIndexed { i, hold ->
            Log.d(TAG,
                "  홀드[$i] conf=${String.format("%.3f", hold.confidence)}" +
                "  bbox=(L=${String.format("%.3f", hold.boundingBox.left)}" +
                " T=${String.format("%.3f", hold.boundingBox.top)}" +
                " R=${String.format("%.3f", hold.boundingBox.right)}" +
                " B=${String.format("%.3f", hold.boundingBox.bottom)})"
            )
        }

        // 클라이밍 영상이면 홀드가 1개 이상이어야 함
        assertTrue(
            "홀드 0개 → 클라이밍 벽 영상인지 확인하거나 confidence threshold를 낮춰보세요",
            holds.isNotEmpty()
        )

        // 좌표 범위 검증
        holds.forEach { hold ->
            val bb = hold.boundingBox
            assertTrue("left >= 0",   bb.left   >= 0f)
            assertTrue("top >= 0",    bb.top    >= 0f)
            assertTrue("right <= 1",  bb.right  <= 1f)
            assertTrue("bottom <= 1", bb.bottom <= 1f)
            assertTrue("left < right",  bb.left  < bb.right)
            assertTrue("top < bottom",  bb.top   < bb.bottom)
            assertTrue("confidence > 0", hold.confidence > 0f)
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────────────────────────────────

    private fun skipIfVideoMissing() {
        val file = testVideoFile
        if (!file.exists()) {
            Log.w(TAG, "⚠️  테스트 영상 없음 → 테스트 건너뜀")
            Log.w(TAG, "   adb push 영상.mp4 ${file.absolutePath}")
            org.junit.Assume.assumeTrue(
                "테스트 영상 없음. 아래 명령으로 복사 후 재실행:\n" +
                "  adb push 영상.mp4 ${file.absolutePath}",
                false
            )
        }
    }
}
