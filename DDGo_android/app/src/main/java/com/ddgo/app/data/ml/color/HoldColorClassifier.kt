package com.ddgo.app.data.ml.color

import android.graphics.Bitmap
import android.util.Log
import com.ddgo.app.domain.model.Hold
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * same_color.js 로직을 기반으로 한 polygon/mask 기반 홀드 색상 분류기.
 *
 * 핵심 흐름:
 * 1. seg polygon 품질 검증
 * 2. polygon mask + inner erosion / outer ring 생성
 * 3. 전역/로컬 밝기 기준으로 HSV threshold 보정
 * 4. inner mask 색상 분포 분석
 * 5. detection reliability + contamination 기반 strict filter 적용
 */
@Singleton
class HoldColorClassifier @Inject constructor() {

    private data class ChromaticColorProfile(
        val label: String,
        val centers: IntArray,
        val hueTolerance: Float,
        val saturationBias: Float,
        val valueBias: Float,
        // 색상별 채도 최솟값 override (null이면 전역 chromaticSMin 사용)
        // 낮게 설정하면 채도가 낮아도(탁하거나 어두운) 해당 색으로 분류 가능
        val saturationMinOverride: Float? = null,
        // 채도 정규화 범위 스케일 (1.0=표준, >1.0=넓게=관대, <1.0=좁게=엄격)
        // 예: 2.0이면 동일한 채도에서 saturationFactor가 더 높아져 가중치 상승
        val saturationScale: Float = 1.0f,
        // 색상별 명도 최솟값 override (null이면 전역 lowValueCutoff 사용)
        val valueMinOverride: Float? = null,
        // 명도 정규화 범위 스케일 (1.0=표준, >1.0=넓게=관대)
        val valueScale: Float = 1.0f
    )

    private data class CalibrationContext(
        val meanV: Float,
        val meanS: Float,
        val sampleStep: Int
    )

    private data class ThresholdCalibration(
        val brightnessShift: Float,
        val globalMeanV: Float,
        val localMeanV: Float,
        val surroundingMeanV: Float
    )

    private data class ThresholdProfile(
        val chromaticSMin: Float,
        val graySMax: Float,
        val lowValueCutoff: Float,
        val highValueCutoff: Float,
        val blackValueCutoff: Float,
        val blackSMax: Float,
        val whiteValueCutoff: Float,
        val whiteSMax: Float,
        val blackWhiteRatio: Float,
        val calibration: ThresholdCalibration
    )

    private data class PixelPoint(
        val x: Int,
        val y: Int
    )

    private data class PixelBounds(
        val x1: Int,
        val y1: Int,
        val x2: Int,
        val y2: Int
    ) {
        val width: Int get() = max(0, x2 - x1)
        val height: Int get() = max(0, y2 - y1)
    }

    private data class DetectionQuality(
        val polygonArea: Float,
        val bboxArea: Float,
        val fillRatio: Float,
        val edgeTouchRatio: Float
    )

    private data class PreparedHold(
        val hold: Hold,
        val polygon: List<PixelPoint>,
        val bbox: PixelBounds,
        val shouldAnalyze: Boolean,
        val exclusionReason: String?,
        val warnings: Set<String>,
        val quality: DetectionQuality
    )

    private data class MaskData(
        val mask: BooleanArray,
        val innerMask: BooleanArray,
        val outerMask: BooleanArray,
        val maskPixels: Int,
        val innerPixels: Int,
        val outerPixels: Int,
        val innerMaskRatio: Float,
        val erosionRadius: Int
    )

    private data class RawMaskStats(
        val pixelCount: Int,
        val meanV: Float,
        val meanS: Float
    )

    private data class RegionStats(
        val totalPixels: Int,
        val hueHistogram: IntArray,
        val familyCounts: MutableMap<String, Int>,
        val colorWeights: MutableMap<String, Float>,
        val blackCount: Int,
        val whiteCount: Int,
        val grayCount: Int,
        val unknownCount: Int,
        val validChromaticCount: Int,
        val saturationAccumulator: Float,
        val rawSaturationAccumulator: Float,
        val valueAccumulator: Float
    )

    private data class ChromaticMembership(
        val topLabel: String,
        val normalizedWeights: Map<String, Float>
    )

    private data class DistributionEntry(
        val label: String,
        val share: Float
    )

    private data class Hsv(
        val h: Float,
        val s: Float,
        val v: Float
    )

    private data class AnalyzedHold(
        val hold: Hold,
        val colorLabel: String,
        val colorScore: Float,
        val colorStatus: String,
        val primaryColor: String?,
        val colorDistribution: Map<String, Float>,
        val rawColorScore: Float,
        val detectionReliability: Float,
        val validPixelRatio: Float,
        val warnings: Set<String>
    ) {
        fun toHold(): Hold = hold.copy(
            colorLabel = colorLabel,
            colorScore = colorScore
        )
    }

    private object Config {
        object Detection {
            const val HARD_REJECT_CONFIDENCE = 0.18f
            const val LOW_CONFIDENCE = 0.55f
            const val HARD_REJECT_POLYGON_AREA = 120f
            const val WARNING_POLYGON_AREA = 750f
            const val PREFERRED_POLYGON_AREA = 2600f
            const val HARD_REJECT_FILL_RATIO = 0.08f
            const val MIN_FILL_RATIO = 0.18f
            const val EDGE_MARGIN_PX = 4
            const val EDGE_TOUCH_WARN_RATIO = 0.18f
            const val EDGE_TOUCH_REJECT_RATIO = 0.5f
        }

        object Sampling {
            const val MIN_INNER_ERODE_PX = 1
            const val MAX_INNER_ERODE_PX = 8
            const val INNER_ERODE_RATIO = 0.08f
            const val MIN_INNER_PIXELS = 24
            const val MIN_INNER_MASK_RATIO = 0.16f
            const val THIN_INNER_MASK_RATIO = 0.22f
        }

        object Hsv {
            object Base {
                // IMPORTANT: GRAY_S_MAX < CHROMATIC_S_MIN 이어야 함
                // when 블록에서 gray 체크가 chromatic보다 먼저 실행되므로,
                // GRAY_S_MAX >= CHROMATIC_S_MIN 이면 chromatic 픽셀이 gray로 분류됨
                const val CHROMATIC_S_MIN = 35f   // 실내 조명 환경 고려해 낮춤 (원래 45)
                const val GRAY_S_MAX = 28f         // 반드시 CHROMATIC_S_MIN보다 낮아야 함
                const val LOW_VALUE_CUTOFF = 30f   // 어두운 환경 홀드 포함
                const val HIGH_VALUE_CUTOFF = 245f
                const val BLACK_VALUE_CUTOFF = 50f
                const val BLACK_S_MAX = 80f
                // WHITE_S_MAX를 낮춰야 반사광 보라/파랑 픽셀이 white로 잘못 분류되지 않음
                // 순수 흰색 홀드는 S=0~15, 반사광은 S=25~60이므로 25로 분리
                const val WHITE_VALUE_CUTOFF = 215f
                const val WHITE_S_MAX = 25f
                // black/white 분류에 필요한 최소 비율
                const val BLACK_WHITE_RATIO = 0.50f
                // gray 분류에 필요한 최소 비율
                const val GRAY_DOMINANT_RATIO = 0.50f
                // brown 판별: orange hue 범위에서 낮은 value
                const val BROWN_VALUE_MAX = 140f
                const val BROWN_S_MIN = 35f
                const val BROWN_DOMINANT_RATIO = 0.38f
            }

            object Calibration {
                const val REFERENCE_MEAN_VALUE = 140f
                const val GLOBAL_WEIGHT = 0.35f
                const val LOCAL_WEIGHT = 0.45f
                const val SURROUNDING_WEIGHT = 0.2f
                const val BRIGHTNESS_SHIFT_LIMIT = 18f
                const val SATURATION_SHIFT_LIMIT = 12f
                const val WHITE_SHIFT_LIMIT = 16f
                const val BLACK_SHIFT_LIMIT = 12f
            }
        }

        object Scoring {
            const val MIN_VALID_RATIO = 0.08f
            const val MIN_VALID_PIXEL_FLOOR = 16
            const val MIN_FILTER_VALID_RATIO = 0.10f
            // peakShare: hue 히스토그램 피크 기반 → 변동성이 커서 기준 완화
            const val MIN_PEAK_SHARE = 0.16f
            // primaryShare: yellow/orange 경계에서 weight 분산으로 0.36 미달 발생 → 완화
            const val MIN_PRIMARY_SHARE = 0.28f
            const val MIN_PRIMARY_MARGIN = 0.08f
            const val MIXED_COLOR_MARGIN = 0.07f
            const val UNKNOWN_SCORE_CAP = 0.45f
            const val CONTAMINATION_WARN = 0.33f
            const val CONTAMINATION_REJECT = 0.58f
            // 5개 점수의 곱이라 빠르게 낮아짐: 신뢰도 0.6 × 면적점수 0.56 = 0.34로 이미 미달
            // → 잘 분류된 홀드도 unknown으로 강제 리셋되는 문제의 원인이었음
            const val CONFIDENT_RELIABILITY_FLOOR = 0.15f
            const val LOW_RELIABILITY_FLOOR = 0.10f
            // detectionReliability 최소값: YOLO 신뢰도 낮아도 색 점수가 너무 낮아지지 않도록
            const val MIN_RELIABILITY_FOR_SCORE = 0.20f
        }

        object Filtering {
            // colorWeights는 chromatic 픽셀의 normalized 합산이라 52%는 너무 높음
            const val STRICT_DISTRIBUTION_FLOOR = 0.38f
        }

        object Preprocessing {
            // 채도 부스팅 배율: 영상 압축(H.264)으로 낮아진 채도를 복원
            // 1.0 = 비활성화, 1.2~1.4 권장 (너무 높으면 색 경계 왜곡)
            const val SATURATION_BOOST = 1.3f
        }
    }

    private val chromaticColorProfiles = listOf(
        // red: 360도 경계(0°, 360°)에 걸쳐 있어 두 center 사용
        // 기본 S/V 범위 사용 (orange와 pink와 구분 필요 → 좁게 유지)
        ChromaticColorProfile("red", intArrayOf(0, 179), 16f, 1.04f, 0.96f),

        // orange: 실제 orange(20-45°) → Hue/2=10-22.5, center=13으로 조정해 yellow와 겹침 감소
        // 기본 S/V 범위 사용 (red/yellow와 구분 필요)
        ChromaticColorProfile("orange", intArrayOf(13), 11f, 1.02f, 1f),

        // yellow: 45-70° → Hue/2=22.5-35
        // 비슷한 색이 없어 S/V를 넓게 허용:
        //   saturationMinOverride=20f → 탁한(채도 낮은) 노란색도 포함
        //   saturationScale=1.8f     → 낮은 채도에서도 factor가 빠르게 올라감
        //   valueMinOverride=25f     → 어두운 노란색도 포함 (기본 lowValueCutoff=30보다 낮게)
        //   valueScale=1.6f          → 어두운 픽셀도 높은 factor 반환
        ChromaticColorProfile(
            label = "yellow", centers = intArrayOf(29), hueTolerance = 15f,
            saturationBias = 0.98f, valueBias = 1.0f,
            saturationMinOverride = 20f, saturationScale = 1.8f,
            valueMinOverride = 25f, valueScale = 1.6f
        ),

        // green: 80-160° → Hue/2=40-80
        // 적당히 넓게 (lime~dark green 포함)
        ChromaticColorProfile(
            label = "green", centers = intArrayOf(58), hueTolerance = 24f,
            saturationBias = 0.98f, valueBias = 0.98f,
            saturationScale = 1.2f, valueScale = 1.1f
        ),

        // blue: 210-260° → Hue/2=105-130
        // cyan(하늘색)과 구분 필요 → S/V 엄격하게 (기본 scale 사용, saturationScale 낮춤)
        // saturationScale=0.8f → 채도 factor가 느리게 올라가므로 채도 낮은 픽셀은 weight 낮아짐
        ChromaticColorProfile(
            label = "blue", centers = intArrayOf(110), hueTolerance = 20f,
            saturationBias = 1.0f, valueBias = 0.94f,
            saturationScale = 0.85f
        ),

        // purple: 260-290° → Hue/2=130-145
        // 반사광으로 hue가 약간 벗어나거나 S가 낮아질 수 있어 약간 관대하게
        ChromaticColorProfile(
            label = "purple", centers = intArrayOf(143), hueTolerance = 20f,
            saturationBias = 1.02f, valueBias = 0.96f,
            saturationMinOverride = 22f, saturationScale = 1.3f,
            valueScale = 1.2f
        ),

        // pink/magenta: 290-330° → Hue/2=145-165
        // 기본 S/V 범위 사용 (red/purple과 구분 필요)
        ChromaticColorProfile("pink", intArrayOf(161), 14f, 0.95f, 1.06f)
    )

    private val chromaticLabels = chromaticColorProfiles.map { it.label }
    // gray와 brown을 출력 레이블에 추가
    private val outputLabels = chromaticLabels + listOf("white", "black", "gray", "brown", "unknown")

    val APP_COLOR_TO_LABEL: Map<String, String> = mapOf(
        "red" to "red",
        "orange" to "orange",
        "yellow" to "yellow",
        "green" to "green",
        "cyan" to "blue",
        "blue" to "blue",
        "purple" to "purple",
        "pink" to "pink",
        "white" to "white",
        "black" to "black",
        "brown" to "brown",
        "gray" to "gray"
    )

    fun classifyAll(bitmap: Bitmap, holds: List<Hold>): List<Hold> {
        val analyzed = analyzeAll(bitmap, holds)
        Log.d(TAG, "색상 분류 완료: ${holds.size}개 홀드")
        return analyzed.map { it.toHold() }
    }

    fun classifyAndFilter(
        bitmap: Bitmap,
        holds: List<Hold>,
        targetColorName: String,
        scoreThreshold: Float = 0.38f
    ): List<Hold> {
        val targetLabel = APP_COLOR_TO_LABEL[targetColorName.lowercase()] ?: targetColorName.lowercase()
        val analyzed = analyzeAll(bitmap, holds)
        val filtered = if (targetLabel.isBlank() || targetLabel == "all") {
            analyzed
        } else {
            analyzed.filter { result ->
                passesStrictColorFilter(result, targetLabel, scoreThreshold)
            }
        }

        Log.d(
            TAG,
            "색상 필터 결과: target='$targetColorName' -> '$targetLabel', ${analyzed.size}개 분류 -> ${filtered.size}개 매칭"
        )
        return filtered.map { it.toHold() }
    }

    fun classifySingle(bitmap: Bitmap, hold: Hold): Hold {
        val calibration = buildImageCalibrationContext(bitmap)
        return analyzeHold(bitmap, hold, calibration).toHold()
    }

    private fun analyzeAll(bitmap: Bitmap, holds: List<Hold>): List<AnalyzedHold> {
        if (holds.isEmpty()) return emptyList()
        val calibration = buildImageCalibrationContext(bitmap)
        return holds.map { hold -> analyzeHold(bitmap, hold, calibration) }
    }

    private fun analyzeHold(
        bitmap: Bitmap,
        hold: Hold,
        calibrationContext: CalibrationContext
    ): AnalyzedHold {
        val prepared = prepareHoldForAnalysis(hold, bitmap.width, bitmap.height)
        val warnings = prepared.warnings.toMutableSet()
        if (!prepared.shouldAnalyze) {
            return buildUnknownResult(
                hold = hold,
                colorStatus = prepared.exclusionReason ?: STATUS_UNKNOWN_LOW_QUALITY,
                detectionReliability = computeDetectionReliability(
                    confidence = hold.confidence,
                    polygonArea = prepared.quality.polygonArea,
                    fillRatio = prepared.quality.fillRatio,
                    edgeTouchRatio = prepared.quality.edgeTouchRatio,
                    innerMaskRatio = 0f
                ),
                validPixelRatio = 0f,
                warnings = warnings
            )
        }

        val maskData = buildPolygonMaskData(prepared.polygon, prepared.bbox)
        if (maskData.maskPixels == 0 || maskData.innerPixels == 0) {
            warnings += WARNING_INVALID_POLYGON
            warnings += WARNING_PREPROCESS_EXCLUDED
            return buildUnknownResult(
                hold = hold,
                colorStatus = STATUS_UNKNOWN_LOW_QUALITY,
                detectionReliability = computeDetectionReliability(
                    confidence = hold.confidence,
                    polygonArea = prepared.quality.polygonArea,
                    fillRatio = prepared.quality.fillRatio,
                    edgeTouchRatio = prepared.quality.edgeTouchRatio,
                    innerMaskRatio = 0f
                ),
                validPixelRatio = 0f,
                warnings = warnings
            )
        }

        val bboxPixels = IntArray(prepared.bbox.width * prepared.bbox.height)
        bitmap.getPixels(
            bboxPixels,
            0,
            prepared.bbox.width,
            prepared.bbox.x1,
            prepared.bbox.y1,
            prepared.bbox.width,
            prepared.bbox.height
        )

        // threshold 캘리브레이션은 원본 픽셀 기준 (실제 밝기/채도 정보 보존)
        val polygonRawStats = collectRawMaskStats(bboxPixels, maskData.mask)
        val surroundingRawStats = collectRawMaskStats(bboxPixels, maskData.outerMask)
        val thresholdProfile = calibrateThresholdProfile(
            globalStats = calibrationContext,
            localStats = polygonRawStats,
            surroundingStats = surroundingRawStats
        )

        // 색상 분석은 채도 부스팅된 픽셀 사용:
        // 영상 압축(H.264)으로 낮아진 채도를 복원해 chromatic 픽셀 비율 상승
        val colorPixels = boostPixelSaturation(bboxPixels, Config.Preprocessing.SATURATION_BOOST)
        val innerStats = collectRegionColorStats(colorPixels, maskData.innerMask, thresholdProfile)
        val outerStats = collectRegionColorStats(colorPixels, maskData.outerMask, thresholdProfile)
        val innerDistribution = buildColorDistribution(innerStats)
        val outerDistribution = buildColorDistribution(outerStats)
        // gray, brown, white, black은 별도 경로로 분류하므로 chromatic 후보에서 제외
        val topEntries = getTopDistributionEntries(innerDistribution, 2, setOf("unknown", "gray", "brown", "white", "black"))
        val primaryCandidate = topEntries.getOrNull(0)
        val secondaryCandidate = topEntries.getOrNull(1)
        val primaryColor = primaryCandidate?.label

        val peakHistogram = smoothCircularHistogram(innerStats.hueHistogram, 4)
        val peakHueBin = if (innerStats.validChromaticCount > 0) argMax(peakHistogram) else null
        val peakShare = if (peakHueBin != null && innerStats.validChromaticCount > 0) {
            peakHistogram[peakHueBin].toFloat() / innerStats.validChromaticCount.toFloat()
        } else {
            0f
        }

        val totalPixels = innerStats.totalPixels
        val validPixelRatio = if (totalPixels > 0) {
            innerStats.validChromaticCount.toFloat() / totalPixels.toFloat()
        } else {
            0f
        }
        val blackRatio = if (totalPixels > 0) innerStats.blackCount.toFloat() / totalPixels else 0f
        val whiteRatio = if (totalPixels > 0) innerStats.whiteCount.toFloat() / totalPixels else 0f
        val grayRatio = if (totalPixels > 0) innerStats.grayCount.toFloat() / totalPixels else 0f
        val labelShare = primaryCandidate?.share ?: 0f
        val topMargin = labelShare - (secondaryCandidate?.share ?: 0f)
        val saturationScore = if (innerStats.validChromaticCount > 0) {
            innerStats.saturationAccumulator / innerStats.validChromaticCount.toFloat()
        } else {
            0f
        }
        val outerRingContamination = computeOuterRingContamination(primaryColor, outerDistribution)

        if (maskData.innerMaskRatio < Config.Sampling.THIN_INNER_MASK_RATIO) {
            warnings += WARNING_THIN_INNER_MASK
        }
        if (outerRingContamination >= Config.Scoring.CONTAMINATION_WARN) {
            warnings += WARNING_BOUNDARY_CONTAMINATION
        }
        if (grayRatio > 0.45f) warnings += WARNING_LOW_SATURATION_MIX
        if (primaryColor != "black" && blackRatio > 0.25f) warnings += WARNING_SHADOW_MIX
        if (primaryColor != "white" && whiteRatio > 0.25f) warnings += WARNING_GLARE_MIX

        val detectionReliability = computeDetectionReliability(
            confidence = hold.confidence,
            polygonArea = prepared.quality.polygonArea,
            fillRatio = prepared.quality.fillRatio,
            edgeTouchRatio = prepared.quality.edgeTouchRatio,
            innerMaskRatio = maskData.innerMaskRatio
        )

        var colorLabel = "unknown"
        var colorStatus = STATUS_UNKNOWN_LOW_CHROMA
        var rawColorScore = 0f

        val minimumValidPixels = max(
            Config.Scoring.MIN_VALID_PIXEL_FLOOR,
            floor(totalPixels * Config.Scoring.MIN_VALID_RATIO).toInt()
        )

        // brown 판별: orange hue 범위에서 value가 낮고 saturation이 중간인 픽셀 비율
        val brownRatio = if (totalPixels > 0) {
            computeBrownRatio(colorPixels, maskData.innerMask, thresholdProfile)
        } else 0f

        if (blackRatio >= thresholdProfile.blackWhiteRatio && whiteRatio < 0.3f) {
            colorLabel = "black"
            colorStatus = STATUS_CLASSIFIED
            rawColorScore = clamp(
                0.64f * blackRatio +
                    0.14f * maskData.innerMaskRatio +
                    0.1f * (1f - outerRingContamination) +
                    0.12f * hold.confidence
            )
        } else if (whiteRatio >= thresholdProfile.blackWhiteRatio && blackRatio < 0.3f) {
            colorLabel = "white"
            colorStatus = STATUS_CLASSIFIED
            rawColorScore = clamp(
                0.64f * whiteRatio +
                    0.14f * maskData.innerMaskRatio +
                    0.1f * (1f - outerRingContamination) +
                    0.12f * hold.confidence
            )
        } else if (grayRatio >= Config.Hsv.Base.GRAY_DOMINANT_RATIO && blackRatio < 0.25f && whiteRatio < 0.25f && validPixelRatio < 0.15f) {
            // gray 홀드: 채도가 낮고 gray 픽셀이 지배적인 경우
            colorLabel = "gray"
            colorStatus = STATUS_CLASSIFIED
            rawColorScore = clamp(
                0.60f * grayRatio +
                    0.15f * maskData.innerMaskRatio +
                    0.1f * (1f - outerRingContamination) +
                    0.15f * hold.confidence
            )
        } else if (brownRatio >= Config.Hsv.Base.BROWN_DOMINANT_RATIO && validPixelRatio < 0.25f) {
            // brown 홀드: orange hue + 낮은 value/saturation 조합
            colorLabel = "brown"
            colorStatus = STATUS_CLASSIFIED
            rawColorScore = clamp(
                0.60f * brownRatio +
                    0.15f * maskData.innerMaskRatio +
                    0.1f * (1f - outerRingContamination) +
                    0.15f * hold.confidence
            )
        } else if (
            innerStats.validChromaticCount < minimumValidPixels ||
            validPixelRatio < Config.Scoring.MIN_VALID_RATIO
        ) {
            warnings += WARNING_FEW_VALID_PIXELS
            if (grayRatio > 0.55f) warnings += WARNING_GRAY_DOMINANT
            colorStatus = if (detectionReliability < Config.Scoring.LOW_RELIABILITY_FLOOR) {
                STATUS_UNKNOWN_LOW_CONF
            } else {
                STATUS_UNKNOWN_LOW_CHROMA
            }
            rawColorScore = clamp(
                0.4f * labelShare +
                    0.2f * validPixelRatio +
                    0.2f * saturationScore +
                    0.2f * (1f - outerRingContamination)
            )
        } else {
            val dominanceScore = clamp(
                (topMargin - Config.Scoring.MIXED_COLOR_MARGIN) / 0.25f
            )
            rawColorScore = clamp(
                0.34f * labelShare +
                    0.18f * peakShare +
                    0.16f * dominanceScore +
                    0.12f * validPixelRatio +
                    0.1f * saturationScore +
                    0.05f * maskData.innerMaskRatio +
                    0.05f * (1f - outerRingContamination)
            )

            when {
                primaryColor == null -> {
                    warnings += WARNING_AMBIGUOUS_HUE
                    colorStatus = STATUS_UNKNOWN_LOW_CHROMA
                }
                labelShare < Config.Scoring.MIN_PRIMARY_SHARE ||
                    peakShare < Config.Scoring.MIN_PEAK_SHARE -> {
                    warnings += WARNING_AMBIGUOUS_HUE
                    colorStatus = STATUS_UNKNOWN_LOW_CHROMA
                }
                topMargin < Config.Scoring.MIN_PRIMARY_MARGIN -> {
                    warnings += WARNING_MIXED_COLOR
                    colorStatus = STATUS_MIXED_COLOR
                }
                outerRingContamination >= Config.Scoring.CONTAMINATION_REJECT -> {
                    colorStatus = STATUS_UNKNOWN_CONTAMINATED
                }
                else -> {
                    colorLabel = primaryColor
                    colorStatus = STATUS_CLASSIFIED
                }
            }
        }

        if (detectionReliability < Config.Scoring.CONFIDENT_RELIABILITY_FLOOR) {
            warnings += WARNING_LOW_DETECTION_RELIABILITY
            if (colorStatus == STATUS_CLASSIFIED) {
                colorLabel = "unknown"
                colorStatus = if (hold.confidence < Config.Detection.LOW_CONFIDENCE) {
                    STATUS_UNKNOWN_LOW_CONF
                } else {
                    STATUS_UNKNOWN_LOW_QUALITY
                }
            }
        }

        rawColorScore = roundTo(rawColorScore, 3)
        // detectionReliability 최솟값을 높여 YOLO 신뢰도가 낮아도 색 점수가 너무 낮아지지 않도록
        var colorScore = clamp(rawColorScore * max(detectionReliability, Config.Scoring.MIN_RELIABILITY_FOR_SCORE))
        if (colorLabel == "unknown") {
            colorScore = min(colorScore, Config.Scoring.UNKNOWN_SCORE_CAP)
        }
        colorScore = roundTo(colorScore, 3)

        return AnalyzedHold(
            hold = hold,
            colorLabel = colorLabel,
            colorScore = colorScore,
            colorStatus = colorStatus,
            primaryColor = primaryColor,
            colorDistribution = innerDistribution,
            rawColorScore = rawColorScore,
            detectionReliability = roundTo(detectionReliability, 3),
            validPixelRatio = roundTo(validPixelRatio, 3),
            warnings = warnings
        )
    }

    private fun buildUnknownResult(
        hold: Hold,
        colorStatus: String,
        detectionReliability: Float,
        validPixelRatio: Float,
        warnings: Set<String>
    ): AnalyzedHold = AnalyzedHold(
        hold = hold,
        colorLabel = "unknown",
        colorScore = 0f,
        colorStatus = colorStatus,
        primaryColor = null,
        colorDistribution = buildColorDistributionTemplate(mapOf("unknown" to 1f)),
        rawColorScore = 0f,
        detectionReliability = roundTo(detectionReliability, 3),
        validPixelRatio = roundTo(validPixelRatio, 3),
        warnings = warnings
    )

    private fun prepareHoldForAnalysis(
        hold: Hold,
        imageWidth: Int,
        imageHeight: Int
    ): PreparedHold {
        val fallbackBounds = resolveFallbackBounds(hold, imageWidth, imageHeight)
        if (fallbackBounds == null) {
            return PreparedHold(
                hold = hold,
                polygon = emptyList(),
                bbox = emptyBounds(),
                shouldAnalyze = false,
                exclusionReason = STATUS_INVALID_POLYGON,
                warnings = setOf(WARNING_INVALID_POLYGON, WARNING_PREPROCESS_EXCLUDED),
                quality = DetectionQuality(0f, 0f, 0f, 0f)
            )
        }

        val rawPolygon = hold.polygon
            .takeIf { it.size >= 3 }
            ?.map { point ->
                PixelPoint(
                    x = clampInt((point.x * imageWidth).roundToInt(), 0, max(imageWidth - 1, 0)),
                    y = clampInt((point.y * imageHeight).roundToInt(), 0, max(imageHeight - 1, 0))
                )
            }
            .orEmpty()

        val polygon = sanitizePolygon(
            points = if (rawPolygon.size >= 3) rawPolygon else boundsToPolygon(fallbackBounds),
            imageWidth = imageWidth,
            imageHeight = imageHeight
        )
        val bbox = computePolygonBounds(polygon, imageWidth, imageHeight)

        if (bbox == null || polygon.size < 3) {
            return PreparedHold(
                hold = hold,
                polygon = polygon,
                bbox = fallbackBounds,
                shouldAnalyze = false,
                exclusionReason = STATUS_INVALID_POLYGON,
                warnings = setOf(WARNING_INVALID_POLYGON, WARNING_PREPROCESS_EXCLUDED),
                quality = DetectionQuality(0f, 0f, 0f, 0f)
            )
        }

        val polygonArea = computePolygonArea(polygon)
        val bboxArea = (bbox.width * bbox.height).toFloat()
        val fillRatio = if (bboxArea > 0f) clamp(polygonArea / bboxArea) else 0f
        val edgeTouchRatio = computeEdgeTouchRatio(polygon, bbox, imageWidth, imageHeight)

        val warnings = linkedSetOf<String>()
        var shouldAnalyze = true
        var exclusionReason: String? = null

        if (hold.confidence < Config.Detection.LOW_CONFIDENCE) warnings += WARNING_LOW_DET_CONF
        if (polygonArea < Config.Detection.WARNING_POLYGON_AREA) warnings += WARNING_SMALL_POLYGON
        if (fillRatio < Config.Detection.MIN_FILL_RATIO) warnings += WARNING_LOW_FILL_RATIO
        if (edgeTouchRatio >= Config.Detection.EDGE_TOUCH_WARN_RATIO) warnings += WARNING_EDGE_TOUCH

        when {
            hold.confidence < Config.Detection.HARD_REJECT_CONFIDENCE -> {
                shouldAnalyze = false
                exclusionReason = STATUS_UNKNOWN_LOW_CONF
            }
            polygonArea < Config.Detection.HARD_REJECT_POLYGON_AREA -> {
                shouldAnalyze = false
                exclusionReason = STATUS_UNKNOWN_LOW_QUALITY
            }
            fillRatio < Config.Detection.HARD_REJECT_FILL_RATIO -> {
                shouldAnalyze = false
                exclusionReason = STATUS_UNKNOWN_LOW_QUALITY
            }
            edgeTouchRatio >= Config.Detection.EDGE_TOUCH_REJECT_RATIO -> {
                shouldAnalyze = false
                exclusionReason = STATUS_UNKNOWN_LOW_QUALITY
            }
        }

        if (!shouldAnalyze) warnings += WARNING_PREPROCESS_EXCLUDED

        return PreparedHold(
            hold = hold,
            polygon = polygon,
            bbox = bbox,
            shouldAnalyze = shouldAnalyze,
            exclusionReason = exclusionReason,
            warnings = warnings,
            quality = DetectionQuality(
                polygonArea = polygonArea,
                bboxArea = bboxArea,
                fillRatio = fillRatio,
                edgeTouchRatio = edgeTouchRatio
            )
        )
    }

    private fun resolveFallbackBounds(
        hold: Hold,
        imageWidth: Int,
        imageHeight: Int
    ): PixelBounds? {
        if (imageWidth <= 0 || imageHeight <= 0) return null

        val maxX = max(imageWidth - 1, 0)
        val maxY = max(imageHeight - 1, 0)
        val x1 = clampInt(floor(hold.boundingBox.left * imageWidth).toInt(), 0, maxX)
        val y1 = clampInt(floor(hold.boundingBox.top * imageHeight).toInt(), 0, maxY)
        val x2 = clampInt(ceil(hold.boundingBox.right * imageWidth).toInt(), x1 + 1, imageWidth)
        val y2 = clampInt(ceil(hold.boundingBox.bottom * imageHeight).toInt(), y1 + 1, imageHeight)
        if (x2 <= x1 || y2 <= y1) return null
        return PixelBounds(x1, y1, x2, y2)
    }

    private fun sanitizePolygon(
        points: List<PixelPoint>,
        imageWidth: Int,
        imageHeight: Int
    ): List<PixelPoint> {
        val maxX = max(imageWidth - 1, 0)
        val maxY = max(imageHeight - 1, 0)
        val sanitized = mutableListOf<PixelPoint>()

        points.forEach { point ->
            val current = PixelPoint(
                x = clampInt(point.x, 0, maxX),
                y = clampInt(point.y, 0, maxY)
            )
            if (sanitized.lastOrNull() != current) sanitized += current
        }

        if (sanitized.size > 1 && sanitized.first() == sanitized.last()) {
            sanitized.removeAt(sanitized.lastIndex)
        }
        return sanitized
    }

    private fun boundsToPolygon(bounds: PixelBounds): List<PixelPoint> = listOf(
        PixelPoint(bounds.x1, bounds.y1),
        PixelPoint(bounds.x2 - 1, bounds.y1),
        PixelPoint(bounds.x2 - 1, bounds.y2 - 1),
        PixelPoint(bounds.x1, bounds.y2 - 1)
    )

    private fun computePolygonBounds(
        points: List<PixelPoint>,
        imageWidth: Int,
        imageHeight: Int
    ): PixelBounds? {
        if (points.size < 3 || imageWidth <= 0 || imageHeight <= 0) return null

        val minX = points.minOf { it.x }
        val minY = points.minOf { it.y }
        val maxX = points.maxOf { it.x }
        val maxY = points.maxOf { it.y }

        val x1 = clampInt(minX, 0, imageWidth - 1)
        val y1 = clampInt(minY, 0, imageHeight - 1)
        val x2 = clampInt(maxX + 1, x1 + 1, imageWidth)
        val y2 = clampInt(maxY + 1, y1 + 1, imageHeight)
        return PixelBounds(x1, y1, x2, y2)
    }

    private fun computePolygonArea(points: List<PixelPoint>): Float {
        if (points.size < 3) return 0f

        var area = 0.0
        for (index in points.indices) {
            val current = points[index]
            val next = points[(index + 1) % points.size]
            area += current.x.toDouble() * next.y.toDouble()
            area -= next.x.toDouble() * current.y.toDouble()
        }
        return abs((area / 2.0).toFloat())
    }

    private fun computeEdgeTouchRatio(
        points: List<PixelPoint>,
        bbox: PixelBounds,
        imageWidth: Int,
        imageHeight: Int
    ): Float {
        if (points.isEmpty()) return 0f

        val margin = Config.Detection.EDGE_MARGIN_PX
        val maxX = max(imageWidth - 1, 0)
        val maxY = max(imageHeight - 1, 0)
        val edgePointCount = points.count { point ->
            point.x <= margin ||
                point.y <= margin ||
                point.x >= maxX - margin ||
                point.y >= maxY - margin
        }
        val bboxTouchCount = listOf(
            bbox.x1 <= margin,
            bbox.y1 <= margin,
            bbox.x2 >= imageWidth - margin,
            bbox.y2 >= imageHeight - margin
        ).count { it }

        val pointRatio = edgePointCount.toFloat() / max(points.size, 1).toFloat()
        val bboxRatio = bboxTouchCount.toFloat() / 4f
        return clamp((pointRatio * 0.7f) + (bboxRatio * 0.3f))
    }

    private fun buildPolygonMaskData(
        polygon: List<PixelPoint>,
        bbox: PixelBounds
    ): MaskData {
        if (bbox.width <= 0 || bbox.height <= 0 || polygon.size < 3) {
            return MaskData(
                mask = BooleanArray(0),
                innerMask = BooleanArray(0),
                outerMask = BooleanArray(0),
                maskPixels = 0,
                innerPixels = 0,
                outerPixels = 0,
                innerMaskRatio = 0f,
                erosionRadius = 0
            )
        }

        val mask = BooleanArray(bbox.width * bbox.height)
        var maskPixels = 0
        for (localY in 0 until bbox.height) {
            val worldY = bbox.y1 + localY + 0.5f
            val rowOffset = localY * bbox.width
            for (localX in 0 until bbox.width) {
                val worldX = bbox.x1 + localX + 0.5f
                if (pointInPolygon(worldX, worldY, polygon)) {
                    mask[rowOffset + localX] = true
                    maskPixels++
                }
            }
        }

        var resolvedRadius = resolveInnerErosionRadius(bbox, maskPixels)
        var innerMask = BooleanArray(mask.size)
        var innerPixels = 0
        while (resolvedRadius >= 0) {
            innerMask = createErodedMask(mask, bbox.width, bbox.height, resolvedRadius)
            innerPixels = sumBinaryMask(innerMask)
            val enoughInnerPixels = innerPixels >= max(
                Config.Sampling.MIN_INNER_PIXELS,
                floor(maskPixels * Config.Sampling.MIN_INNER_MASK_RATIO).toInt()
            )
            if (enoughInnerPixels || resolvedRadius == 0) break
            resolvedRadius--
        }

        val outerMask = BooleanArray(mask.size)
        var outerPixels = 0
        for (index in mask.indices) {
            if (mask[index] && !innerMask[index]) {
                outerMask[index] = true
                outerPixels++
            }
        }

        return MaskData(
            mask = mask,
            innerMask = innerMask,
            outerMask = outerMask,
            maskPixels = maskPixels,
            innerPixels = innerPixels,
            outerPixels = outerPixels,
            innerMaskRatio = if (maskPixels > 0) innerPixels.toFloat() / maskPixels.toFloat() else 0f,
            erosionRadius = max(resolvedRadius, 0)
        )
    }

    private fun resolveInnerErosionRadius(
        bbox: PixelBounds,
        maskPixels: Int
    ): Int {
        val minDimension = max(1, min(bbox.width, bbox.height))
        val relativeRadius = round(minDimension * Config.Sampling.INNER_ERODE_RATIO).toInt()
        val areaRadius = round(sqrt(maskPixels.toFloat()) / 18f).toInt()
        return clampInt(
            max(relativeRadius, areaRadius),
            Config.Sampling.MIN_INNER_ERODE_PX,
            Config.Sampling.MAX_INNER_ERODE_PX
        )
    }

    private fun createErodedMask(
        mask: BooleanArray,
        width: Int,
        height: Int,
        radius: Int
    ): BooleanArray {
        if (mask.isEmpty()) return BooleanArray(0)
        if (radius <= 0) return mask.copyOf()

        val integral = buildMaskIntegralImage(mask, width, height)
        val eroded = BooleanArray(mask.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                if (!mask[index]) continue

                val x1 = max(0, x - radius)
                val y1 = max(0, y - radius)
                val x2 = min(width - 1, x + radius)
                val y2 = min(height - 1, y + radius)
                val area = (x2 - x1 + 1) * (y2 - y1 + 1)
                val sum = sumIntegralRect(integral, width, x1, y1, x2, y2)
                if (sum == area) eroded[index] = true
            }
        }
        return eroded
    }

    private fun buildMaskIntegralImage(
        mask: BooleanArray,
        width: Int,
        height: Int
    ): IntArray {
        val stride = width + 1
        val integral = IntArray((width + 1) * (height + 1))
        for (y in 1..height) {
            var rowSum = 0
            for (x in 1..width) {
                if (mask[(y - 1) * width + (x - 1)]) rowSum += 1
                integral[y * stride + x] = integral[(y - 1) * stride + x] + rowSum
            }
        }
        return integral
    }

    private fun sumIntegralRect(
        integral: IntArray,
        width: Int,
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int
    ): Int {
        val stride = width + 1
        val ax = x1
        val ay = y1
        val bx = x2 + 1
        val by = y2 + 1
        return integral[by * stride + bx] -
            integral[ay * stride + bx] -
            integral[by * stride + ax] +
            integral[ay * stride + ax]
    }

    private fun sumBinaryMask(mask: BooleanArray): Int {
        var total = 0
        mask.forEach { if (it) total += 1 }
        return total
    }

    private fun buildImageCalibrationContext(bitmap: Bitmap): CalibrationContext {
        val imageWidth = bitmap.width
        val imageHeight = bitmap.height
        if (imageWidth <= 0 || imageHeight <= 0) {
            return CalibrationContext(
                meanV = Config.Hsv.Calibration.REFERENCE_MEAN_VALUE,
                meanS = 96f,
                sampleStep = 1
            )
        }

        val pixels = IntArray(imageWidth * imageHeight)
        bitmap.getPixels(pixels, 0, imageWidth, 0, 0, imageWidth, imageHeight)
        val step = max(4, floor(min(imageWidth, imageHeight) / 160f).toInt())

        var count = 0
        var valueSum = 0f
        var saturationSum = 0f
        for (y in 0 until imageHeight step step) {
            val rowOffset = y * imageWidth
            for (x in 0 until imageWidth step step) {
                val pixel = pixels[rowOffset + x]
                val hsv = rgbToHsv180(
                    r = ((pixel shr 16) and 0xFF).toFloat(),
                    g = ((pixel shr 8) and 0xFF).toFloat(),
                    b = (pixel and 0xFF).toFloat()
                )
                valueSum += hsv.v
                saturationSum += hsv.s
                count++
            }
        }

        return CalibrationContext(
            meanV = if (count > 0) valueSum / count.toFloat() else Config.Hsv.Calibration.REFERENCE_MEAN_VALUE,
            meanS = if (count > 0) saturationSum / count.toFloat() else 96f,
            sampleStep = step
        )
    }

    private fun collectRawMaskStats(
        pixels: IntArray,
        mask: BooleanArray
    ): RawMaskStats {
        var pixelCount = 0
        var valueSum = 0f
        var saturationSum = 0f
        for (index in mask.indices) {
            if (!mask[index]) continue
            val pixel = pixels[index]
            val hsv = rgbToHsv180(
                r = ((pixel shr 16) and 0xFF).toFloat(),
                g = ((pixel shr 8) and 0xFF).toFloat(),
                b = (pixel and 0xFF).toFloat()
            )
            pixelCount++
            valueSum += hsv.v
            saturationSum += hsv.s
        }

        return RawMaskStats(
            pixelCount = pixelCount,
            meanV = if (pixelCount > 0) valueSum / pixelCount.toFloat() else Config.Hsv.Calibration.REFERENCE_MEAN_VALUE,
            meanS = if (pixelCount > 0) saturationSum / pixelCount.toFloat() else 96f
        )
    }

    private fun calibrateThresholdProfile(
        globalStats: CalibrationContext,
        localStats: RawMaskStats,
        surroundingStats: RawMaskStats
    ): ThresholdProfile {
        val localMeanV = if (localStats.pixelCount > 0) localStats.meanV else globalStats.meanV
        val surroundingMeanV = if (surroundingStats.pixelCount > 0) surroundingStats.meanV else localMeanV
        val combinedMeanV = weightedAverage(
            listOf(
                globalStats.meanV to Config.Hsv.Calibration.GLOBAL_WEIGHT,
                localMeanV to Config.Hsv.Calibration.LOCAL_WEIGHT,
                surroundingMeanV to Config.Hsv.Calibration.SURROUNDING_WEIGHT
            )
        )
        val rawBrightnessShift = combinedMeanV - Config.Hsv.Calibration.REFERENCE_MEAN_VALUE
        val brightnessShift = clamp(
            rawBrightnessShift,
            -Config.Hsv.Calibration.BRIGHTNESS_SHIFT_LIMIT,
            Config.Hsv.Calibration.BRIGHTNESS_SHIFT_LIMIT
        )

        val calibratedChromaticSMin = clamp(
            Config.Hsv.Base.CHROMATIC_S_MIN + (brightnessShift * 0.35f),
            Config.Hsv.Base.CHROMATIC_S_MIN - Config.Hsv.Calibration.SATURATION_SHIFT_LIMIT,
            Config.Hsv.Base.CHROMATIC_S_MIN + Config.Hsv.Calibration.SATURATION_SHIFT_LIMIT
        )
        // graySMax는 항상 chromaticSMin보다 낮아야 함 (역전 시 gray 체크가 chromatic을 삼킴)
        val calibratedGraySMax = min(
            clamp(Config.Hsv.Base.GRAY_S_MAX + (brightnessShift * 0.12f), 15f, 40f),
            calibratedChromaticSMin - 4f
        )

        return ThresholdProfile(
            chromaticSMin = calibratedChromaticSMin,
            graySMax = calibratedGraySMax,
            lowValueCutoff = clamp(Config.Hsv.Base.LOW_VALUE_CUTOFF + (brightnessShift * 0.45f), 24f, 80f),
            highValueCutoff = clamp(
                Config.Hsv.Base.HIGH_VALUE_CUTOFF - max(0f, brightnessShift * 0.32f),
                215f,
                250f
            ),
            blackValueCutoff = clamp(
                Config.Hsv.Base.BLACK_VALUE_CUTOFF + (brightnessShift * 0.26f),
                Config.Hsv.Base.BLACK_VALUE_CUTOFF - Config.Hsv.Calibration.BLACK_SHIFT_LIMIT,
                Config.Hsv.Base.BLACK_VALUE_CUTOFF + Config.Hsv.Calibration.BLACK_SHIFT_LIMIT
            ),
            blackSMax = Config.Hsv.Base.BLACK_S_MAX,
            whiteValueCutoff = clamp(
                Config.Hsv.Base.WHITE_VALUE_CUTOFF + (brightnessShift * 0.4f),
                Config.Hsv.Base.WHITE_VALUE_CUTOFF - Config.Hsv.Calibration.WHITE_SHIFT_LIMIT,
                Config.Hsv.Base.WHITE_VALUE_CUTOFF + Config.Hsv.Calibration.WHITE_SHIFT_LIMIT
            ),
            whiteSMax = clamp(Config.Hsv.Base.WHITE_S_MAX + (brightnessShift * 0.05f), 18f, 30f),
            blackWhiteRatio = Config.Hsv.Base.BLACK_WHITE_RATIO,
            calibration = ThresholdCalibration(
                brightnessShift = brightnessShift,
                globalMeanV = globalStats.meanV,
                localMeanV = localMeanV,
                surroundingMeanV = surroundingMeanV
            )
        )
    }

    private fun collectRegionColorStats(
        pixels: IntArray,
        mask: BooleanArray,
        thresholds: ThresholdProfile
    ): RegionStats {
        var totalPixels = 0
        val hueHistogram = IntArray(180)
        val familyCounts = chromaticLabels.associateWith { 0 }.toMutableMap()
        val colorWeights = chromaticLabels.associateWith { 0f }.toMutableMap()
        var blackCount = 0
        var whiteCount = 0
        var grayCount = 0
        var unknownCount = 0
        var validChromaticCount = 0
        var saturationAccumulator = 0f
        var rawSaturationAccumulator = 0f
        var valueAccumulator = 0f

        for (index in mask.indices) {
            if (!mask[index]) continue

            val pixel = pixels[index]
            val hsv = rgbToHsv180(
                r = ((pixel shr 16) and 0xFF).toFloat(),
                g = ((pixel shr 8) and 0xFF).toFloat(),
                b = (pixel and 0xFF).toFloat()
            )

            totalPixels++
            valueAccumulator += hsv.v / 255f
            rawSaturationAccumulator += hsv.s / 255f

            when {
                isWhitePixel(hsv, thresholds) -> whiteCount++
                isBlackPixel(hsv, thresholds) -> blackCount++
                isGrayPixel(hsv, thresholds) -> grayCount++
                !isChromaticPixel(hsv, thresholds) -> unknownCount++
                else -> {
                    val membership = getChromaticMembership(hsv, thresholds)
                    if (membership == null) {
                        unknownCount++
                    } else {
                        validChromaticCount++
                        saturationAccumulator += hsv.s / 255f
                        val hueBin = clampInt(floor(hsv.h).toInt(), 0, 179)
                        hueHistogram[hueBin] += 1
                        membership.normalizedWeights.forEach { (label, weight) ->
                            colorWeights[label] = (colorWeights[label] ?: 0f) + weight
                        }
                        familyCounts[membership.topLabel] = (familyCounts[membership.topLabel] ?: 0) + 1
                    }
                }
            }
        }

        return RegionStats(
            totalPixels = totalPixels,
            hueHistogram = hueHistogram,
            familyCounts = familyCounts,
            colorWeights = colorWeights,
            blackCount = blackCount,
            whiteCount = whiteCount,
            grayCount = grayCount,
            unknownCount = unknownCount,
            validChromaticCount = validChromaticCount,
            saturationAccumulator = saturationAccumulator,
            rawSaturationAccumulator = rawSaturationAccumulator,
            valueAccumulator = valueAccumulator
        )
    }

    private fun isWhitePixel(hsv: Hsv, thresholds: ThresholdProfile): Boolean =
        hsv.s <= thresholds.whiteSMax && hsv.v >= thresholds.whiteValueCutoff

    private fun isBlackPixel(hsv: Hsv, thresholds: ThresholdProfile): Boolean =
        hsv.s <= thresholds.blackSMax && hsv.v <= thresholds.blackValueCutoff

    private fun isGrayPixel(hsv: Hsv, thresholds: ThresholdProfile): Boolean =
        hsv.s <= thresholds.graySMax &&
            hsv.v > thresholds.blackValueCutoff &&
            hsv.v < thresholds.whiteValueCutoff

    private fun isChromaticPixel(hsv: Hsv, thresholds: ThresholdProfile): Boolean =
        hsv.s >= thresholds.chromaticSMin &&
            hsv.v >= thresholds.lowValueCutoff &&
            hsv.v <= thresholds.highValueCutoff

    private fun getChromaticMembership(
        hsv: Hsv,
        thresholds: ThresholdProfile
    ): ChromaticMembership? {
        val rawWeights = mutableMapOf<String, Float>()
        var totalWeight = 0f
        var topLabel = "unknown"
        var topWeight = 0f
        chromaticColorProfiles.forEach { profile ->
            val distance = profile.centers.minOf { center ->
                circularHueDistance(hsv.h, center.toFloat())
            }
            val hueScore = clamp(1f - (distance / profile.hueTolerance))
            if (hueScore <= 0f) {
                rawWeights[profile.label] = 0f
                return@forEach
            }

            // 색상별 채도 factor: saturationMinOverride가 있으면 해당 색의 기준으로 계산
            // saturationScale > 1이면 정규화 범위가 넓어져 낮은 채도에서도 높은 factor 반환
            val saturationFactor = if (profile.saturationMinOverride != null) {
                val sMin = profile.saturationMinOverride
                val sRange = max(1f, (255f - sMin) / profile.saturationScale)
                clamp((hsv.s - sMin) / sRange)
            } else {
                val sRange = max(1f, (255f - thresholds.chromaticSMin) / profile.saturationScale)
                clamp((hsv.s - thresholds.chromaticSMin) / sRange)
            }

            // 색상별 명도 factor: valueMinOverride가 있으면 해당 색의 기준으로 계산
            // valueScale > 1이면 어두운 픽셀도 높은 factor 반환
            val valueFactor = if (profile.valueMinOverride != null) {
                val vMin = profile.valueMinOverride
                val vRange = max(1f, (thresholds.highValueCutoff - vMin) / profile.valueScale)
                clamp((hsv.v - vMin) / vRange)
            } else {
                val vRange = max(1f, (thresholds.highValueCutoff - thresholds.lowValueCutoff) / profile.valueScale)
                clamp((hsv.v - thresholds.lowValueCutoff) / vRange)
            }

            val weight = (hueScore * hueScore) *
                (0.55f + (0.45f * saturationFactor * profile.saturationBias)) *
                (0.65f + (0.35f * valueFactor * profile.valueBias))

            rawWeights[profile.label] = weight
            totalWeight += weight
            if (weight > topWeight) {
                topWeight = weight
                topLabel = profile.label
            }
        }

        if (topWeight < 0.08f || totalWeight <= 0f) return null

        val normalizedWeights = rawWeights.mapValues { (_, weight) -> weight / totalWeight }
        return ChromaticMembership(topLabel = topLabel, normalizedWeights = normalizedWeights)
    }

    private fun buildColorDistribution(stats: RegionStats): Map<String, Float> {
        if (stats.totalPixels == 0) {
            return buildColorDistributionTemplate(mapOf("unknown" to 1f))
        }

        val total = stats.totalPixels.toFloat()
        val distribution = buildColorDistributionTemplate()
        chromaticLabels.forEach { label ->
            distribution[label] = roundTo((stats.colorWeights[label] ?: 0f) / total, 3)
        }
        distribution["white"] = roundTo(stats.whiteCount.toFloat() / total, 3)
        distribution["black"] = roundTo(stats.blackCount.toFloat() / total, 3)
        // gray를 distribution에 명시적으로 포함
        distribution["gray"] = roundTo(stats.grayCount.toFloat() / total, 3)
        // unknown은 grayCount를 제외한 순수 unknown만 포함
        distribution["unknown"] = roundTo(stats.unknownCount.toFloat() / total, 3)
        return distribution
    }

    private fun buildColorDistributionTemplate(
        overrides: Map<String, Float> = emptyMap()
    ): MutableMap<String, Float> = outputLabels.associateWith { label ->
        roundTo(overrides[label] ?: 0f, 3)
    }.toMutableMap()

    private fun getTopDistributionEntries(
        distribution: Map<String, Float>,
        limit: Int,
        excludedLabels: Set<String> = emptySet()
    ): List<DistributionEntry> = distribution.entries
        .filter { (label, _) -> label !in excludedLabels }
        .map { (label, share) -> DistributionEntry(label, share) }
        .sortedByDescending { it.share }
        .take(limit)

    private fun computeOuterRingContamination(
        primaryColor: String?,
        outerDistribution: Map<String, Float>
    ): Float {
        if (primaryColor == null) {
            return clamp(outerDistribution["unknown"] ?: 1f)
        }

        val primaryShare = outerDistribution[primaryColor] ?: 0f
        val competitorShare = getTopDistributionEntries(outerDistribution, 2, setOf("unknown"))
            .firstOrNull { it.label != primaryColor }
            ?.share ?: 0f

        return clamp(
            (0.55f * (1f - primaryShare)) +
                (0.25f * (outerDistribution["unknown"] ?: 0f)) +
                (0.2f * max(0f, competitorShare - primaryShare))
        )
    }

    private fun computeDetectionReliability(
        confidence: Float,
        polygonArea: Float,
        fillRatio: Float,
        edgeTouchRatio: Float,
        innerMaskRatio: Float
    ): Float {
        val confidenceScore = smoothStep(confidence, Config.Detection.HARD_REJECT_CONFIDENCE, 0.92f)
        val areaScore = smoothStep(
            sqrt(max(polygonArea, 0f)),
            sqrt(Config.Detection.HARD_REJECT_POLYGON_AREA),
            sqrt(Config.Detection.PREFERRED_POLYGON_AREA)
        )
        val fillScore = smoothStep(
            fillRatio,
            Config.Detection.HARD_REJECT_FILL_RATIO,
            Config.Detection.MIN_FILL_RATIO + 0.25f
        )
        val edgeScore = 1f - smoothStep(
            edgeTouchRatio,
            Config.Detection.EDGE_TOUCH_WARN_RATIO,
            Config.Detection.EDGE_TOUCH_REJECT_RATIO
        )
        val innerScore = smoothStep(
            innerMaskRatio,
            0.08f,
            Config.Sampling.THIN_INNER_MASK_RATIO + 0.18f
        )
        return clamp(confidenceScore * areaScore * fillScore * edgeScore * innerScore)
    }

    private fun passesStrictColorFilter(
        result: AnalyzedHold,
        selectedColor: String,
        minColorScore: Float
    ): Boolean {
        if (result.colorLabel != selectedColor) return false
        if (result.colorStatus != STATUS_CLASSIFIED) return false
        if (result.colorScore < minColorScore) return false

        // distribution 체크와 primaryColor 체크를 OR 조건으로 완화:
        // colorWeights는 chromatic 픽셀의 normalized 합산이라 단일 색도 52% 초과가 어렵기 때문
        val distributionOk = (result.colorDistribution[selectedColor] ?: 0f) >= Config.Filtering.STRICT_DISTRIBUTION_FLOOR
        val primaryOk = result.primaryColor == selectedColor
        if (!distributionOk && !primaryOk) return false

        val achromaticColors = setOf("black", "white", "gray", "brown")
        if (selectedColor !in achromaticColors && result.validPixelRatio < Config.Scoring.MIN_FILTER_VALID_RATIO) {
            return false
        }
        return true
    }

    /**
     * Brown 픽셀 비율 계산: orange/red hue 범위에서 낮은 value와 중간 saturation을 가진 픽셀
     * (조명을 받지 못한 갈색, 나무색 홀드를 감지)
     */
    private fun computeBrownRatio(
        pixels: IntArray,
        mask: BooleanArray,
        thresholds: ThresholdProfile
    ): Float {
        var brownCount = 0
        var totalMasked = 0
        for (index in mask.indices) {
            if (!mask[index]) continue
            totalMasked++
            val pixel = pixels[index]
            val hsv = rgbToHsv180(
                r = ((pixel shr 16) and 0xFF).toFloat(),
                g = ((pixel shr 8) and 0xFF).toFloat(),
                b = (pixel and 0xFF).toFloat()
            )
            // brown: orange/red hue(0~25) + 낮은 value + 중간 saturation
            val hueInBrownRange = hsv.h <= 25f || hsv.h >= 165f // orange~red Hue/2 범위
            val valueLow = hsv.v <= Config.Hsv.Base.BROWN_VALUE_MAX
            val satMid = hsv.s >= Config.Hsv.Base.BROWN_S_MIN
            val notBlack = hsv.v > thresholds.blackValueCutoff
            if (hueInBrownRange && valueLow && satMid && notBlack) brownCount++
        }
        return if (totalMasked > 0) brownCount.toFloat() / totalMasked.toFloat() else 0f
    }

    /**
     * 픽셀 배열의 채도를 factor 배 증폭 (H, V는 유지 / S만 조정)
     * factor=1.0이면 원본 그대로, 1.3이면 채도 30% 상승
     */
    private fun boostPixelSaturation(pixels: IntArray, factor: Float): IntArray {
        if (factor <= 1f) return pixels
        return IntArray(pixels.size) { i ->
            val pixel = pixels[i]
            val r = ((pixel shr 16) and 0xFF).toFloat()
            val g = ((pixel shr 8) and 0xFF).toFloat()
            val b = (pixel and 0xFF).toFloat()
            val hsv = rgbToHsv180(r, g, b)
            val boostedS = min(hsv.s * factor, 255f)
            hsvToRgb180Packed(hsv.h, boostedS, hsv.v)
        }
    }

    /**
     * HSV(H: 0~180, S: 0~255, V: 0~255) → ARGB packed Int
     */
    private fun hsvToRgb180Packed(h: Float, s: Float, v: Float): Int {
        val vn = v / 255f
        val sn = s / 255f
        if (sn <= 0f) {
            val vi = (vn * 255f).roundToInt().coerceIn(0, 255)
            return (0xFF shl 24) or (vi shl 16) or (vi shl 8) or vi
        }
        val hh = h * 2f  // 0~360 스케일로 환원
        val sector = floor(hh / 60f).toInt() % 6
        val f = (hh / 60f) - floor(hh / 60f)
        val p = vn * (1f - sn)
        val q = vn * (1f - f * sn)
        val t = vn * (1f - (1f - f) * sn)
        val (rn, gn, bn) = when (sector) {
            0 -> Triple(vn, t, p)
            1 -> Triple(q, vn, p)
            2 -> Triple(p, vn, t)
            3 -> Triple(p, q, vn)
            4 -> Triple(t, p, vn)
            else -> Triple(vn, p, q)
        }
        val ri = (rn * 255f).roundToInt().coerceIn(0, 255)
        val gi = (gn * 255f).roundToInt().coerceIn(0, 255)
        val bi = (bn * 255f).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
    }

    private fun pointInPolygon(x: Float, y: Float, polygon: List<PixelPoint>): Boolean {
        if (polygon.size < 3) return false

        var inside = false
        var previous = polygon.last()
        polygon.forEach { current ->
            val dy = (previous.y - current.y).toFloat()
            val intersects = ((current.y > y) != (previous.y > y)) &&
                (x < ((previous.x - current.x).toFloat() * (y - current.y.toFloat())) /
                (if (abs(dy) > 1e-6f) dy else 1e-6f) +
                current.x.toFloat())
            if (intersects) inside = !inside
            previous = current
        }
        return inside
    }

    private fun smoothCircularHistogram(histogram: IntArray, radius: Int): IntArray =
        IntArray(histogram.size) { index ->
            var total = 0
            for (offset in -radius..radius) {
                val sampleIndex = (index + offset + histogram.size) % histogram.size
                total += histogram[sampleIndex]
            }
            total
        }

    private fun argMax(values: IntArray): Int? =
        values.indices.maxByOrNull { values[it] }

    private fun weightedAverage(values: List<Pair<Float, Float>>): Float {
        var totalWeight = 0f
        var weightedSum = 0f
        values.forEach { (value, weight) ->
            if (weight <= 0f) return@forEach
            totalWeight += weight
            weightedSum += value * weight
        }
        return if (totalWeight > 0f) weightedSum / totalWeight else 0f
    }

    private fun smoothStep(value: Float, edge0: Float, edge1: Float): Float {
        if (edge1 <= edge0) return if (value >= edge1) 1f else 0f
        val t = clamp((value - edge0) / (edge1 - edge0))
        return t * t * (3f - (2f * t))
    }

    private fun circularHueDistance(left: Float, right: Float): Float {
        val delta = abs(left - right)
        return min(delta, 180f - delta)
    }

    private fun rgbToHsv180(r: Float, g: Float, b: Float): Hsv {
        val rn = r / 255f
        val gn = g / 255f
        val bn = b / 255f
        val maxChannel = max(rn, max(gn, bn))
        val minChannel = min(rn, min(gn, bn))
        val delta = maxChannel - minChannel

        var hue = 0f
        if (delta != 0f) {
            hue = when (maxChannel) {
                rn -> 60f * (((gn - bn) / delta) % 6f)
                gn -> 60f * (((bn - rn) / delta) + 2f)
                else -> 60f * (((rn - gn) / delta) + 4f)
            }
        }
        if (hue < 0f) hue += 360f

        return Hsv(
            h = hue / 2f,
            s = if (maxChannel == 0f) 0f else (delta / maxChannel) * 255f,
            v = maxChannel * 255f
        )
    }

    private fun roundTo(value: Float, digits: Int): Float {
        val scale = when (digits) {
            0 -> 1f
            1 -> 10f
            2 -> 100f
            3 -> 1000f
            else -> 10f.powInt(digits)
        }
        return round(value * scale) / scale
    }

    private fun Float.powInt(exponent: Int): Float {
        var result = 1f
        repeat(max(exponent, 0)) {
            result *= this
        }
        return result
    }

    private fun clamp(value: Float, minValue: Float = 0f, maxValue: Float = 1f): Float =
        value.coerceIn(minValue, maxValue)

    private fun clampInt(value: Int, minValue: Int, maxValue: Int): Int =
        if (maxValue < minValue) minValue else value.coerceIn(minValue, maxValue)

    private fun emptyBounds(): PixelBounds = PixelBounds(0, 0, 0, 0)

    companion object {
        private const val TAG = "HoldColorClassifier"

        private const val STATUS_CLASSIFIED = "classified"
        private const val STATUS_MIXED_COLOR = "mixed_color"
        private const val STATUS_UNKNOWN_LOW_CHROMA = "unknown_low_chroma"
        private const val STATUS_UNKNOWN_LOW_CONF = "unknown_low_conf"
        private const val STATUS_UNKNOWN_LOW_QUALITY = "unknown_low_quality"
        private const val STATUS_UNKNOWN_CONTAMINATED = "unknown_contaminated"
        private const val STATUS_INVALID_POLYGON = "invalid_polygon"

        private const val WARNING_SMALL_POLYGON = "small_polygon"
        private const val WARNING_SHADOW_MIX = "shadow_mix"
        private const val WARNING_GLARE_MIX = "glare_mix"
        private const val WARNING_LOW_SATURATION_MIX = "low_sat_mix"
        private const val WARNING_LOW_DET_CONF = "low_det_conf"
        private const val WARNING_FEW_VALID_PIXELS = "few_valid_pixels"
        private const val WARNING_AMBIGUOUS_HUE = "ambiguous_hue"
        private const val WARNING_INVALID_POLYGON = "invalid_polygon"
        private const val WARNING_GRAY_DOMINANT = "gray_dominant"
        private const val WARNING_EDGE_TOUCH = "edge_touch"
        private const val WARNING_LOW_FILL_RATIO = "low_fill_ratio"
        private const val WARNING_BOUNDARY_CONTAMINATION = "boundary_contamination"
        private const val WARNING_MIXED_COLOR = "mixed_color"
        private const val WARNING_LOW_DETECTION_RELIABILITY = "low_det_reliability"
        private const val WARNING_THIN_INNER_MASK = "thin_inner_mask"
        private const val WARNING_PREPROCESS_EXCLUDED = "preprocess_excluded"
    }
}
