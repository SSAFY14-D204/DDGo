package com.ddgo.app.data.ml.common

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ddgo.app.data.ml.common.TFLiteInferenceUtils.RawDetection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * TFLiteInferenceUtils 단위 테스트 — 영상/모델 파일 불필요.
 *
 * 실행 방법:
 *   Android Studio → 이 파일 우클릭 → "Run 'TFLiteInferenceUtilsTest'"
 *   또는: ./gradlew connectedAndroidTest --tests "*.TFLiteInferenceUtilsTest"
 *
 * 에뮬레이터 또는 실제 기기가 연결되어 있어야 합니다.
 */
@RunWith(AndroidJUnit4::class)
class TFLiteInferenceUtilsTest {

    // ──────────────────────────────────────────────────────────────────────────
    // letterbox() 테스트
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun letterbox_출력_크기는_항상_modelSize_정사각형이어야_한다() {
        val src = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        val info = TFLiteInferenceUtils.letterbox(src, 320)

        assertEquals("너비가 modelSize여야 함", 320, info.bitmap.width)
        assertEquals("높이가 modelSize여야 함", 320, info.bitmap.height)
        src.recycle()
        info.bitmap.recycle()
    }

    @Test
    fun letterbox_세로_영상은_좌우에_패딩이_생겨야_한다() {
        // 세로 영상 (1080×1920) → 320 모델
        // scale = min(320/1080, 320/1920) = 320/1920 ≈ 0.1667
        // scaledW ≈ 180, padLeft = (320 - 180) / 2 = 70
        val src = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        val info = TFLiteInferenceUtils.letterbox(src, 320)

        assertTrue("세로 영상은 padLeft > 0이어야 함", info.padLeft > 0)
        assertEquals("padTop은 0이어야 함", 0, info.padTop)
        src.recycle()
        info.bitmap.recycle()
    }

    @Test
    fun letterbox_가로_영상은_위아래에_패딩이_생겨야_한다() {
        // 가로 영상 (1920×1080) → 640 모델
        // scale = min(640/1920, 640/1080) = 640/1920 ≈ 0.333
        // scaledH ≈ 360, padTop = (640 - 360) / 2 = 140
        val src = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        val info = TFLiteInferenceUtils.letterbox(src, 640)

        assertEquals("가로 영상은 padLeft가 0이어야 함", 0, info.padLeft)
        assertTrue("가로 영상은 padTop > 0이어야 함", info.padTop > 0)
        src.recycle()
        info.bitmap.recycle()
    }

    @Test
    fun letterbox_패딩_영역은_회색_114_114_114이어야_한다() {
        val src = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        val info = TFLiteInferenceUtils.letterbox(src, 320)

        // 왼쪽 패딩의 중앙 픽셀은 회색(114, 114, 114)이어야 함
        if (info.padLeft > 0) {
            val pixel = info.bitmap.getPixel(0, 160)  // 왼쪽 끝 중간
            assertEquals("R 채널이 114여야 함", 114, Color.red(pixel))
            assertEquals("G 채널이 114여야 함", 114, Color.green(pixel))
            assertEquals("B 채널이 114여야 함", 114, Color.blue(pixel))
        }
        src.recycle()
        info.bitmap.recycle()
    }

    @Test
    fun letterbox_originalWidth_originalHeight_올바르게_저장되어야_한다() {
        val src = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        val info = TFLiteInferenceUtils.letterbox(src, 640)

        assertEquals(800, info.originalWidth)
        assertEquals(600, info.originalHeight)
        src.recycle()
        info.bitmap.recycle()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // filterByConfidence() 테스트
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun filterByConfidence_임계값_미만_검출은_제거되어야_한다() {
        val rows = listOf(
            floatArrayOf(100f, 100f, 50f, 50f, 0.20f),   // score < 0.25 → 제거
            floatArrayOf(200f, 200f, 60f, 60f, 0.80f),   // score >= 0.25 → 통과
            floatArrayOf(300f, 300f, 40f, 40f, 0.25f),   // score == 0.25 → 통과
        )
        val result = TFLiteInferenceUtils.filterByConfidence(rows, 0.25f)

        assertEquals("0.25 미만 1개 제거 후 2개여야 함", 2, result.size)
    }

    @Test
    fun filterByConfidence_xywh가_xyxy로_올바르게_변환되어야_한다() {
        // cx=100, cy=200, w=40, h=60, score=0.9
        // → x1=80, y1=170, x2=120, y2=230
        val rows = listOf(floatArrayOf(100f, 200f, 40f, 60f, 0.9f))
        val result = TFLiteInferenceUtils.filterByConfidence(rows, 0.25f)

        assertEquals(1, result.size)
        assertClose(80f,  result[0].x1)
        assertClose(170f, result[0].y1)
        assertClose(120f, result[0].x2)
        assertClose(230f, result[0].y2)
    }

    @Test
    fun filterByConfidence_멀티_클래스_argmax가_올바르게_선택되어야_한다() {
        // [cx, cy, w, h, hold_score, volume_score]
        // hold=0.3, volume=0.8 → classIndex=1(volume)이어야 함
        val rows = listOf(floatArrayOf(50f, 50f, 20f, 20f, 0.3f, 0.8f))
        val result = TFLiteInferenceUtils.filterByConfidence(rows, 0.25f)

        assertEquals(1, result.size)
        assertEquals("volume 클래스(1)가 선택되어야 함", 1, result[0].classIndex)
        assertClose(0.8f, result[0].confidence)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // applyNms() 테스트
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun applyNms_완전히_겹치는_박스는_1개만_남아야_한다() {
        val detections = listOf(
            RawDetection(10f, 10f, 50f, 50f, confidence = 0.9f, classIndex = 0),
            RawDetection(10f, 10f, 50f, 50f, confidence = 0.7f, classIndex = 0),  // 완전 중복 → 제거
        )
        val result = TFLiteInferenceUtils.applyNms(detections, 0.45f)

        assertEquals("완전 중복 시 1개만 남아야 함", 1, result.size)
        assertClose(0.9f, result[0].confidence)  // 높은 confidence가 살아남아야 함
    }

    @Test
    fun applyNms_겹치지_않는_박스는_모두_살아남아야_한다() {
        val detections = listOf(
            RawDetection(10f,  10f,  50f,  50f,  confidence = 0.9f, classIndex = 0),
            RawDetection(200f, 200f, 250f, 250f, confidence = 0.8f, classIndex = 0),  // 멀리 있음
        )
        val result = TFLiteInferenceUtils.applyNms(detections, 0.45f)

        assertEquals("겹치지 않으면 2개 모두 살아남아야 함", 2, result.size)
    }

    @Test
    fun applyNms_다른_클래스끼리는_NMS_적용_안_됨() {
        // 완전히 같은 위치이지만 클래스가 다름 → 둘 다 살아남아야 함
        val detections = listOf(
            RawDetection(10f, 10f, 50f, 50f, confidence = 0.9f, classIndex = 0),  // hold
            RawDetection(10f, 10f, 50f, 50f, confidence = 0.8f, classIndex = 1),  // volume
        )
        val result = TFLiteInferenceUtils.applyNms(detections, 0.45f)

        assertEquals("서로 다른 클래스는 NMS 무관 → 2개 모두 유지", 2, result.size)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // scaleToNormalized() 테스트
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun scaleToNormalized_좌표가_0에서_1_범위_안에_있어야_한다() {
        // 1080×1920 세로 영상 → 320 모델 letterbox
        val src = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        val info = TFLiteInferenceUtils.letterbox(src, 320)

        // letterbox 이미지 내 임의 박스
        val detections = listOf(
            RawDetection(info.padLeft.toFloat(), 0f,
                         (320 - info.padLeft).toFloat(), 319f,
                         0.9f, 0)
        )
        val result = TFLiteInferenceUtils.scaleToNormalized(detections, info)

        assertEquals(1, result.size)
        val normLeft   = result[0][0]
        val normTop    = result[0][1]
        val normRight  = result[0][2]
        val normBottom = result[0][3]

        assertTrue("left >= 0",   normLeft   >= 0f)
        assertTrue("top >= 0",    normTop    >= 0f)
        assertTrue("right <= 1",  normRight  <= 1f)
        assertTrue("bottom <= 1", normBottom <= 1f)

        src.recycle()
        info.bitmap.recycle()
    }

    @Test
    fun scaleToNormalized_전체_영상_박스는_1로_정규화되어야_한다() {
        // 정사각형 원본 → 패딩 없음
        val src = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
        val info = TFLiteInferenceUtils.letterbox(src, 640)

        // 전체를 덮는 박스 (패딩 없으므로 0,0,640,640)
        val detections = listOf(RawDetection(0f, 0f, 640f, 640f, 0.9f, 0))
        val result = TFLiteInferenceUtils.scaleToNormalized(detections, info)

        assertClose(0f, result[0][0])   // normLeft  ≈ 0
        assertClose(0f, result[0][1])   // normTop   ≈ 0
        assertClose(1f, result[0][2])   // normRight ≈ 1
        assertClose(1f, result[0][3])   // normBottom ≈ 1

        src.recycle()
        info.bitmap.recycle()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // normalizeToByteBuffer() 테스트
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun normalizeToByteBuffer_순수_빨간_픽셀은_R1_G0_B0이어야_한다() {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)  // 모든 픽셀을 빨간색으로

        val buffer = TFLiteInferenceUtils.normalizeToByteBuffer(bitmap, 4)
        buffer.rewind()

        val r = buffer.float
        val g = buffer.float
        val b = buffer.float

        assertClose(1.0f, r, delta = 0.01f)   // Red ≈ 1.0
        assertClose(0.0f, g, delta = 0.01f)   // Green ≈ 0.0
        assertClose(0.0f, b, delta = 0.01f)   // Blue ≈ 0.0

        bitmap.recycle()
    }

    @Test
    fun normalizeToByteBuffer_버퍼_크기는_H_x_W_x_3_x_4바이트여야_한다() {
        val size = 32
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val buffer = TFLiteInferenceUtils.normalizeToByteBuffer(bitmap, size)

        val expected = size * size * 3 * 4  // float = 4 bytes
        assertEquals(expected, buffer.capacity())
        bitmap.recycle()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────────────────────────────────

    private fun assertClose(expected: Float, actual: Float, delta: Float = 1f) {
        assertTrue(
            "expected=$expected, actual=$actual (delta=$delta)",
            abs(expected - actual) <= delta
        )
    }
}
