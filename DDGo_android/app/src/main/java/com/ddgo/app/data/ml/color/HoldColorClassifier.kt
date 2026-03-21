package com.ddgo.app.data.ml.color

import android.graphics.Bitmap
import android.util.Log
import com.ddgo.app.domain.model.Hold
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
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
        val varianceV: Float,
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
        val brownCount: Int,
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

    internal data class ClassifiedHoldRich(
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
                // IMPORTANT: GRAY_S_MAX = CHROMATIC_S_MIN - 1 이어야 함 (gap 없음)
                // when 블록에서 gray 체크가 chromatic보다 먼저 실행되므로,
                // GRAY_S_MAX >= CHROMATIC_S_MIN 이면 chromatic 픽셀이 gray로 분류됨
                // 기존 gap(28~34)은 pastel/fluorescent/원거리 홀드에서 unknown 발생 원인이었음
                const val CHROMATIC_S_MIN = 30f   // 실내 조명 + pastel 홀드 고려해 더 낮춤 (기존 35)
                const val GRAY_S_MAX = 29f         // CHROMATIC_S_MIN - 1 (gap 제거)
                const val LOW_VALUE_CUTOFF = 30f   // 어두운 환경 홀드 포함
                const val HIGH_VALUE_CUTOFF = 245f
                const val BLACK_VALUE_CUTOFF = 50f
                const val BLACK_S_MAX = 80f
                // WHITE_S_MAX: 색상 LED 조명 아래 white 홀드는 S=30~45 가능
                // 35로 완화해 실제 white 홀드 감지율 향상 (반사광 false positive는
                // WHITE_VALUE_CUTOFF와 calibration의 whiteSMax 상한(38f)으로 억제)
                const val WHITE_VALUE_CUTOFF = 215f
                const val WHITE_S_MAX = 35f
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
                const val BRIGHTNESS_SHIFT_LIMIT = 28f  // 기존 18 → 28: 어두운 실내 체육관 대응
                const val SATURATION_SHIFT_LIMIT = 16f  // 기존 12 → 16: 채도 보정 폭 확대
                const val WHITE_SHIFT_LIMIT = 20f       // 기존 16 → 20: 밝은/어두운 환경 대응
                const val BLACK_SHIFT_LIMIT = 16f       // 기존 12 → 16: 어두운 환경 대응
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
            // 가중 기하평균 + floor(0.3) 적용으로 이전보다 값이 높아짐
            // 0.10으로 낮춰 색상 점수가 좋은 홀드가 불필요하게 리셋되지 않도록 함
            const val CONFIDENT_RELIABILITY_FLOOR = 0.10f
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
            // 적응적 부스트: 어두운/탈채도 홀드는 더 강하게, 밝은 홀드는 약하게
            const val MIN_ADAPTIVE_BOOST = 1.0f
            const val MAX_ADAPTIVE_BOOST = 1.8f
            const val DARKNESS_WEIGHT = 0.6f
            const val DESATURATION_WEIGHT = 0.4f
        }

        object ChromaticSoft {
            // 채도가 chromaticSMin 바로 아래인 픽셀을 부분 멤버십으로 처리
            // 그림자 속 동일 색상 홀드에서 gray 오분류 방지
            const val SOFT_MARGIN = 8f
        }
    }

    private val chromaticColorProfiles = listOf(
        // red: 360도 경계(0°, 360°)에 걸쳐 있어 두 center 사용
        // hueTolerance 12로 좁혀 orange 영역(hue 10+) 침범 감소
        // valueBias 1.02로 올려 밝은 red(순색)에 가중치 → brown(어두운)과 분리
        ChromaticColorProfile("red", intArrayOf(0, 179), 12f, 1.04f, 1.02f),

        // orange: center=15로 이동 (실제 orange 중심 30°/2=15)
        // hueTolerance 15로 확장해 hue 0~30 커버 → 실제 orange 범위 포착
        // valueBias 1.06으로 높여 밝은 orange에 가중치 → brown(어두운)과 분리
        ChromaticColorProfile("orange", intArrayOf(15), 15f, 1.02f, 1.06f),

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

        // blue 계열을 skyblue(하늘색)와 navy(남색)로 분리
        // skyblue (하늘색/cyan): 170-216° → hue/2 = 85-108
        // hueTolerance 18로 확대: 그림자/조명 변화에 의한 hue 편차 흡수
        // saturationMinOverride 22: 그림자 속 탈채도 하늘색(S=22~30)도 포함
        // saturationScale 1.4: 낮은 채도에서도 factor가 빠르게 올라감
        // valueScale 1.2: 어두운 하늘색도 높은 factor 반환
        ChromaticColorProfile(
            label = "skyblue", centers = intArrayOf(95), hueTolerance = 18f,
            saturationBias = 0.98f, valueBias = 1.04f,
            saturationMinOverride = 22f, saturationScale = 1.4f,
            valueScale = 1.2f
        ),

        // navy (남색): 220-250° → hue/2 = 110-125
        // valueBias 0.94로 어두운 남색 포용, saturationScale 0.95로 purple과 분리
        ChromaticColorProfile(
            label = "navy", centers = intArrayOf(115), hueTolerance = 14f,
            saturationBias = 1.0f, valueBias = 0.94f,
            saturationScale = 0.95f
        ),

        // purple: 260-300° → Hue/2=130-150
        // center 138로 하향 → 실제 blue-purple 경계(hue ~128)와 간격 확보
        // hueTolerance 18로 약간 좁혀 pink 영역 침범 감소
        ChromaticColorProfile(
            label = "purple", centers = intArrayOf(138), hueTolerance = 18f,
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
        "cyan" to "skyblue",
        "skyblue" to "skyblue",
        "blue" to "navy",
        "navy" to "navy",
        "purple" to "purple",
        "pink" to "pink",
        "white" to "white",
        "black" to "black",
        "brown" to "brown",
        "gray" to "gray"
    )

    data class DetectionClassificationResult(
        val allHolds: List<Hold>,
        val filteredHolds: List<Hold>
    )

    internal data class ClassifiedHoldPrecomputeResult(
        val classifiedHolds: List<ClassifiedHoldRich>,
        val allHolds: List<Hold>
    )

    fun classifyAll(bitmap: Bitmap, holds: List<Hold>): List<Hold> {
        val precomputed = classifyAllRich(
            bitmap = bitmap,
            holds = holds,
            relaxedRejection = false
        )
        Log.d(TAG, "색상 분류 완료: ${holds.size}개 홀드")
        return precomputed.allHolds
    }

    fun classifyForDetection(
        bitmap: Bitmap,
        holds: List<Hold>,
        targetColorName: String,
        scoreThreshold: Float = 0.38f
    ): DetectionClassificationResult {
        val targetLabel = resolveTargetLabel(targetColorName)
        val precomputed = classifyAllRich(
            bitmap = bitmap,
            holds = holds,
            relaxedRejection = targetLabel.isNotBlank() && targetLabel != "all"
        )
        val filtered = filterClassifiedHolds(
            classifiedHolds = precomputed.classifiedHolds,
            targetColorName = targetColorName,
            scoreThreshold = scoreThreshold
        )
        return DetectionClassificationResult(
            allHolds = precomputed.allHolds,
            filteredHolds = filtered
        )
    }

    fun classifyAndFilter(
        bitmap: Bitmap,
        holds: List<Hold>,
        targetColorName: String,
        scoreThreshold: Float = 0.38f
    ): List<Hold> {
        val precomputed = classifyAllRich(
            bitmap = bitmap,
            holds = holds,
            relaxedRejection = true
        )
        return filterClassifiedHolds(
            classifiedHolds = precomputed.classifiedHolds,
            targetColorName = targetColorName,
            scoreThreshold = scoreThreshold
        )

        val targetLabel = APP_COLOR_TO_LABEL[targetColorName.lowercase()] ?: targetColorName.lowercase()
        // 특정 색상을 필터링할 때는 완화된 rejection 기준 사용
        val analyzed = analyzeAll(bitmap, holds, relaxedRejection = true)

        // ── 1차 필터: 기존 로직 ──
        val firstPassFiltered = if (targetLabel.isBlank() || targetLabel == "all") {
            analyzed
        } else {
            analyzed.filter { result ->
                passesStrictColorFilter(result, targetLabel, scoreThreshold)
            }
        }

        // ── 2차 패스: 근접 동색 홀드 컨텍스트로 unknown 구제 ──
        // 볼더링 특성: 같은 색 홀드가 가까이 모여 있으므로,
        // 1차에서 확정된 target 홀드 근처의 unknown 홀드를 소량 가중치로 구제
        val rescued = if (targetLabel.isNotBlank() && targetLabel != "all") {
            rescueUnknownByProximity(
                allAnalyzed = analyzed,
                confirmedTargetHolds = firstPassFiltered,
                targetLabel = targetLabel
            )
        } else {
            emptyList()
        }

        val filtered = firstPassFiltered + rescued

        // 상세 필터링 결과 로깅
        val classifiedCount = analyzed.count { it.colorStatus == STATUS_CLASSIFIED }
        val weakCount = analyzed.count { it.colorStatus == STATUS_CLASSIFIED_WEAK }
        val unknownCount = analyzed.count { it.colorLabel == "unknown" }
        val unknownWithTarget = analyzed.count {
            it.colorLabel == "unknown" && it.primaryColor == targetLabel
        }
        val statusBreakdown = analyzed
            .filter { it.colorLabel == "unknown" }
            .groupBy { it.colorStatus }
            .mapValues { it.value.size }

        Log.d(
            TAG,
            "색상 필터 결과: target='$targetColorName' -> '$targetLabel', " +
                "${analyzed.size}개 분류 -> ${filtered.size}개 매칭 " +
                "(1차=${firstPassFiltered.size}, 근접구제=${rescued.size}) " +
                "(확정=$classifiedCount, 약한=$weakCount, unknown=$unknownCount" +
                "${if (unknownWithTarget > 0) ", unknown중_target일치=$unknownWithTarget" else ""}" +
                "${if (statusBreakdown.isNotEmpty()) ", unknown내역=$statusBreakdown" else ""})"
        )
        return filtered.map { it.toHold() }
    }

    private fun resolveTargetLabel(targetColorName: String): String {
        return APP_COLOR_TO_LABEL[targetColorName.lowercase()] ?: targetColorName.lowercase()
    }

    internal fun classifyAllRich(
        bitmap: Bitmap,
        holds: List<Hold>,
        relaxedRejection: Boolean = true
    ): ClassifiedHoldPrecomputeResult {
        val analyzed = analyzeAll(
            bitmap = bitmap,
            holds = holds,
            relaxedRejection = relaxedRejection
        )
        return ClassifiedHoldPrecomputeResult(
            classifiedHolds = analyzed.map { analyzedHold -> analyzedHold.toClassifiedHoldRich() },
            allHolds = analyzed.map { analyzedHold -> analyzedHold.toHold() }
        )
    }

    internal fun filterClassifiedHolds(
        classifiedHolds: List<ClassifiedHoldRich>,
        targetColorName: String,
        scoreThreshold: Float = 0.38f
    ): List<Hold> {
        val targetLabel = resolveTargetLabel(targetColorName)
        val analyzed = classifiedHolds.map { classifiedHold -> classifiedHold.toAnalyzedHold() }
        return filterAnalyzedHolds(
            analyzed = analyzed,
            targetColorName = targetColorName,
            targetLabel = targetLabel,
            scoreThreshold = scoreThreshold
        ).map { analyzedHold -> analyzedHold.toHold() }
    }

    private fun AnalyzedHold.toClassifiedHoldRich(): ClassifiedHoldRich = ClassifiedHoldRich(
        hold = hold,
        colorLabel = colorLabel,
        colorScore = colorScore,
        colorStatus = colorStatus,
        primaryColor = primaryColor,
        colorDistribution = colorDistribution,
        rawColorScore = rawColorScore,
        detectionReliability = detectionReliability,
        validPixelRatio = validPixelRatio,
        warnings = warnings
    )

    private fun ClassifiedHoldRich.toAnalyzedHold(): AnalyzedHold = AnalyzedHold(
        hold = hold,
        colorLabel = colorLabel,
        colorScore = colorScore,
        colorStatus = colorStatus,
        primaryColor = primaryColor,
        colorDistribution = colorDistribution,
        rawColorScore = rawColorScore,
        detectionReliability = detectionReliability,
        validPixelRatio = validPixelRatio,
        warnings = warnings
    )

    private fun filterAnalyzedHolds(
        analyzed: List<AnalyzedHold>,
        targetColorName: String,
        targetLabel: String,
        scoreThreshold: Float
    ): List<AnalyzedHold> {
        val firstPassFiltered = if (targetLabel.isBlank() || targetLabel == "all") {
            analyzed
        } else {
            analyzed.filter { result ->
                passesStrictColorFilter(result, targetLabel, scoreThreshold)
            }
        }

        val rescued = if (targetLabel.isNotBlank() && targetLabel != "all") {
            rescueUnknownByProximity(
                allAnalyzed = analyzed,
                confirmedTargetHolds = firstPassFiltered,
                targetLabel = targetLabel
            )
        } else {
            emptyList()
        }

        val filtered = firstPassFiltered + rescued
        val classifiedCount = analyzed.count { it.colorStatus == STATUS_CLASSIFIED }
        val weakCount = analyzed.count { it.colorStatus == STATUS_CLASSIFIED_WEAK }
        val unknownCount = analyzed.count { it.colorLabel == "unknown" }
        val unknownWithTarget = analyzed.count {
            it.colorLabel == "unknown" && it.primaryColor == targetLabel
        }
        val statusBreakdown = analyzed
            .filter { it.colorLabel == "unknown" }
            .groupBy { it.colorStatus }
            .mapValues { it.value.size }

        Log.d(
            TAG,
            "색상 필터 결과: target='$targetColorName' -> '$targetLabel', " +
                "${analyzed.size}개 분류 -> ${filtered.size}개 매칭 " +
                "(1차=${firstPassFiltered.size}, 근접구제=${rescued.size}) " +
                "(확정=$classifiedCount, 약함=$weakCount, unknown=$unknownCount" +
                "${if (unknownWithTarget > 0) ", unknown 중 target 일치=$unknownWithTarget" else ""}" +
                "${if (statusBreakdown.isNotEmpty()) ", unknown 내역=$statusBreakdown" else ""})"
        )

        return filtered
    }

    /**
     * 2차 패스: 근접 동색 홀드 컨텍스트 기반 unknown 구제.
     *
     * 볼더링 특성상 같은 색 홀드가 루트를 따라 가까이 배치되므로,
     * 1차에서 확정된 target 홀드 근처의 unknown 홀드를 구제한다.
     *
     * 조건 (모두 충족해야 함):
     * 1. colorLabel이 "unknown"이고 1차 필터에서 탈락한 홀드
     * 2. target color가 해당 홀드의 chromatic 분포에서 최소 0.10 이상
     * 3. 확정된 target 홀드 중 하나 이상이 PROXIMITY_THRESHOLD 이내에 존재
     * 4. 근접 가중치 반영 후 최소 rawColorScore >= 0.15
     *
     * 가중치는 소량(최대 0.12)만 부여하여 오탐을 방지한다.
     */
    private fun rescueUnknownByProximity(
        allAnalyzed: List<AnalyzedHold>,
        confirmedTargetHolds: List<AnalyzedHold>,
        targetLabel: String
    ): List<AnalyzedHold> {
        if (confirmedTargetHolds.isEmpty()) return emptyList()

        val confirmedSet = confirmedTargetHolds.toSet()
        val unknowns = allAnalyzed.filter { it !in confirmedSet }

        val rescued = mutableListOf<AnalyzedHold>()
        for (unknown in unknowns) {
            // target 색상이 이 홀드의 분포에 최소한으로라도 존재해야 함
            val targetShare = unknown.colorDistribution[targetLabel] ?: 0f
            if (targetShare < PROXIMITY_MIN_TARGET_SHARE) continue

            // 이 홀드가 해당 색상의 primary 또는 top chromatic이어야 함
            val isTargetTopChromatic = unknown.colorDistribution.entries
                .filter { it.key !in setOf("unknown", "black", "white", "gray", "brown") }
                .maxByOrNull { it.value }
                ?.key == targetLabel
            if (!isTargetTopChromatic) continue

            // 확정 홀드와의 최소 거리 계산
            val minDistance = confirmedTargetHolds.minOf { confirmed ->
                bboxCenterDistance(unknown.hold.boundingBox, confirmed.hold.boundingBox)
            }
            if (minDistance > PROXIMITY_THRESHOLD) continue

            // 근접 가중치: 가까울수록 높지만 최대 PROXIMITY_MAX_BOOST
            val proximityFactor = clamp(1f - (minDistance / PROXIMITY_THRESHOLD))
            val proximityBoost = proximityFactor * PROXIMITY_MAX_BOOST

            // rawColorScore + 근접 가중치가 최소 기준 이상이어야 구제
            val boostedScore = unknown.rawColorScore + proximityBoost
            if (boostedScore < PROXIMITY_MIN_RESCUE_SCORE) continue

            Log.d(
                TAG,
                "🔗 근접 구제: bbox=${unknown.hold.boundingBox}, " +
                    "targetShare=$targetShare, dist=${roundTo(minDistance, 3)}, " +
                    "boost=${roundTo(proximityBoost, 3)}, " +
                    "rawScore=${unknown.rawColorScore} -> boosted=${roundTo(boostedScore, 3)}, " +
                    "status=${unknown.colorStatus}"
            )
            rescued += unknown
        }
        return rescued
    }

    /**
     * 두 BoundingBox 중심점 간의 정규화 유클리드 거리.
     * 좌표가 0~1 정규화이므로 결과도 0~√2 범위.
     */
    private fun bboxCenterDistance(a: Hold.BoundingBox, b: Hold.BoundingBox): Float {
        val acx = (a.left + a.right) / 2f
        val acy = (a.top + a.bottom) / 2f
        val bcx = (b.left + b.right) / 2f
        val bcy = (b.top + b.bottom) / 2f
        val dx = acx - bcx
        val dy = acy - bcy
        return sqrt(dx * dx + dy * dy)
    }

    fun classifySingle(bitmap: Bitmap, hold: Hold): Hold {
        val calibration = buildImageCalibrationContext(bitmap)
        return analyzeHold(bitmap, hold, calibration).toHold()
    }

    private fun analyzeAll(
        bitmap: Bitmap,
        holds: List<Hold>,
        relaxedRejection: Boolean = false
    ): List<AnalyzedHold> {
        if (holds.isEmpty()) return emptyList()
        val calibration = buildImageCalibrationContext(bitmap)
        return holds.map { hold -> analyzeHold(bitmap, hold, calibration, relaxedRejection) }
    }

    private fun analyzeHold(
        bitmap: Bitmap,
        hold: Hold,
        calibrationContext: CalibrationContext,
        relaxedRejection: Boolean = false
    ): AnalyzedHold {
        val prepared = prepareHoldForAnalysis(hold, bitmap.width, bitmap.height, relaxedRejection)
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
        // 적응적 부스트: 어두운/탈채도 홀드는 더 강하게, 밝은 홀드는 약하게
        val adaptiveBoost = computeAdaptiveBoostFactor(polygonRawStats, surroundingRawStats, calibrationContext)
        val colorPixels = boostPixelSaturation(bboxPixels, adaptiveBoost)
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

        // brown 판별: collectRegionColorStats에서 이미 인라인 계산된 brownCount 사용
        val brownRatio = if (totalPixels > 0) innerStats.brownCount.toFloat() / totalPixels.toFloat() else 0f

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
            rawColorScore = clamp(
                0.4f * labelShare +
                    0.2f * validPixelRatio +
                    0.2f * saturationScore +
                    0.2f * (1f - outerRingContamination)
            )
            // chromatic 수가 적어도 primaryColor가 존재하고 share가 일정 이상이면
            // best-effort로 분류 (target-color 필터에서 구제 가능)
            if (primaryColor != null && labelShare >= 0.20f && !grayRatio.let { it > 0.55f }) {
                colorLabel = primaryColor
                colorStatus = STATUS_CLASSIFIED_WEAK
            } else {
                colorStatus = if (detectionReliability < Config.Scoring.LOW_RELIABILITY_FLOOR) {
                    STATUS_UNKNOWN_LOW_CONF
                } else {
                    STATUS_UNKNOWN_LOW_CHROMA
                }
            }
        } else {
            val dominanceScore = clamp(
                (topMargin - Config.Scoring.MIXED_COLOR_MARGIN) / 0.25f
            )
            rawColorScore = clamp(
                0.38f * labelShare +
                    0.10f * peakShare +
                    0.16f * dominanceScore +
                    0.14f * validPixelRatio +
                    0.10f * saturationScore +
                    0.06f * maskData.innerMaskRatio +
                    0.06f * (1f - outerRingContamination)
            )

            when {
                primaryColor == null -> {
                    warnings += WARNING_AMBIGUOUS_HUE
                    colorStatus = STATUS_UNKNOWN_LOW_CHROMA
                }
                labelShare < Config.Scoring.MIN_PRIMARY_SHARE ||
                    peakShare < Config.Scoring.MIN_PEAK_SHARE -> {
                    warnings += WARNING_AMBIGUOUS_HUE
                    // share가 낮아도 primary가 명확하면 weak 분류 (target-color 필터에서 구제)
                    if (labelShare >= 0.18f && topMargin >= 0.04f) {
                        colorLabel = primaryColor
                        colorStatus = STATUS_CLASSIFIED_WEAK
                    } else {
                        colorStatus = STATUS_UNKNOWN_LOW_CHROMA
                    }
                }
                topMargin < Config.Scoring.MIN_PRIMARY_MARGIN -> {
                    warnings += WARNING_MIXED_COLOR
                    // margin이 좁아도 labelShare가 충분히 높으면 primary가 맞을 가능성이 높음
                    if (labelShare >= 0.30f) {
                        colorLabel = primaryColor
                        colorStatus = STATUS_CLASSIFIED
                    } else if (labelShare >= 0.20f) {
                        colorLabel = primaryColor
                        colorStatus = STATUS_CLASSIFIED_WEAK
                    } else {
                        colorStatus = STATUS_MIXED_COLOR
                    }
                }
                outerRingContamination >= Config.Scoring.CONTAMINATION_REJECT -> {
                    // 오염이 높아도 inner 색 분포가 강하면 weak 분류 (구제 가능)
                    if (labelShare >= 0.30f && topMargin >= 0.10f) {
                        colorLabel = primaryColor
                        colorStatus = STATUS_CLASSIFIED_WEAK
                    } else {
                        colorStatus = STATUS_UNKNOWN_CONTAMINATED
                    }
                }
                else -> {
                    colorLabel = primaryColor
                    colorStatus = STATUS_CLASSIFIED
                }
            }
        }

        if (detectionReliability < Config.Scoring.CONFIDENT_RELIABILITY_FLOOR) {
            warnings += WARNING_LOW_DETECTION_RELIABILITY
            // rawColorScore가 충분히 높으면 색상 분류 결과를 신뢰하고 리셋하지 않음
            // detection 품질이 낮아도 색상 자체는 명확할 수 있음
            if (colorStatus == STATUS_CLASSIFIED && rawColorScore < 0.35f) {
                colorLabel = "unknown"
                colorStatus = if (hold.confidence < Config.Detection.LOW_CONFIDENCE) {
                    STATUS_UNKNOWN_LOW_CONF
                } else {
                    STATUS_UNKNOWN_LOW_QUALITY
                }
            } else if (colorStatus == STATUS_CLASSIFIED_WEAK && rawColorScore < 0.25f) {
                colorLabel = "unknown"
                colorStatus = STATUS_UNKNOWN_LOW_QUALITY
            }
            // STATUS_CLASSIFIED_WEAK + rawColorScore >= 0.25이면 분류 유지 (구제 가능)
        }

        rawColorScore = roundTo(rawColorScore, 3)
        // detectionReliability 최솟값을 높여 YOLO 신뢰도가 낮아도 색 점수가 너무 낮아지지 않도록
        var colorScore = clamp(rawColorScore * max(detectionReliability, Config.Scoring.MIN_RELIABILITY_FOR_SCORE))
        if (colorLabel == "unknown") {
            colorScore = min(colorScore, Config.Scoring.UNKNOWN_SCORE_CAP)
        }
        colorScore = roundTo(colorScore, 3)

        // 진단 로깅: unknown이거나 weak 분류인 경우 상세 정보 출력
        if (colorLabel == "unknown" || colorStatus == STATUS_CLASSIFIED_WEAK) {
            val topDist = innerDistribution.entries
                .sortedByDescending { it.value }
                .take(3)
                .joinToString { "${it.key}=${it.value}" }
            Log.d(
                TAG,
                "⚠️ [$colorStatus] bbox=${hold.boundingBox}, " +
                    "label=$colorLabel, primary=$primaryColor, " +
                    "score=$colorScore(raw=$rawColorScore), " +
                    "validRatio=$validPixelRatio, reliability=${roundTo(detectionReliability, 3)}, " +
                    "dist=[$topDist], " +
                    "innerMaskRatio=${maskData.innerMaskRatio}, " +
                    "warnings=$warnings"
            )
        }

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
        imageHeight: Int,
        relaxedRejection: Boolean = false
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

        // Target-color 필터링 시 완화된 기준: 작은/얇은/가장자리 홀드도 분석 대상으로 포함
        val areaThreshold = if (relaxedRejection) 60f else Config.Detection.HARD_REJECT_POLYGON_AREA
        val fillThreshold = if (relaxedRejection) 0.04f else Config.Detection.HARD_REJECT_FILL_RATIO
        val edgeThreshold = if (relaxedRejection) 0.65f else Config.Detection.EDGE_TOUCH_REJECT_RATIO

        when {
            hold.confidence < Config.Detection.HARD_REJECT_CONFIDENCE -> {
                shouldAnalyze = false
                exclusionReason = STATUS_UNKNOWN_LOW_CONF
            }
            polygonArea < areaThreshold -> {
                shouldAnalyze = false
                exclusionReason = STATUS_UNKNOWN_LOW_QUALITY
            }
            fillRatio < fillThreshold -> {
                shouldAnalyze = false
                exclusionReason = STATUS_UNKNOWN_LOW_QUALITY
            }
            edgeTouchRatio >= edgeThreshold -> {
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

        // Outer ring: dilate the mask outward by the same erosion radius,
        // then take (dilatedMask && !mask) to sample actual wall pixels
        // instead of the hold's own edge pixels.
        // Fallback: if dilation yields nothing (mask fills entire bbox), use inner edge ring.
        val dilatedMask = createDilatedMask(mask, bbox.width, bbox.height, max(resolvedRadius, 2))
        val outerMask = BooleanArray(mask.size)
        var outerPixels = 0
        for (index in mask.indices) {
            if (dilatedMask[index] && !mask[index]) {
                outerMask[index] = true
                outerPixels++
            }
        }
        // Fallback: if dilation ring is empty (polygon fills bbox), use eroded edge ring
        if (outerPixels == 0) {
            for (index in mask.indices) {
                if (mask[index] && !innerMask[index]) {
                    outerMask[index] = true
                    outerPixels++
                }
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
        val maxDimension = max(1, max(bbox.width, bbox.height))

        // 종횡비 보정: 초승달/얇은 홀드(aspect > 2.5)는 에로전을 줄여 내부 픽셀 보존
        val aspectRatio = maxDimension.toFloat() / minDimension.toFloat()
        val aspectPenalty = if (aspectRatio > 2.5f) {
            clamp(1f - (aspectRatio - 2.5f) * 0.15f, 0.4f, 1f)
        } else {
            1f
        }

        val relativeRadius = round(minDimension * Config.Sampling.INNER_ERODE_RATIO * aspectPenalty).toInt()
        val areaRadius = round(sqrt(maskPixels.toFloat()) / 18f * aspectPenalty).toInt()

        // 매우 작은 마스크(<80px)는 에로전 없이 분석 허용
        val minRadius = if (maskPixels < 80) 0 else Config.Sampling.MIN_INNER_ERODE_PX

        return clampInt(
            max(relativeRadius, areaRadius),
            minRadius,
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

    /**
     * Dilation: pixel is set if ANY pixel in its radius neighborhood is set.
     * Uses integral image: sum > 0 means at least one mask pixel exists in the window.
     */
    private fun createDilatedMask(
        mask: BooleanArray,
        width: Int,
        height: Int,
        radius: Int
    ): BooleanArray {
        if (mask.isEmpty()) return BooleanArray(0)
        if (radius <= 0) return mask.copyOf()

        val integral = buildMaskIntegralImage(mask, width, height)
        val dilated = BooleanArray(mask.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val x1 = max(0, x - radius)
                val y1 = max(0, y - radius)
                val x2 = min(width - 1, x + radius)
                val y2 = min(height - 1, y + radius)
                val sum = sumIntegralRect(integral, width, x1, y1, x2, y2)
                if (sum > 0) dilated[y * width + x] = true
            }
        }
        return dilated
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

    /**
     * 이미지 전체에서 밝기/채도 통계를 수집하여 캘리브레이션 컨텍스트 생성.
     * 중앙 가중치(gaussian-like): 벽은 보통 이미지 중앙에 위치하므로,
     * 가장자리(천장 조명, 바닥 매트)의 영향을 줄이기 위해 중심 픽셀에 더 높은 가중치 부여.
     * 분산(varianceV): 고대비 장면(밝은 조명+어두운 그림자)과 균일 조명 구분용.
     */
    private fun buildImageCalibrationContext(bitmap: Bitmap): CalibrationContext {
        val imageWidth = bitmap.width
        val imageHeight = bitmap.height
        if (imageWidth <= 0 || imageHeight <= 0) {
            return CalibrationContext(
                meanV = Config.Hsv.Calibration.REFERENCE_MEAN_VALUE,
                meanS = 96f,
                varianceV = 0f,
                sampleStep = 1
            )
        }

        val pixels = IntArray(imageWidth * imageHeight)
        bitmap.getPixels(pixels, 0, imageWidth, 0, 0, imageWidth, imageHeight)
        val step = max(4, floor(min(imageWidth, imageHeight) / 160f).toInt())

        val cx = imageWidth / 2f
        val cy = imageHeight / 2f
        // sigma = 40% of half-dimension → center 60% gets ~86% of weight
        val sigmaX = imageWidth * 0.4f
        val sigmaY = imageHeight * 0.4f
        val invSigmaX2 = if (sigmaX > 0f) 1f / (2f * sigmaX * sigmaX) else 0f
        val invSigmaY2 = if (sigmaY > 0f) 1f / (2f * sigmaY * sigmaY) else 0f

        var weightSum = 0f
        var weightedValueSum = 0f
        var weightedSatSum = 0f
        var weightedValueSqSum = 0f
        for (y in 0 until imageHeight step step) {
            val dy = y.toFloat() - cy
            val yGauss = exp(-(dy * dy * invSigmaY2))
            val rowOffset = y * imageWidth
            for (x in 0 until imageWidth step step) {
                val dx = x.toFloat() - cx
                val w = yGauss * exp(-(dx * dx * invSigmaX2))
                val pixel = pixels[rowOffset + x]
                val hsv = rgbToHsv180(
                    r = ((pixel shr 16) and 0xFF).toFloat(),
                    g = ((pixel shr 8) and 0xFF).toFloat(),
                    b = (pixel and 0xFF).toFloat()
                )
                weightedValueSum += hsv.v * w
                weightedSatSum += hsv.s * w
                weightedValueSqSum += hsv.v * hsv.v * w
                weightSum += w
            }
        }

        val meanV = if (weightSum > 0f) weightedValueSum / weightSum else Config.Hsv.Calibration.REFERENCE_MEAN_VALUE
        val meanS = if (weightSum > 0f) weightedSatSum / weightSum else 96f
        // variance = E[V^2] - E[V]^2
        val varianceV = if (weightSum > 0f) max(0f, (weightedValueSqSum / weightSum) - (meanV * meanV)) else 0f

        return CalibrationContext(
            meanV = meanV,
            meanS = meanS,
            varianceV = varianceV,
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

        // Variance-based threshold adjustment:
        // stdDev ~40 = typical uniform lighting, ~65+ = high contrast (bright lights + dark shadows)
        // varianceSpread: 0.0 = uniform, 1.0 = very high contrast
        // High variance → widen black/white gap (avoid misclassifying shadows/highlights)
        // Low variance → tighten thresholds for finer precision
        val stdDevV = sqrt(max(0f, globalStats.varianceV))
        val varianceSpread = clamp((stdDevV - 40f) / 40f)  // 0 at stdDev=40, 1 at stdDev=80+

        val calibratedChromaticSMin = clamp(
            Config.Hsv.Base.CHROMATIC_S_MIN + (brightnessShift * 0.35f),
            Config.Hsv.Base.CHROMATIC_S_MIN - Config.Hsv.Calibration.SATURATION_SHIFT_LIMIT,
            Config.Hsv.Base.CHROMATIC_S_MIN + Config.Hsv.Calibration.SATURATION_SHIFT_LIMIT
        )
        // graySMax는 항상 chromaticSMin - 1 이어야 함 (gap=0 유지, 역전 방지)
        // 기존 -4f gap이 unknown 영역을 만들었으므로 -1f로 변경
        val calibratedGraySMax = min(
            clamp(Config.Hsv.Base.GRAY_S_MAX + (brightnessShift * 0.12f), 15f, 40f),
            calibratedChromaticSMin - 1f
        )

        // High variance: lower black cutoff (more tolerant of shadows)
        //                 raise white cutoff (more tolerant of highlights)
        val varianceBlackAdjust = -varianceSpread * 8f   // up to -8 at max variance
        val varianceWhiteAdjust = varianceSpread * 8f    // up to +8 at max variance

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
                Config.Hsv.Base.BLACK_VALUE_CUTOFF + (brightnessShift * 0.26f) + varianceBlackAdjust,
                Config.Hsv.Base.BLACK_VALUE_CUTOFF - Config.Hsv.Calibration.BLACK_SHIFT_LIMIT - 8f,
                Config.Hsv.Base.BLACK_VALUE_CUTOFF + Config.Hsv.Calibration.BLACK_SHIFT_LIMIT
            ),
            blackSMax = Config.Hsv.Base.BLACK_S_MAX,
            whiteValueCutoff = clamp(
                Config.Hsv.Base.WHITE_VALUE_CUTOFF + (brightnessShift * 0.4f) + varianceWhiteAdjust,
                Config.Hsv.Base.WHITE_VALUE_CUTOFF - Config.Hsv.Calibration.WHITE_SHIFT_LIMIT,
                Config.Hsv.Base.WHITE_VALUE_CUTOFF + Config.Hsv.Calibration.WHITE_SHIFT_LIMIT + 8f
            ),
            whiteSMax = clamp(Config.Hsv.Base.WHITE_S_MAX + (brightnessShift * 0.08f), 22f, 42f),
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
        var brownCount = 0
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

            // Brown detection inline: checked BEFORE chromatic classification
            // to avoid double-counting. Brown pixels have orange/red hue but low value.
            if (isBrownPixel(hsv, thresholds)) {
                brownCount++
                continue
            }

            when {
                isWhitePixel(hsv, thresholds) -> whiteCount++
                isBlackPixel(hsv, thresholds) -> blackCount++
                isChromaticPixel(hsv, thresholds) -> {
                    // 완전한 chromatic 픽셀: 가중치 1.0
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
                isSoftChromaticPixel(hsv, thresholds) -> {
                    // 경계 픽셀 (채도가 chromaticSMin 바로 아래): 부분 멤버십
                    // 그림자 속 동일 색상 홀드에서 gray 오분류 방지
                    val membership = getChromaticMembership(hsv, thresholds)
                    if (membership != null) {
                        val softWeight = (hsv.s - (thresholds.chromaticSMin - Config.ChromaticSoft.SOFT_MARGIN)) /
                            Config.ChromaticSoft.SOFT_MARGIN
                        val clampedWeight = clamp(softWeight, 0.1f, 0.8f)
                        validChromaticCount++
                        saturationAccumulator += hsv.s / 255f * clampedWeight
                        val hueBin = clampInt(floor(hsv.h).toInt(), 0, 179)
                        hueHistogram[hueBin] += 1
                        membership.normalizedWeights.forEach { (label, weight) ->
                            colorWeights[label] = (colorWeights[label] ?: 0f) + weight * clampedWeight
                        }
                        familyCounts[membership.topLabel] = (familyCounts[membership.topLabel] ?: 0) + 1
                    } else {
                        grayCount++
                    }
                }
                isGrayPixel(hsv, thresholds) -> grayCount++
                else -> unknownCount++
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
            brownCount = brownCount,
            unknownCount = unknownCount,
            validChromaticCount = validChromaticCount,
            saturationAccumulator = saturationAccumulator,
            rawSaturationAccumulator = rawSaturationAccumulator,
            valueAccumulator = valueAccumulator
        )
    }

    /**
     * Brown 픽셀 판별: orange/red hue 범위에서 낮은 value + 중간 saturation.
     * 이 함수는 collectRegionColorStats 루프 내에서 호출되어 별도 pass를 제거함.
     */
    private fun isBrownPixel(hsv: Hsv, thresholds: ThresholdProfile): Boolean {
        val hueInBrownRange = (hsv.h in 5f..25f) || hsv.h >= 170f
        val valueLow = hsv.v <= Config.Hsv.Base.BROWN_VALUE_MAX
        val satMid = hsv.s >= Config.Hsv.Base.BROWN_S_MIN && hsv.s <= 180f
        val notBlack = hsv.v > thresholds.blackValueCutoff
        return hueInBrownRange && valueLow && satMid && notBlack
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

    /**
     * Soft chromatic: 채도가 chromaticSMin 바로 아래(margin 범위)인 경계 픽셀.
     * 그림자 속 동일 색상이 gray로 잘못 분류되는 것을 방지.
     * isChromaticPixel이 false일 때만 호출됨.
     */
    private fun isSoftChromaticPixel(hsv: Hsv, thresholds: ThresholdProfile): Boolean {
        val softMin = thresholds.chromaticSMin - Config.ChromaticSoft.SOFT_MARGIN
        return hsv.s >= softMin &&
            hsv.s < thresholds.chromaticSMin &&
            hsv.v >= thresholds.lowValueCutoff &&
            hsv.v <= thresholds.highValueCutoff
    }

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

            // hue 매칭이 강할 때(>0.6) 보너스를 줘서 S/V가 낮아도 정확한 hue가 보상받도록
            val hueBoost = if (hueScore > 0.6f) 1f + 0.15f * (hueScore - 0.6f) / 0.4f else 1f
            // 바닥값을 올려(0.55→0.60, 0.65→0.70) S/V 저하로 인한 과도한 패널티 방지
            val weight = (hueScore * hueScore * hueBoost) *
                (0.60f + (0.40f * saturationFactor * profile.saturationBias)) *
                (0.70f + (0.30f * valueFactor * profile.valueBias))

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
        // brown을 distribution에 명시적으로 포함
        distribution["brown"] = roundTo(stats.brownCount.toFloat() / total, 3)
        // unknown은 grayCount/brownCount를 제외한 순수 unknown만 포함
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

        // 벽 색상(gray/black/white)은 오염이 아니라 배경이므로 제외하고 계산
        // 클라이밍 벽은 대부분 gray/dark → achromatic이 높다고 오염으로 판정하면 안 됨
        val achromaticLabels = setOf("black", "gray", "white")
        val achromaticShare = achromaticLabels.sumOf { (outerDistribution[it] ?: 0f).toDouble() }.toFloat()
        val primaryShare = outerDistribution[primaryColor] ?: 0f

        // 인접 동색 홀드 보정: outer ring에 primary color가 많으면 인접 동색 홀드 존재
        // 오염이 아니라 확인(confirmation)이므로 effective share에 보너스 부여
        val adjacencyBonus = when {
            primaryShare >= 0.20f -> primaryShare * 0.4f
            primaryShare >= 0.10f -> primaryShare * 0.2f
            else -> 0f
        }

        // primary + achromatic을 제외한 나머지가 실제 오염 후보
        val excludedLabels = achromaticLabels + setOf("unknown", primaryColor)
        val competitorShare = getTopDistributionEntries(outerDistribution, 2, excludedLabels)
            .firstOrNull()
            ?.share ?: 0f
        // achromatic을 배경으로 간주해 primaryShare에 합산 + adjacency bonus
        val effectivePrimaryShare = min(1f, primaryShare + achromaticShare + adjacencyBonus)

        return clamp(
            (0.55f * (1f - effectivePrimaryShare)) +
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
        // 개별 점수에 floor(0.3)를 적용해 단일 요소가 전체 신뢰도를 붕괴시키지 않도록 함.
        // 가중 기하평균: confidence(40%) + area(20%) + fill(15%) + edge(15%) + inner(10%)
        val scores = floatArrayOf(
            max(confidenceScore, 0.3f),
            max(areaScore, 0.3f),
            max(fillScore, 0.3f),
            max(edgeScore, 0.3f),
            max(innerScore, 0.3f)
        )
        val weights = floatArrayOf(0.40f, 0.20f, 0.15f, 0.15f, 0.10f)
        // weighted geometric mean: product(score_i ^ weight_i)
        var logSum = 0f
        for (i in scores.indices) {
            logSum += weights[i] * ln(scores[i])
        }
        return clamp(exp(logSum))
    }

    private fun passesStrictColorFilter(
        result: AnalyzedHold,
        selectedColor: String,
        minColorScore: Float
    ): Boolean {
        val achromaticColors = setOf("black", "white", "gray", "brown")

        // ── Tier 1: 확정 분류 (STATUS_CLASSIFIED) ──
        if (result.colorLabel == selectedColor && result.colorStatus == STATUS_CLASSIFIED) {
            if (result.primaryColor == selectedColor) {
                // primary 일치 → 완화된 임계값
                val relaxedScoreThreshold = minColorScore * 0.70f
                if (result.colorScore < relaxedScoreThreshold) return false
                if (selectedColor !in achromaticColors && result.validPixelRatio < 0.05f) return false
                return true
            }
            // primary 불일치이지만 label은 맞음 → 기존 strict
            if (result.colorScore < minColorScore) return false
            val distributionOk = (result.colorDistribution[selectedColor] ?: 0f) >= Config.Filtering.STRICT_DISTRIBUTION_FLOOR
            val primaryOk = result.primaryColor == selectedColor
            if (!distributionOk && !primaryOk) return false
            if (selectedColor !in achromaticColors && result.validPixelRatio < Config.Scoring.MIN_FILTER_VALID_RATIO) return false
            return true
        }

        // ── Tier 2: 약한 분류 (STATUS_CLASSIFIED_WEAK) ──
        // 경계 영역의 홀드: unknown 직전이지만 primary color는 정확
        if (result.colorLabel == selectedColor && result.colorStatus == STATUS_CLASSIFIED_WEAK) {
            if (result.primaryColor == selectedColor && result.colorScore >= minColorScore * 0.55f) {
                if (selectedColor !in achromaticColors && result.validPixelRatio < 0.04f) return false
                return true
            }
            return false
        }

        // ── Tier 3: unknown 구제 ──
        // colorLabel이 unknown이지만, 색상 분포에서 target color 증거가 충분한 경우 구제
        if (result.colorLabel == "unknown" && result.primaryColor == selectedColor) {
            val targetDistShare = result.colorDistribution[selectedColor] ?: 0f
            // primary가 target이고, 분포 상 가장 높은 chromatic color가 target이어야 함
            val isTopChromatic = result.colorDistribution.entries
                .filter { it.key !in setOf("unknown", "black", "white", "gray", "brown") }
                .maxByOrNull { it.value }
                ?.let { it.key == selectedColor && it.value >= 0.15f }
                ?: false

            if (isTopChromatic && result.rawColorScore >= 0.20f) {
                Log.d(TAG, "🔄 unknown 홀드 구제: bbox=${result.hold.boundingBox}, " +
                    "primary=$selectedColor, dist=${targetDistShare}, " +
                    "rawScore=${result.rawColorScore}, status=${result.colorStatus}")
                return true
            }
        }

        return false
    }

    // computeBrownRatio removed: brown detection merged into collectRegionColorStats
    // via isBrownPixel() to avoid redundant second pass over all pixels.

    /**
     * 홀드별 적응적 채도 부스트 계수 산출.
     * 어두운 홀드(low V)와 탈채도 홀드(low S)에는 더 강한 부스트를 적용하여
     * 동일 물리 색상이 조명 차이로 놓치는 현상을 방지.
     */
    private fun computeAdaptiveBoostFactor(
        localStats: RawMaskStats,
        surroundingStats: RawMaskStats,
        globalStats: CalibrationContext
    ): Float {
        val effectiveMeanV = when {
            localStats.pixelCount >= 16 -> localStats.meanV
            surroundingStats.pixelCount >= 16 -> surroundingStats.meanV
            else -> globalStats.meanV
        }
        val effectiveMeanS = when {
            localStats.pixelCount >= 16 -> localStats.meanS
            surroundingStats.pixelCount >= 16 -> surroundingStats.meanS
            else -> globalStats.meanS
        }

        val darknessFactor = Config.Hsv.Calibration.REFERENCE_MEAN_VALUE / max(effectiveMeanV, 40f)
        val desaturationFactor = 96f / max(effectiveMeanS, 30f)

        val rawBoost = Config.Preprocessing.SATURATION_BOOST * (
            Config.Preprocessing.DARKNESS_WEIGHT * darknessFactor +
                Config.Preprocessing.DESATURATION_WEIGHT * desaturationFactor
        )

        return clamp(
            rawBoost,
            Config.Preprocessing.MIN_ADAPTIVE_BOOST,
            Config.Preprocessing.MAX_ADAPTIVE_BOOST
        )
    }

    /**
     * 픽셀 배열의 채도를 비선형(power curve) 증폭 (H, V는 유지 / S만 조정)
     * factor=1.0이면 원본 그대로.
     * 공식: boostedS = 255 * (1 - (1 - S/255)^factor)
     * → S=0 → 0, S=255 → 255 (경계 보존, 클램핑 불필요)
     * → 낮은 채도(S=50): 1-(1-0.196)^1.3 ≈ 0.244 → 62 (24% 증가)
     * → 높은 채도(S=200): 1-(1-0.784)^1.3 ≈ 0.818 → 209 (4.5% 증가)
     * → orange(S=180)과 red(S=190)의 상대적 차이가 보존됨
     */
    private fun boostPixelSaturation(pixels: IntArray, factor: Float): IntArray {
        if (factor <= 1f) return pixels
        val factorD = factor.toDouble()
        return IntArray(pixels.size) { i ->
            val pixel = pixels[i]
            val r = ((pixel shr 16) and 0xFF).toFloat()
            val g = ((pixel shr 8) and 0xFF).toFloat()
            val b = (pixel and 0xFF).toFloat()
            val hsv = rgbToHsv180(r, g, b)
            // Power curve: maps [0,255]→[0,255] with no clipping needed
            val sNorm = (hsv.s / 255f).toDouble()
            val boostedS = (255.0 * (1.0 - Math.pow(1.0 - sNorm, factorD))).toFloat()
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
        private const val STATUS_CLASSIFIED_WEAK = "classified_weak"
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

        // ── 근접 동색 홀드 구제 상수 ──
        // 정규화 좌표(0~1) 기준 거리. 0.18 ≈ 이미지 크기의 18%
        // 볼더링 루트에서 인접 홀드 간 일반적 거리
        private const val PROXIMITY_THRESHOLD = 0.18f
        // 근접 가중치 최대값: 너무 크면 오탐, 너무 작으면 효과 없음
        private const val PROXIMITY_MAX_BOOST = 0.12f
        // 구제 대상 홀드의 target 색상 최소 분포 비율
        private const val PROXIMITY_MIN_TARGET_SHARE = 0.10f
        // 근접 가중치 반영 후 최소 점수 기준
        private const val PROXIMITY_MIN_RESCUE_SCORE = 0.15f
    }
}
