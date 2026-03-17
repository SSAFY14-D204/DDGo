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
        val valueBias: Float
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
                const val CHROMATIC_S_MIN = 45f
                const val GRAY_S_MAX = 35f
                const val LOW_VALUE_CUTOFF = 45f
                const val HIGH_VALUE_CUTOFF = 245f
                const val BLACK_VALUE_CUTOFF = 55f
                const val BLACK_S_MAX = 70f
                const val WHITE_VALUE_CUTOFF = 210f
                const val WHITE_S_MAX = 35f
                const val BLACK_WHITE_RATIO = 0.6f
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
            const val MIN_VALID_RATIO = 0.12f
            const val MIN_VALID_PIXEL_FLOOR = 20
            const val MIN_FILTER_VALID_RATIO = 0.15f
            const val MIN_PEAK_SHARE = 0.32f
            const val MIN_PRIMARY_SHARE = 0.46f
            const val MIN_PRIMARY_MARGIN = 0.1f
            const val MIXED_COLOR_MARGIN = 0.07f
            const val UNKNOWN_SCORE_CAP = 0.45f
            const val CONTAMINATION_WARN = 0.33f
            const val CONTAMINATION_REJECT = 0.58f
            const val CONFIDENT_RELIABILITY_FLOOR = 0.45f
            const val LOW_RELIABILITY_FLOOR = 0.3f
        }

        object Filtering {
            const val STRICT_DISTRIBUTION_FLOOR = 0.52f
        }
    }

    private val chromaticColorProfiles = listOf(
        ChromaticColorProfile("red", intArrayOf(0, 179), 18f, 1.04f, 0.96f),
        ChromaticColorProfile("orange", intArrayOf(16), 14f, 1.02f, 1f),
        ChromaticColorProfile("yellow", intArrayOf(28), 16f, 0.98f, 1.08f),
        ChromaticColorProfile("green", intArrayOf(58), 26f, 0.98f, 0.98f),
        ChromaticColorProfile("blue", intArrayOf(108), 24f, 1f, 0.94f),
        ChromaticColorProfile("purple", intArrayOf(145), 18f, 1.02f, 0.96f),
        ChromaticColorProfile("pink", intArrayOf(166), 14f, 0.95f, 1.06f)
    )

    private val chromaticLabels = chromaticColorProfiles.map { it.label }
    private val outputLabels = chromaticLabels + listOf("white", "black", "unknown")

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
        scoreThreshold: Float = 0.55f
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

        val polygonRawStats = collectRawMaskStats(bboxPixels, maskData.mask)
        val surroundingRawStats = collectRawMaskStats(bboxPixels, maskData.outerMask)
        val thresholdProfile = calibrateThresholdProfile(
            globalStats = calibrationContext,
            localStats = polygonRawStats,
            surroundingStats = surroundingRawStats
        )

        val innerStats = collectRegionColorStats(bboxPixels, maskData.innerMask, thresholdProfile)
        val outerStats = collectRegionColorStats(bboxPixels, maskData.outerMask, thresholdProfile)
        val innerDistribution = buildColorDistribution(innerStats)
        val outerDistribution = buildColorDistribution(outerStats)
        val topEntries = getTopDistributionEntries(innerDistribution, 2, setOf("unknown"))
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
        var colorScore = clamp(rawColorScore * max(detectionReliability, 0.05f))
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

        return ThresholdProfile(
            chromaticSMin = clamp(
                Config.Hsv.Base.CHROMATIC_S_MIN + (brightnessShift * 0.35f),
                Config.Hsv.Base.CHROMATIC_S_MIN - Config.Hsv.Calibration.SATURATION_SHIFT_LIMIT,
                Config.Hsv.Base.CHROMATIC_S_MIN + Config.Hsv.Calibration.SATURATION_SHIFT_LIMIT
            ),
            graySMax = clamp(Config.Hsv.Base.GRAY_S_MAX + (brightnessShift * 0.12f), 20f, 55f),
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
            whiteSMax = clamp(Config.Hsv.Base.WHITE_S_MAX + (brightnessShift * 0.08f), 24f, 44f),
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
        val saturationFactor = clamp(
            (hsv.s - thresholds.chromaticSMin) / max(1f, 255f - thresholds.chromaticSMin)
        )
        val valueFactor = clamp(
            (hsv.v - thresholds.lowValueCutoff) / max(1f, thresholds.highValueCutoff - thresholds.lowValueCutoff)
        )

        chromaticColorProfiles.forEach { profile ->
            val distance = profile.centers.minOf { center ->
                circularHueDistance(hsv.h, center.toFloat())
            }
            val hueScore = clamp(1f - (distance / profile.hueTolerance))
            if (hueScore <= 0f) {
                rawWeights[profile.label] = 0f
                return@forEach
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

        val distribution = buildColorDistributionTemplate()
        chromaticLabels.forEach { label ->
            distribution[label] = roundTo((stats.colorWeights[label] ?: 0f) / stats.totalPixels.toFloat(), 3)
        }
        distribution["white"] = roundTo(stats.whiteCount.toFloat() / stats.totalPixels.toFloat(), 3)
        distribution["black"] = roundTo(stats.blackCount.toFloat() / stats.totalPixels.toFloat(), 3)
        distribution["unknown"] = roundTo(
            (stats.grayCount + stats.unknownCount).toFloat() / stats.totalPixels.toFloat(),
            3
        )
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
        if (result.primaryColor != selectedColor) return false
        if (result.colorStatus != STATUS_CLASSIFIED) return false
        if ((result.colorDistribution[selectedColor] ?: 0f) < Config.Filtering.STRICT_DISTRIBUTION_FLOOR) return false
        if (result.colorScore < minColorScore) return false
        if (selectedColor !in setOf("black", "white") && result.validPixelRatio < Config.Scoring.MIN_FILTER_VALID_RATIO) {
            return false
        }
        return true
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
