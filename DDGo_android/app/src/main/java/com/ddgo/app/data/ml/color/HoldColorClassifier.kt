package com.ddgo.app.data.ml.color

import android.graphics.Bitmap
import android.util.Log
import com.ddgo.app.domain.model.Hold
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * HSV 기반 홀드 색상 분류기.
 *
 * same_color.js 로직을 Kotlin으로 완전 포팅.
 * ML 모델 없이 순수 픽셀 연산으로 동작 → 초기화 비용 없음.
 *
 * 알고리즘 개요:
 *   1. bbox 중앙 45% (작은 박스는 60%) ROI 추출
 *   2. RGB → HSV 변환 (hue 0-180, s/v 0-255)
 *   3. 흑/백 픽셀 비율로 먼저 판정
 *   4. 크로매틱 hue 히스토그램 + 색상 패밀리 카운트 → 대표색 결정
 *   5. 확장: gray / brown 추가 판정
 */
@Singleton
class HoldColorClassifier @Inject constructor() {

    // ── 임계값 (same_color.js THRESHOLDS 와 동일) ──────────────────────────
    private object T {
        const val CHROMATIC_S_MIN    = 45f
        const val GRAY_S_MAX         = 35f
        const val LOW_V_CUTOFF       = 45f
        const val HIGH_V_CUTOFF      = 245f
        const val BLACK_V_CUTOFF     = 55f
        const val BLACK_S_MAX        = 70f
        const val WHITE_V_CUTOFF     = 210f
        const val WHITE_S_MAX        = 35f
        const val BLACK_WHITE_RATIO  = 0.60f
        const val MIN_VALID_RATIO    = 0.12f
        const val MIN_VALID_FLOOR    = 20
        const val MIN_PEAK_SHARE     = 0.35f
        const val MIN_LABEL_SHARE    = 0.42f
        const val MIN_LABEL_MARGIN   = 0.08f
        const val UNKNOWN_SCORE_CAP  = 0.45f
        // 확장: gray / brown 판정 기준
        const val GRAY_RATIO_MIN     = 0.55f   // gray가 지배적인 ROI
        const val BROWN_SAT_MAX      = 0.45f   // 갈색 = 낮은 채도의 orange/red
    }

    // ── HSV 180 hue 범위 (same_color.js COLOR_DEFINITIONS 와 동일) ─────────
    // 각 색상의 hue 범위: IntRange 리스트
    private val HUE_RANGES: Map<String, List<IntRange>> = mapOf(
        "red"    to listOf(0..10, 170..180),
        "orange" to listOf(10..22),
        "yellow" to listOf(22..35),
        "green"  to listOf(35..85),
        "blue"   to listOf(85..130),
        "purple" to listOf(130..160),
        "pink"   to listOf(160..170),
    )

    // 크로매틱 색상 레이블 목록 (black/white/gray/brown 제외)
    private val CHROMATIC_LABELS = HUE_RANGES.keys.toList()

    /**
     * 앱 색상 이름 → 분류기 레이블 매핑.
     *
     * ChallengeCreateScreen의 HOLD_COLORS 이름을 분류기가 사용하는 내부 레이블로 변환.
     * - cyan  → blue  (hue 85-130 범위에 이미 포함)
     * - brown → "brown" (orange/red hue + 낮은 채도로 별도 판정)
     * - gray  → "gray"  (별도 판정)
     */
    val APP_COLOR_TO_LABEL: Map<String, String> = mapOf(
        "red"    to "red",
        "orange" to "orange",
        "yellow" to "yellow",
        "green"  to "green",
        "cyan"   to "blue",
        "blue"   to "blue",
        "purple" to "purple",
        "brown"  to "brown",
        "pink"   to "pink",
        "white"  to "white",
        "gray"   to "gray",
        "black"  to "black",
    )

    // ── 공개 API ─────────────────────────────────────────────────────────────

    /**
     * 홀드 목록을 색상 분류하고, 사용자가 선택한 색과 일치하는 홀드만 반환합니다.
     *
     * @param bitmap          원본 프레임 비트맵 (정규화 좌표 역산에 사용)
     * @param holds           YOLO 탐지 홀드 목록
     * @param targetColorName 사용자가 선택한 색 이름 (앱 색상 이름 기준, 예: "red")
     * @param scoreThreshold  색상 점수 최소 임계값 (기본 0.25 — UI 필터 0.55보다 넉넉하게)
     * @return colorLabel/colorScore 가 채워진 Hold 중, targetColor와 일치하는 것만
     */
    fun classifyAndFilter(
        bitmap: Bitmap,
        holds: List<Hold>,
        targetColorName: String,
        scoreThreshold: Float = 0.25f
    ): List<Hold> {
        val targetLabel = APP_COLOR_TO_LABEL[targetColorName.lowercase()] ?: targetColorName.lowercase()
        Log.d(TAG, "색상 분류 시작: ${holds.size}개 홀드, 목표 색='$targetColorName' → label='$targetLabel'")

        val classified = holds.map { hold -> classifySingle(bitmap, hold) }

        val filtered = if (targetLabel.isEmpty() || targetLabel == "all") {
            classified
        } else {
            classified.filter { h ->
                h.colorLabel == targetLabel && h.colorScore >= scoreThreshold
            }
        }

        Log.d(TAG, "색상 필터 결과: ${classified.size}개 분류 → ${filtered.size}개 매칭")
        classified.forEach { h ->
            Log.d(TAG, "  홀드(${h.boundingBox}) → ${h.colorLabel} (score=${
                "%.3f".format(h.colorScore)}) ${if (filtered.contains(h)) "✅" else "❌"}")
        }
        return filtered
    }

    /**
     * 단일 홀드의 색상을 분류합니다 (hold.copy로 colorLabel/colorScore 채움).
     */
    fun classifySingle(bitmap: Bitmap, hold: Hold): Hold {
        val iw = bitmap.width
        val ih = bitmap.height

        // 정규화 좌표 → 픽셀 좌표
        val x1 = (hold.boundingBox.left  * iw).toInt().coerceIn(0, iw - 1)
        val y1 = (hold.boundingBox.top   * ih).toInt().coerceIn(0, ih - 1)
        val x2 = (hold.boundingBox.right * iw).toInt().coerceIn(0, iw)
        val y2 = (hold.boundingBox.bottom * ih).toInt().coerceIn(0, ih)
        val bw = max(0, x2 - x1)
        val bh = max(0, y2 - y1)

        if (bw <= 0 || bh <= 0) return hold.copy(colorLabel = "unknown", colorScore = 0f)

        val roi = computeCentralRoi(x1, y1, bw, bh, iw, ih)
        if (roi.width <= 0 || roi.height <= 0) return hold.copy(colorLabel = "unknown", colorScore = 0f)

        // ── 픽셀 추출 ────────────────────────────────────────────────────────
        val pixels = IntArray(roi.width * roi.height)
        bitmap.getPixels(pixels, 0, roi.width, roi.x, roi.y, roi.width, roi.height)

        val totalPixels = pixels.size
        val hueHist = IntArray(180)
        val familyCounts = CHROMATIC_LABELS.associateWith { 0 }.toMutableMap()

        var blackCount = 0
        var whiteCount = 0
        var grayCount  = 0
        var validChromatic = 0
        var satSum = 0f
        // brown 판정용: orange/red hue 범위에서의 채도 누적
        var brownHueCount = 0
        var brownSatSum   = 0f

        for (px in pixels) {
            val r = ((px shr 16) and 0xFF).toFloat()
            val g = ((px shr  8) and 0xFF).toFloat()
            val b = (px          and 0xFF).toFloat()
            val hsv = rgbToHsv180(r, g, b)

            if (hsv.s < T.WHITE_S_MAX && hsv.v > T.WHITE_V_CUTOFF) whiteCount++
            if (hsv.s < T.BLACK_S_MAX && hsv.v < T.BLACK_V_CUTOFF) blackCount++
            if (hsv.s < T.GRAY_S_MAX  && hsv.v in T.BLACK_V_CUTOFF..T.WHITE_V_CUTOFF) grayCount++

            if (hsv.s >= T.CHROMATIC_S_MIN && hsv.v in T.LOW_V_CUTOFF..T.HIGH_V_CUTOFF) {
                validChromatic++
                satSum += hsv.s / 255f
                val bin = hsv.h.toInt().coerceIn(0, 179)
                hueHist[bin]++
                val lbl = mapHueToColor(bin)
                if (lbl != "unknown") familyCounts[lbl] = (familyCounts[lbl] ?: 0) + 1

                // brown 후보: orange/red hue 범위 (hue 0-22 in HSV180)
                if (bin <= 22 || bin >= 165) {
                    brownHueCount++
                    brownSatSum += hsv.s / 255f
                }
            }
        }

        val blackRatio = if (totalPixels > 0) blackCount.toFloat() / totalPixels else 0f
        val whiteRatio = if (totalPixels > 0) whiteCount.toFloat() / totalPixels else 0f
        val grayRatio  = if (totalPixels > 0) grayCount.toFloat()  / totalPixels else 0f
        val validRatio = if (totalPixels > 0) validChromatic.toFloat() / totalPixels else 0f
        val minSide    = min(bw, bh).toFloat()
        val sizeRel    = (minSide / 60f).coerceIn(0.25f, 1f)

        var colorLabel = "unknown"
        var colorScore = 0f

        // ── 흑/백 우선 판정 (JS와 동일) ──────────────────────────────────────
        if (blackRatio >= T.BLACK_WHITE_RATIO && whiteRatio < 0.3f) {
            colorLabel = "black"
            colorScore = (0.7f * blackRatio + 0.15f * sizeRel + 0.15f * hold.confidence).coerceIn(0f, 1f)
        } else if (whiteRatio >= T.BLACK_WHITE_RATIO && blackRatio < 0.3f) {
            colorLabel = "white"
            colorScore = (0.7f * whiteRatio + 0.15f * sizeRel + 0.15f * hold.confidence).coerceIn(0f, 1f)
        } else {
            // ── 크로매틱 색 판정 ──────────────────────────────────────────────
            val minValid = max(T.MIN_VALID_FLOOR, (totalPixels * T.MIN_VALID_RATIO).toInt())

            if (validChromatic >= minValid) {
                val smoothed   = smoothCircularHistogram(hueHist, radius = 4)
                val peakBin    = smoothed.indices.maxByOrNull { smoothed[it] } ?: 0
                val peakShare  = if (validChromatic > 0) smoothed[peakBin].toFloat() / validChromatic else 0f
                val peakFamily = mapHueToColor(peakBin)

                val sorted = familyCounts.entries
                    .map { (lbl, cnt) -> lbl to (if (validChromatic > 0) cnt.toFloat() / validChromatic else 0f) }
                    .sortedByDescending { it.second }

                val top    = sorted.getOrNull(0) ?: ("unknown" to 0f)
                val second = sorted.getOrNull(1) ?: ("unknown" to 0f)

                colorLabel = if (peakFamily != "unknown") peakFamily else top.first
                val curShare = if (validChromatic > 0) (familyCounts[colorLabel] ?: 0).toFloat() / validChromatic else 0f
                if (top.second > curShare + 0.08f) colorLabel = top.first

                val labelShare = if (validChromatic > 0) (familyCounts[colorLabel] ?: 0).toFloat() / validChromatic else top.second
                val topMargin  = top.second - second.second
                val satScore   = if (validChromatic > 0) satSum / validChromatic else 0f

                colorScore = (
                    0.35f * peakShare +
                    0.25f * labelShare +
                    0.15f * validRatio +
                    0.10f * satScore +
                    0.10f * sizeRel +
                    0.05f * hold.confidence
                ).coerceIn(0f, 1f)

                if (colorLabel == "unknown" ||
                    peakShare  < T.MIN_PEAK_SHARE ||
                    labelShare < T.MIN_LABEL_SHARE ||
                    topMargin  < T.MIN_LABEL_MARGIN
                ) {
                    colorLabel = "unknown"
                    colorScore = min(colorScore, T.UNKNOWN_SCORE_CAP)
                }

                // ── Brown 판정 (JS 확장): orange/red hue + 낮은 채도 ───────────
                // 갈색 = hue 0-22 또는 165-180 범위에 pixel이 집중되고,
                // 해당 픽셀들의 평균 채도가 낮은 경우 (선명한 red/orange 가 아님)
                if (colorLabel in listOf("orange", "red", "unknown") && brownHueCount >= minValid) {
                    val avgBrownSat = brownSatSum / brownHueCount
                    if (avgBrownSat < T.BROWN_SAT_MAX) {
                        colorLabel = "brown"
                        colorScore = (colorScore * 0.9f).coerceIn(0f, 1f)
                    }
                }
            }

            // ── Gray 판정 (JS 확장): 크로매틱 픽셀이 거의 없고 gray 지배적 ───────
            if (colorLabel == "unknown" && grayRatio >= T.GRAY_RATIO_MIN) {
                colorLabel = "gray"
                colorScore = (0.6f * grayRatio + 0.2f * sizeRel + 0.2f * hold.confidence).coerceIn(0f, 1f)
            }
        }

        // ── 패널티 조정 (JS와 동일) ────────────────────────────────────────
        if (minSide < 32f) colorScore *= 0.88f
        if (colorLabel != "black" && blackRatio > 0.25f) colorScore *= 0.92f
        if (colorLabel != "white" && whiteRatio > 0.25f) colorScore *= 0.92f
        if (colorLabel !in listOf("black", "white") && grayRatio > 0.45f) colorScore *= 0.85f
        if (hold.confidence < 0.5f) colorScore *= 0.90f

        colorScore = colorScore.coerceIn(0f, 1f)
        return hold.copy(colorLabel = colorLabel, colorScore = colorScore)
    }

    // ── 내부 헬퍼 ─────────────────────────────────────────────────────────────

    /**
     * bbox 중앙 ROI 계산.
     * - minSide < 40px → 60% 비율 (small bbox 확장)
     * - 그 외          → 45% 비율
     * same_color.js computeCentralRoi() 와 동일.
     */
    private fun computeCentralRoi(
        x1: Int, y1: Int, bw: Int, bh: Int,
        iw: Int, ih: Int
    ): RoiRect {
        val minSide   = min(bw, bh)
        val ratio     = if (minSide < 40) 0.6f else 0.45f
        val rw        = (bw * ratio).toInt().coerceIn(12, bw)
        val rh        = (bh * ratio).toInt().coerceIn(12, bh)
        val cx        = x1 + bw / 2
        val cy        = y1 + bh / 2
        val minX      = max(0, x1)
        val maxX      = max(minX, min(x1 + bw - rw, iw - rw))
        val minY      = max(0, y1)
        val maxY      = max(minY, min(y1 + bh - rh, ih - rh))
        return RoiRect(
            x      = (cx - rw / 2).coerceIn(minX, maxX),
            y      = (cy - rh / 2).coerceIn(minY, maxY),
            width  = rw,
            height = rh
        )
    }

    /**
     * RGB → HSV 변환.
     * hue 범위: 0-180 (OpenCV 방식, same_color.js rgbToHsv180 와 동일)
     * s, v 범위: 0-255
     */
    private fun rgbToHsv180(r: Float, g: Float, b: Float): Hsv {
        val rn    = r / 255f
        val gn    = g / 255f
        val bn    = b / 255f
        val max   = maxOf(rn, gn, bn)
        val min   = minOf(rn, gn, bn)
        val delta = max - min

        var hue = 0f
        if (delta != 0f) {
            hue = when (max) {
                rn   -> 60f * (((gn - bn) / delta) % 6f)
                gn   -> 60f * ((bn - rn) / delta + 2f)
                else -> 60f * ((rn - gn) / delta + 4f)
            }
        }
        if (hue < 0f) hue += 360f

        return Hsv(
            h = hue / 2f,                                  // 0-360 → 0-180
            s = if (max == 0f) 0f else (delta / max) * 255f,
            v = max * 255f
        )
    }

    /**
     * hue bin(0-179) → 색상 패밀리 이름 매핑.
     * same_color.js mapHueToColor() 와 동일.
     */
    private fun mapHueToColor(hueBin: Int): String {
        for ((label, ranges) in HUE_RANGES) {
            if (ranges.any { hueBin in it }) return label
        }
        return "unknown"
    }

    /**
     * 원형 히스토그램 스무딩 (radius 슬라이딩 합계).
     * same_color.js smoothCircularHistogram() 와 동일.
     */
    private fun smoothCircularHistogram(histogram: IntArray, radius: Int): IntArray =
        IntArray(histogram.size) { idx ->
            var total = 0
            for (offset in -radius..radius) {
                val sampleIdx = (idx + offset + histogram.size) % histogram.size
                total += histogram[sampleIdx]
            }
            total
        }

    private data class Hsv(val h: Float, val s: Float, val v: Float)
    private data class RoiRect(val x: Int, val y: Int, val width: Int, val height: Int)

    // Float 범위 연산 편의 확장 (coerceIn은 Comparable 기반, Float in range 불가)
    private operator fun ClosedFloatingPointRange<Float>.contains(v: Float) = v in this

    companion object {
        private const val TAG = "HoldColorClassifier"
    }
}
