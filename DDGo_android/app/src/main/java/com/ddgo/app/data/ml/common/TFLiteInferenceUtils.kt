package com.ddgo.app.data.ml.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * TFLite 추론 공통 유틸리티.
 *
 * 이 파일은 domain 계층을 import 하지 않습니다.
 * PersonDetectorImpl과 HoldDetectorImpl 양쪽에서 공유합니다.
 *
 * 단일 출력 detection 모델 파이프라인:
 * letterbox → normalizeToByteBuffer → createInterpreter + run → parseOutputTensor
 *   → filterByConfidence → applyNms → scaleToNormalized
 *
 * 세그멘테이션 출력 모델은 runSegmentationInference()에서 별도 처리합니다.
 */
object TFLiteInferenceUtils {

    // ──────────────────────────────────────────────────────────────────────────
    // 내부 데이터 클래스
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Letterbox 변환 결과 — 역변환(스케일 복원)에 필요한 파라미터를 함께 보존합니다.
     *
     * @param bitmap          변환된 정사각형 Bitmap (modelSize × modelSize, ARGB_8888)
     * @param padLeft         원본 이미지를 중앙 정렬하기 위해 왼쪽에 추가된 픽셀 수
     * @param padTop          원본 이미지를 중앙 정렬하기 위해 위쪽에 추가된 픽셀 수
     * @param scale           원본 → letterbox 스케일 비율 (min(modelSize/w, modelSize/h))
     * @param originalWidth   원본 Bitmap 너비 (픽셀)
     * @param originalHeight  원본 Bitmap 높이 (픽셀)
     */
    data class LetterboxInfo(
        val bitmap: Bitmap,
        val padLeft: Int,
        val padTop: Int,
        val scale: Float,
        val originalWidth: Int,
        val originalHeight: Int
    )

    /**
     * NMS 전 단일 검출 결과 — letterbox 이미지 좌표계(픽셀)로 표현됩니다.
     *
     * @param x1, y1, x2, y2  xyxy 바운딩박스
     * @param confidence       max class score (임계값 필터링 이후)
     * @param classIndex       argmax class index
     */
    data class RawDetection(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val confidence: Float,
        val classIndex: Int
    )

    private data class FlatTensorOutput(
        val shape: IntArray,
        val values: FloatArray
    )

    data class SegmentationDetection(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val confidence: Float,
        val classIndex: Int,
        val polygon: List<NormalizedPoint>
    )

    data class NormalizedPoint(
        val x: Float,
        val y: Float
    )

    private data class SegmentationCandidate(
        val detection: RawDetection,
        val maskCoefficients: FloatArray
    )

    private data class ProtoTensor(
        val channels: Int,
        val height: Int,
        val width: Int,
        val values: FloatArray
    )

    private data class IntPoint(
        val x: Int,
        val y: Int
    )

    private data class EdgeSegment(
        val start: IntPoint,
        val end: IntPoint
    )

    private data class ComponentMask(
        val mask: BooleanArray,
        val width: Int,
        val height: Int,
        val offsetX: Int,
        val offsetY: Int
    )

    private data class BoundaryLoopTrace(
        val points: List<IntPoint>,
        val segmentIndices: List<Int>,
        val isClosed: Boolean
    )

    private const val MAX_SEGMENTATION_POLYGON_POINTS = 180

    // ──────────────────────────────────────────────────────────────────────────
    // 1. Letterbox 리사이즈
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Aspect-ratio 보존 리사이즈 + 회색(114, 114, 114) 패딩.
     *
     * Python one_frame_draw.py의 letterbox() 함수와 동일한 로직입니다.
     * 패딩 색상 114/255 = 0.4471 (YOLOv8 기본값)
     *
     * @param src       원본 Bitmap
     * @param modelSize YOLOv8 입력 크기 (320 또는 640)
     * @return          LetterboxInfo (변환된 비트맵 + 역변환 파라미터)
     */
    fun letterbox(src: Bitmap, modelSize: Int): LetterboxInfo {
        val origW = src.width
        val origH = src.height

        val scale = minOf(modelSize.toFloat() / origW, modelSize.toFloat() / origH)
        val scaledW = (origW * scale).toInt()
        val scaledH = (origH * scale).toInt()
        val padLeft = (modelSize - scaledW) / 2
        val padTop  = (modelSize - scaledH) / 2

        // 정사각형 캔버스 생성 후 회색 배경 채우기
        val result = Bitmap.createBitmap(modelSize, modelSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.rgb(114, 114, 114))

        // 스케일된 원본을 중앙에 그리기
        val scaled = Bitmap.createScaledBitmap(src, scaledW, scaledH, true)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(scaled, padLeft.toFloat(), padTop.toFloat(), paint)
        scaled.recycle()

        return LetterboxInfo(
            bitmap        = result,
            padLeft       = padLeft,
            padTop        = padTop,
            scale         = scale,
            originalWidth = origW,
            originalHeight= origH
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2. Bitmap → ByteBuffer 정규화
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Bitmap → ByteBuffer (NHWC float32, 값 0~1).
     *
     * 텐서 형태: [1, modelSize, modelSize, 3]
     * 채널 순서: R, G, B
     *
     * @param bitmap    letterbox 변환된 정사각형 Bitmap
     * @param modelSize 모델 입력 크기
     * @return          직접 ByteBuffer (native byte order, rewind 완료)
     */
    fun normalizeToByteBuffer(bitmap: Bitmap, modelSize: Int): ByteBuffer {
        val pixels = IntArray(modelSize * modelSize)
        bitmap.getPixels(pixels, 0, modelSize, 0, 0, modelSize, modelSize)

        // [1, H, W, 3] → 1 * modelSize * modelSize * 3 floats * 4 bytes
        val buffer = ByteBuffer.allocateDirect(modelSize * modelSize * 3 * 4)
            .order(ByteOrder.nativeOrder())

        for (pixel in pixels) {
            buffer.putFloat(Color.red(pixel)   / 255f)
            buffer.putFloat(Color.green(pixel) / 255f)
            buffer.putFloat(Color.blue(pixel)  / 255f)
        }
        buffer.rewind()
        return buffer
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3. TFLite Interpreter 초기화
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Assets에서 TFLite 모델을 MMap으로 로드하여 Interpreter를 생성합니다.
     *
     * @param context   ApplicationContext
     * @param modelPath assets 상대 경로 (예: "models/person_detect_v0n_320.tflite")
     * @return          초기화된 Interpreter (사용 후 반드시 close() 호출)
     */
    fun createInterpreter(context: Context, modelPath: String): Interpreter {
        val fd = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fd.fileDescriptor)
        val fileChannel = inputStream.channel
        val mappedBuffer = fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fd.startOffset,
            fd.declaredLength
        )
        inputStream.close()

        val options = Interpreter.Options().apply {
            numThreads = 4
            // XNNPACK 비활성화: ARM SVE 최적화 명령어를 사용하며
            // berberis(에뮬레이터 ARM→x86 변환기)가 이를 지원하지 않아 크래시 발생
            setUseXNNPACK(false)
        }
        return Interpreter(mappedBuffer, options)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 4. 출력 텐서 파싱
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * 출력 텐서를 파싱하여 각 detection row를 FloatArray로 반환합니다.
     *
     * YOLOv8 출력 포맷 자동 판별:
     *   - shape [1, F, N] (F < N): features가 행 → transpose 필요
     *   - shape [1, N, F] (N > F): detections가 행 → 그대로 사용
     *
     * 반환 각 row: [cx, cy, w, h, score_class0, score_class1, ...]
     *
     * @param interpreter  실행 완료된 Interpreter
     * @param outputBuffer 출력 텐서 버퍼 (shape: [1][dim1][dim2])
     * @return             각 detection의 FloatArray 리스트
     */
    fun parseOutputTensor(
        interpreter: Interpreter,
        outputBuffer: Array<Array<FloatArray>>
    ): List<FloatArray> {
        val shape = interpreter.getOutputTensor(0).shape()
        // shape[0]=1(batch), shape[1]=dim1, shape[2]=dim2
        val dim1 = shape[1]
        val dim2 = shape[2]
        val raw = outputBuffer[0]  // [dim1][dim2]

        return if (dim1 < dim2) {
            // [B, F, N] 포맷 — features가 행, detections가 열 → transpose
            // raw[f][n] → result[n][f]
            List(dim2) { n ->
                FloatArray(dim1) { f -> raw[f][n] }
            }
        } else {
            // [B, N, F] 포맷 — detections가 행 → 그대로
            List(dim1) { n -> raw[n].copyOf() }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 5. Confidence 필터링 + xywh → xyxy 변환
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * xywh(center) → xyxy 변환 후 confidence threshold 적용합니다.
     *
     * @param rows                parseOutputTensor 결과 (각 row: [cx, cy, w, h, score0, ...])
     * @param confidenceThreshold 기본 0.25f
     * @return                    임계값 통과한 RawDetection 리스트 (NMS 이전)
     */
    fun filterByConfidence(
        rows: List<FloatArray>,
        confidenceThreshold: Float = 0.25f
    ): List<RawDetection> {
        val result = mutableListOf<RawDetection>()
        for (row in rows) {
            if (row.size < 5) continue

            val cx = row[0]; val cy = row[1]
            val w  = row[2]; val h  = row[3]

            // class scores: row[4..]
            val numClasses = row.size - 4
            if (numClasses <= 0) continue

            var maxScore = -Float.MAX_VALUE
            var maxIdx   = 0
            for (c in 0 until numClasses) {
                val score = row[4 + c]
                if (score > maxScore) {
                    maxScore = score
                    maxIdx   = c
                }
            }

            if (maxScore < confidenceThreshold) continue

            val x1 = cx - w / 2f
            val y1 = cy - h / 2f
            val x2 = cx + w / 2f
            val y2 = cy + h / 2f

            result.add(RawDetection(x1, y1, x2, y2, maxScore, maxIdx))
        }
        return result
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 6. NMS
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Per-class Greedy NMS (Non-Maximum Suppression).
     *
     * @param detections   filterByConfidence 결과
     * @param iouThreshold 기본 0.45f
     * @return             NMS 통과한 RawDetection 리스트
     */
    fun applyNms(
        detections: List<RawDetection>,
        iouThreshold: Float = 0.45f
    ): List<RawDetection> {
        if (detections.isEmpty()) return emptyList()

        // classIndex별로 그룹화
        val byClass = detections.groupBy { it.classIndex }
        val kept = mutableListOf<RawDetection>()

        for ((_, candidates) in byClass) {
            // confidence 내림차순 정렬
            val sorted = candidates.sortedByDescending { it.confidence }.toMutableList()
            val suppressed = BooleanArray(sorted.size)

            for (i in sorted.indices) {
                if (suppressed[i]) continue
                kept.add(sorted[i])
                for (j in i + 1 until sorted.size) {
                    if (!suppressed[j] && iou(sorted[i], sorted[j]) > iouThreshold) {
                        suppressed[j] = true
                    }
                }
            }
        }
        return kept
    }

    /** IoU (Intersection over Union) 계산 */
    private fun iou(a: RawDetection, b: RawDetection): Float {
        val interX1 = maxOf(a.x1, b.x1)
        val interY1 = maxOf(a.y1, b.y1)
        val interX2 = minOf(a.x2, b.x2)
        val interY2 = minOf(a.y2, b.y2)

        val interW = maxOf(0f, interX2 - interX1)
        val interH = maxOf(0f, interY2 - interY1)
        val interArea = interW * interH

        val areaA = (a.x2 - a.x1) * (a.y2 - a.y1)
        val areaB = (b.x2 - b.x1) * (b.y2 - b.y1)
        val union = areaA + areaB - interArea

        return if (union <= 0f) 0f else interArea / (union + 1e-6f)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 7. 좌표 역변환 (letterbox 픽셀 → 원본 정규화 0~1)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Letterbox 픽셀 좌표 → 원본 이미지 정규화 좌표 (0~1) 로 역변환합니다.
     *
     * 역변환 수식:
     *   origX = (letterboxX - padLeft) / scale
     *   normX = clamp(origX / originalWidth, 0f, 1f)
     *
     * @param detections NMS 통과한 RawDetection 리스트 (letterbox 좌표계)
     * @param info       letterbox 변환 파라미터
     * @return           각 detection의 FloatArray: [normLeft, normTop, normRight, normBottom, confidence, classIndex]
     */
    fun scaleToNormalized(
        detections: List<RawDetection>,
        info: LetterboxInfo
    ): List<FloatArray> {
        // 모델이 정규화 좌표(0~1)를 출력하므로 → modelSize 곱해 letterbox 픽셀 좌표로 변환 후 역변환
        // (cx, cy, w, h 모두 [0, 1] 범위로 정규화되어 있음)
        val modelSize = info.bitmap.width.toFloat()  // letterbox 정사각형 크기 (320 or 640)
        return detections.map { det ->
            val origX1 = (det.x1 * modelSize - info.padLeft)  / info.scale
            val origY1 = (det.y1 * modelSize - info.padTop)   / info.scale
            val origX2 = (det.x2 * modelSize - info.padLeft)  / info.scale
            val origY2 = (det.y2 * modelSize - info.padTop)   / info.scale

            val normLeft   = (origX1 / info.originalWidth).coerceIn(0f, 1f)
            val normTop    = (origY1 / info.originalHeight).coerceIn(0f, 1f)
            val normRight  = (origX2 / info.originalWidth).coerceIn(0f, 1f)
            val normBottom = (origY2 / info.originalHeight).coerceIn(0f, 1f)

            floatArrayOf(normLeft, normTop, normRight, normBottom, det.confidence, det.classIndex.toFloat())
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 8. 전체 파이프라인 편의 메서드
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * 단일 프레임에 대한 전체 TFLite 추론 파이프라인을 실행합니다.
     *
     * letterbox → normalizeToByteBuffer → run → parseOutputTensor
     * → filterByConfidence → applyNms → scaleToNormalized
     *
     * @param bitmap              입력 프레임 Bitmap (임의 크기)
     * @param interpreter         이미 초기화된 TFLite Interpreter
     * @param modelSize           320 또는 640
     * @param confidenceThreshold 기본 0.25f
     * @param iouThreshold        기본 0.45f
     * @return  Pair<List<FloatArray>, LetterboxInfo>
     *          - first: 정규화된 검출 결과 ([normLeft, normTop, normRight, normBottom, conf, classIdx])
     *          - second: letterbox 정보 (필요 시 역변환에 재사용)
     */
    fun runInference(
        bitmap: Bitmap,
        interpreter: Interpreter,
        modelSize: Int,
        confidenceThreshold: Float = 0.25f,
        iouThreshold: Float = 0.45f
    ): Pair<List<FloatArray>, LetterboxInfo> {
        // 1. Letterbox 리사이즈
        val lbInfo = letterbox(bitmap, modelSize)

        // 2. ByteBuffer 정규화
        val inputBuffer = normalizeToByteBuffer(lbInfo.bitmap, modelSize)

        // 3. 출력 텐서 버퍼 사전 할당
        val outShape = interpreter.getOutputTensor(0).shape()
        // outShape: [1, dim1, dim2]
        val outputBuffer = Array(1) { Array(outShape[1]) { FloatArray(outShape[2]) } }

        // 4. 추론 실행
        interpreter.run(inputBuffer, outputBuffer)

        // 5. 출력 파싱 (shape 자동 판별)
        val rows = parseOutputTensor(interpreter, outputBuffer)

        // 6. Confidence 필터 + xywh → xyxy
        val filtered = filterByConfidence(rows, confidenceThreshold)

        // 7. NMS
        val nmsResult = applyNms(filtered, iouThreshold)

        // 8. 좌표 정규화
        val normalized = scaleToNormalized(nmsResult, lbInfo)

        // letterbox bitmap 정리
        lbInfo.bitmap.recycle()

        return Pair(normalized, lbInfo)
    }

    /**
     * 세그멘테이션 TFLite 모델(one_frame_seg_draw.py 기준)에서
     * bounding box + polygon contour를 복원해 정규화 좌표로 반환합니다.
     *
     * 모델 출력:
     * - prediction tensor: [1, F, N] 또는 [1, N, F]
     * - proto tensor:      [1, C, H, W] 또는 [1, H, W, C]
     */
    fun runSegmentationInference(
        bitmap: Bitmap,
        interpreter: Interpreter,
        modelSize: Int,
        numClasses: Int,
        confidenceThreshold: Float = 0.25f,
        iouThreshold: Float = 0.45f,
        targetClassIndices: Set<Int>? = null,
        maxDetections: Int = 300
    ): List<SegmentationDetection> {
        val lbInfo = letterbox(bitmap, modelSize)
        try {
            val inputBuffer = normalizeToByteBuffer(lbInfo.bitmap, modelSize)
            val outputs = runMultiOutputFloatInference(interpreter, inputBuffer)

            val (candidates, proto) = parseSegmentationCandidates(
                outputs = outputs,
                numClasses = numClasses,
                confidenceThreshold = confidenceThreshold,
                inputWidth = modelSize,
                inputHeight = modelSize,
                targetClassIndices = targetClassIndices
            )

            if (candidates.isEmpty()) return emptyList()

            val boxesOnOriginal = scaleLetterboxDetectionsToOriginalPixels(
                detections = candidates.map { it.detection },
                info = lbInfo
            )
            val keepIndices = selectNmsIndices(
                detections = boxesOnOriginal,
                iouThreshold = iouThreshold,
                maxDetections = maxDetections
            )

            return keepIndices.map { index ->
                val candidate = candidates[index]
                val boxOnOriginal = boxesOnOriginal[index]
                val inputMask = buildInputSpaceMask(
                    proto = proto,
                    maskCoefficients = candidate.maskCoefficients,
                    inputDetection = candidate.detection,
                    inputWidth = modelSize,
                    inputHeight = modelSize
                )
                val originalMask = scaleMaskToOriginal(
                    mask = inputMask,
                    inputWidth = modelSize,
                    inputHeight = modelSize,
                    info = lbInfo
                )
                val polygon = buildNormalizedPolygon(
                    mask = originalMask,
                    maskWidth = lbInfo.originalWidth,
                    maskHeight = lbInfo.originalHeight,
                    fallbackBox = boxOnOriginal
                )

                SegmentationDetection(
                    left = (boxOnOriginal.x1 / lbInfo.originalWidth).coerceIn(0f, 1f),
                    top = (boxOnOriginal.y1 / lbInfo.originalHeight).coerceIn(0f, 1f),
                    right = (boxOnOriginal.x2 / lbInfo.originalWidth).coerceIn(0f, 1f),
                    bottom = (boxOnOriginal.y2 / lbInfo.originalHeight).coerceIn(0f, 1f),
                    confidence = boxOnOriginal.confidence,
                    classIndex = boxOnOriginal.classIndex,
                    polygon = polygon
                )
            }
        } finally {
            lbInfo.bitmap.recycle()
        }
    }

    private fun runMultiOutputFloatInference(
        interpreter: Interpreter,
        inputBuffer: ByteBuffer
    ): List<FlatTensorOutput> {
        val outputCount = interpreter.outputTensorCount
        val outputBuffers = mutableMapOf<Int, Any>()
        val outputShapes = mutableListOf<IntArray>()

        for (outputIndex in 0 until outputCount) {
            val tensor = interpreter.getOutputTensor(outputIndex)
            require(tensor.dataType() == DataType.FLOAT32) {
                "지원하지 않는 segmentation output dtype: ${tensor.dataType()} (index=$outputIndex)"
            }

            val buffer = ByteBuffer.allocateDirect(tensor.numBytes())
                .order(ByteOrder.nativeOrder())
            outputBuffers[outputIndex] = buffer
            outputShapes += tensor.shape()
        }

        interpreter.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputBuffers)

        return outputShapes.mapIndexed { index, shape ->
            val buffer = outputBuffers[index] as ByteBuffer
            buffer.rewind()

            val values = FloatArray(buffer.remaining() / Float.SIZE_BYTES)
            for (i in values.indices) {
                values[i] = buffer.float
            }

            FlatTensorOutput(shape = shape, values = values)
        }
    }

    private fun parseSegmentationCandidates(
        outputs: List<FlatTensorOutput>,
        numClasses: Int,
        confidenceThreshold: Float,
        inputWidth: Int,
        inputHeight: Int,
        targetClassIndices: Set<Int>? = null
    ): Pair<List<SegmentationCandidate>, ProtoTensor> {
        val predictionTensor = outputs
            .filter { squeezeShape(it.shape).size == 2 }
            .maxByOrNull { squeezeShape(it.shape).fold(1) { acc, dim -> acc * dim } }
            ?: error("Segmentation prediction tensor를 찾지 못했습니다: ${outputs.map { it.shape.toList() }}")

        val protoTensor = outputs
            .filter { squeezeShape(it.shape).size == 3 }
            .maxByOrNull { squeezeShape(it.shape).fold(1) { acc, dim -> acc * dim } }
            ?: error("Segmentation proto tensor를 찾지 못했습니다: ${outputs.map { it.shape.toList() }}")

        val predictionRows = standardizePredictionRows(predictionTensor)
        val standardizedProto = standardizeProtoTensor(protoTensor)
        val numMasks = standardizedProto.channels
        val result = mutableListOf<SegmentationCandidate>()

        for (row in predictionRows) {
            if (row.size <= 4 + numMasks) continue

            val scoreBlockSize = row.size - 4 - numMasks
            if (scoreBlockSize <= 0) continue

            val cx = row[0]
            val cy = row[1]
            val w = row[2]
            val h = row[3]

            if (!cx.isFinite() || !cy.isFinite() || !w.isFinite() || !h.isFinite()) continue

            val scoreStart = 4
            val scoreEnd = 4 + scoreBlockSize
            val scores = row.copyOfRange(scoreStart, scoreEnd)
            val maskCoefficients = row.copyOfRange(scoreEnd, row.size)
            if (maskCoefficients.size != numMasks || maskCoefficients.any { !it.isFinite() }) continue

            val (classIndex, confidence) = when {
                scores.size == numClasses -> {
                    val bestIndex = scores.indices.maxByOrNull { scores[it] } ?: 0
                    bestIndex to scores[bestIndex]
                }
                scores.size == numClasses + 1 && numClasses > 0 -> {
                    val objectness = scores[0]
                    val classScores = scores.copyOfRange(1, scores.size)
                    val bestIndex = classScores.indices.maxByOrNull { classScores[it] } ?: 0
                    bestIndex to (objectness * classScores[bestIndex])
                }
                scores.size == 1 -> 0 to scores[0]
                else -> continue
            }

            if (!confidence.isFinite() || confidence < confidenceThreshold) continue
            if (targetClassIndices != null && classIndex !in targetClassIndices) continue

            var x1 = cx - w / 2f
            var y1 = cy - h / 2f
            var x2 = cx + w / 2f
            var y2 = cy + h / 2f

            val maxAbsCoord = maxOf(abs(x1), abs(y1), abs(x2), abs(y2))
            if (maxAbsCoord <= 2f) {
                x1 *= inputWidth.toFloat()
                x2 *= inputWidth.toFloat()
                y1 *= inputHeight.toFloat()
                y2 *= inputHeight.toFloat()
            }

            if (!x1.isFinite() || !y1.isFinite() || !x2.isFinite() || !y2.isFinite()) continue
            if (x2 <= x1 || y2 <= y1) continue

            result += SegmentationCandidate(
                detection = RawDetection(
                    x1 = x1,
                    y1 = y1,
                    x2 = x2,
                    y2 = y2,
                    confidence = confidence,
                    classIndex = classIndex
                ),
                maskCoefficients = maskCoefficients
            )
        }

        return result to standardizedProto
    }

    private fun standardizePredictionRows(output: FlatTensorOutput): List<FloatArray> {
        val shape = squeezeShape(output.shape)
        require(shape.size == 2) {
            "Unsupported segmentation prediction tensor shape: ${output.shape.toList()}"
        }

        val dim0 = shape[0]
        val dim1 = shape[1]
        val values = output.values

        return if (dim0 <= 128 && dim0 < dim1) {
            List(dim1) { rowIndex ->
                FloatArray(dim0) { featureIndex ->
                    values[featureIndex * dim1 + rowIndex]
                }
            }
        } else {
            List(dim0) { rowIndex ->
                values.copyOfRange(rowIndex * dim1, (rowIndex + 1) * dim1)
            }
        }
    }

    private fun standardizeProtoTensor(output: FlatTensorOutput): ProtoTensor {
        val shape = squeezeShape(output.shape)
        require(shape.size == 3) {
            "Unsupported segmentation proto tensor shape: ${output.shape.toList()}"
        }

        val channelAxis = shape.indices.minByOrNull { shape[it] }
            ?: error("Segmentation proto tensor shape가 비어 있습니다: ${output.shape.toList()}")
        val channelCount = shape[channelAxis]

        require(channelCount <= 128) {
            "Proto tensor의 channel axis를 추론할 수 없습니다: ${output.shape.toList()}"
        }

        val (height, width) = when (channelAxis) {
            0 -> shape[1] to shape[2]
            1 -> shape[0] to shape[2]
            else -> shape[0] to shape[1]
        }
        val values = FloatArray(channelCount * height * width)
        val raw = output.values

        when (channelAxis) {
            0 -> raw.copyInto(values)
            1 -> {
                for (h in 0 until shape[0]) {
                    for (c in 0 until shape[1]) {
                        for (w in 0 until shape[2]) {
                            val srcIndex = ((h * shape[1]) + c) * shape[2] + w
                            val dstIndex = ((c * height) + h) * width + w
                            values[dstIndex] = raw[srcIndex]
                        }
                    }
                }
            }
            else -> {
                for (h in 0 until shape[0]) {
                    for (w in 0 until shape[1]) {
                        for (c in 0 until shape[2]) {
                            val srcIndex = ((h * shape[1]) + w) * shape[2] + c
                            val dstIndex = ((c * height) + h) * width + w
                            values[dstIndex] = raw[srcIndex]
                        }
                    }
                }
            }
        }

        return ProtoTensor(
            channels = channelCount,
            height = height,
            width = width,
            values = values
        )
    }

    private fun scaleLetterboxDetectionsToOriginalPixels(
        detections: List<RawDetection>,
        info: LetterboxInfo
    ): List<RawDetection> = detections.map { det ->
        RawDetection(
            x1 = ((det.x1 - info.padLeft) / info.scale).coerceIn(0f, info.originalWidth.toFloat()),
            y1 = ((det.y1 - info.padTop) / info.scale).coerceIn(0f, info.originalHeight.toFloat()),
            x2 = ((det.x2 - info.padLeft) / info.scale).coerceIn(0f, info.originalWidth.toFloat()),
            y2 = ((det.y2 - info.padTop) / info.scale).coerceIn(0f, info.originalHeight.toFloat()),
            confidence = det.confidence,
            classIndex = det.classIndex
        )
    }.filter { it.x2 > it.x1 && it.y2 > it.y1 }

    private fun normalizeOriginalPixelDetections(
        detections: List<RawDetection>,
        originalWidth: Int,
        originalHeight: Int
    ): List<FloatArray> = detections.map { det ->
        floatArrayOf(
            (det.x1 / originalWidth).coerceIn(0f, 1f),
            (det.y1 / originalHeight).coerceIn(0f, 1f),
            (det.x2 / originalWidth).coerceIn(0f, 1f),
            (det.y2 / originalHeight).coerceIn(0f, 1f),
            det.confidence,
            det.classIndex.toFloat()
        )
    }

    private fun selectNmsIndices(
        detections: List<RawDetection>,
        iouThreshold: Float,
        maxDetections: Int
    ): List<Int> {
        if (detections.isEmpty()) return emptyList()

        val keptIndices = mutableListOf<Int>()
        val byClass = detections.withIndex().groupBy { it.value.classIndex }

        for ((_, indexedCandidates) in byClass) {
            val sorted = indexedCandidates.sortedByDescending { it.value.confidence }
            val suppressed = BooleanArray(sorted.size)

            for (i in sorted.indices) {
                if (suppressed[i]) continue
                keptIndices += sorted[i].index
                for (j in i + 1 until sorted.size) {
                    if (!suppressed[j] && iou(sorted[i].value, sorted[j].value) > iouThreshold) {
                        suppressed[j] = true
                    }
                }
            }
        }

        return keptIndices
            .sortedByDescending { detections[it].confidence }
            .let { if (maxDetections > 0) it.take(maxDetections) else it }
    }

    private fun buildInputSpaceMask(
        proto: ProtoTensor,
        maskCoefficients: FloatArray,
        inputDetection: RawDetection,
        inputWidth: Int,
        inputHeight: Int
    ): FloatArray {
        if (maskCoefficients.isEmpty()) return FloatArray(inputWidth * inputHeight)

        val protoSize = proto.height * proto.width
        val mask = FloatArray(protoSize)
        for (channel in 0 until proto.channels) {
            val coefficient = maskCoefficients[channel]
            if (!coefficient.isFinite() || coefficient == 0f) continue

            val channelOffset = channel * protoSize
            for (index in 0 until protoSize) {
                mask[index] += coefficient * proto.values[channelOffset + index]
            }
        }

        for (index in mask.indices) {
            mask[index] = sigmoid(mask[index])
        }

        val cropX1 = floor(inputDetection.x1 * proto.width / inputWidth.toFloat()).toInt().coerceIn(0, proto.width)
        val cropY1 = floor(inputDetection.y1 * proto.height / inputHeight.toFloat()).toInt().coerceIn(0, proto.height)
        val cropX2 = ceil(inputDetection.x2 * proto.width / inputWidth.toFloat()).toInt().coerceIn(0, proto.width)
        val cropY2 = ceil(inputDetection.y2 * proto.height / inputHeight.toFloat()).toInt().coerceIn(0, proto.height)

        if (cropX2 > cropX1 && cropY2 > cropY1) {
            for (y in 0 until proto.height) {
                val rowOffset = y * proto.width
                for (x in 0 until proto.width) {
                    if (x !in cropX1 until cropX2 || y !in cropY1 until cropY2) {
                        mask[rowOffset + x] = 0f
                    }
                }
            }
        }

        return resizeFloatGrid(mask, proto.width, proto.height, inputWidth, inputHeight)
    }

    private fun scaleMaskToOriginal(
        mask: FloatArray,
        inputWidth: Int,
        inputHeight: Int,
        info: LetterboxInfo,
        threshold: Float = 0.5f
    ): BooleanArray {
        if (mask.isEmpty()) return BooleanArray(info.originalWidth * info.originalHeight)

        val scaledWidth = (info.originalWidth * info.scale).toInt().coerceIn(1, inputWidth)
        val scaledHeight = (info.originalHeight * info.scale).toInt().coerceIn(1, inputHeight)
        val cropLeft = info.padLeft.coerceIn(0, inputWidth - 1)
        val cropTop = info.padTop.coerceIn(0, inputHeight - 1)
        val cropWidth = minOf(scaledWidth, inputWidth - cropLeft).coerceAtLeast(1)
        val cropHeight = minOf(scaledHeight, inputHeight - cropTop).coerceAtLeast(1)

        val cropped = FloatArray(cropWidth * cropHeight)
        for (y in 0 until cropHeight) {
            val srcOffset = (cropTop + y) * inputWidth + cropLeft
            val dstOffset = y * cropWidth
            for (x in 0 until cropWidth) {
                cropped[dstOffset + x] = mask[srcOffset + x]
            }
        }

        val resized = resizeFloatGrid(
            source = cropped,
            sourceWidth = cropWidth,
            sourceHeight = cropHeight,
            targetWidth = info.originalWidth,
            targetHeight = info.originalHeight
        )

        return BooleanArray(resized.size) { index -> resized[index] >= threshold }
    }

    private fun resizeFloatGrid(
        source: FloatArray,
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int
    ): FloatArray {
        if (sourceWidth == targetWidth && sourceHeight == targetHeight) {
            return source.copyOf()
        }

        val result = FloatArray(targetWidth * targetHeight)
        val xScale = if (targetWidth > 1) (sourceWidth - 1).toFloat() / (targetWidth - 1) else 0f
        val yScale = if (targetHeight > 1) (sourceHeight - 1).toFloat() / (targetHeight - 1) else 0f

        for (y in 0 until targetHeight) {
            val srcY = y * yScale
            val y0 = floor(srcY).toInt().coerceIn(0, sourceHeight - 1)
            val y1 = minOf(y0 + 1, sourceHeight - 1)
            val yWeight = srcY - y0

            for (x in 0 until targetWidth) {
                val srcX = x * xScale
                val x0 = floor(srcX).toInt().coerceIn(0, sourceWidth - 1)
                val x1 = minOf(x0 + 1, sourceWidth - 1)
                val xWeight = srcX - x0

                val topLeft = source[y0 * sourceWidth + x0]
                val topRight = source[y0 * sourceWidth + x1]
                val bottomLeft = source[y1 * sourceWidth + x0]
                val bottomRight = source[y1 * sourceWidth + x1]

                val top = topLeft + (topRight - topLeft) * xWeight
                val bottom = bottomLeft + (bottomRight - bottomLeft) * xWeight
                result[y * targetWidth + x] = top + (bottom - top) * yWeight
            }
        }

        return result
    }

    private fun buildNormalizedPolygon(
        mask: BooleanArray,
        maskWidth: Int,
        maskHeight: Int,
        fallbackBox: RawDetection
    ): List<NormalizedPoint> {
        val polygon = traceLargestBoundaryPolygon(mask, maskWidth, maskHeight, fallbackBox)
            .takeIf { it.size >= 3 }
            ?: fallbackPolygonFromBox(fallbackBox)

        return polygon.map { point ->
            NormalizedPoint(
                x = (point.x.toFloat() / maskWidth).coerceIn(0f, 1f),
                y = (point.y.toFloat() / maskHeight).coerceIn(0f, 1f)
            )
        }
    }

    private fun traceLargestBoundaryPolygon(
        mask: BooleanArray,
        maskWidth: Int,
        maskHeight: Int,
        fallbackBox: RawDetection
    ): List<IntPoint> {
        val boxX1 = floor(fallbackBox.x1).toInt().coerceIn(0, maskWidth.saturatingSub(1))
        val boxY1 = floor(fallbackBox.y1).toInt().coerceIn(0, maskHeight.saturatingSub(1))
        val boxX2 = ceil(fallbackBox.x2).toInt().coerceIn(0, maskWidth)
        val boxY2 = ceil(fallbackBox.y2).toInt().coerceIn(0, maskHeight)
        if (boxX2 <= boxX1 || boxY2 <= boxY1) return emptyList()
        val componentMask = extractLargestComponentMask(
            mask = mask,
            maskWidth = maskWidth,
            maskHeight = maskHeight,
            boxX1 = boxX1,
            boxY1 = boxY1,
            boxX2 = boxX2,
            boxY2 = boxY2
        ) ?: return emptyList()

        val contour = traceBoundaryContour(
            mask = componentMask.mask,
            width = componentMask.width,
            height = componentMask.height
        ).map { point ->
            IntPoint(
                x = point.x + componentMask.offsetX,
                y = point.y + componentMask.offsetY
            )
        }

        val simplified = simplifyBoundaryLoop(contour)
        return simplified.takeIf { it.size >= 3 } ?: emptyList()
    }

    private fun extractLargestComponentMask(
        mask: BooleanArray,
        maskWidth: Int,
        maskHeight: Int,
        boxX1: Int,
        boxY1: Int,
        boxX2: Int,
        boxY2: Int
    ): ComponentMask? {
        val width = boxX2 - boxX1
        val height = boxY2 - boxY1
        if (width <= 0 || height <= 0) return null

        val cropped = BooleanArray(width * height)
        for (y in 0 until height) {
            val srcOffset = (boxY1 + y) * maskWidth + boxX1
            val dstOffset = y * width
            for (x in 0 until width) {
                cropped[dstOffset + x] = mask[srcOffset + x]
            }
        }

        val visited = BooleanArray(cropped.size)
        val neighborOffsets = arrayOf(
            IntPoint(-1, -1), IntPoint(0, -1), IntPoint(1, -1),
            IntPoint(-1, 0), IntPoint(1, 0),
            IntPoint(-1, 1), IntPoint(0, 1), IntPoint(1, 1)
        )

        var bestComponent = emptyList<Int>()
        for (index in cropped.indices) {
            if (!cropped[index] || visited[index]) continue

            val queue = ArrayDeque<Int>()
            val component = mutableListOf<Int>()
            queue.addLast(index)
            visited[index] = true

            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                component += current

                val x = current % width
                val y = current / width
                neighborOffsets.forEach { offset ->
                    val nx = x + offset.x
                    val ny = y + offset.y
                    if (nx !in 0 until width || ny !in 0 until height) return@forEach

                    val nextIndex = ny * width + nx
                    if (!cropped[nextIndex] || visited[nextIndex]) return@forEach
                    visited[nextIndex] = true
                    queue.addLast(nextIndex)
                }
            }

            if (component.size > bestComponent.size) {
                bestComponent = component
            }
        }

        if (bestComponent.isEmpty()) return null

        val componentMask = BooleanArray(cropped.size)
        bestComponent.forEach { index -> componentMask[index] = true }
        return ComponentMask(
            mask = componentMask,
            width = width,
            height = height,
            offsetX = boxX1,
            offsetY = boxY1
        )
    }

    private fun traceBoundaryContour(
        mask: BooleanArray,
        width: Int,
        height: Int
    ): List<IntPoint> {
        val directions = arrayOf(
            IntPoint(-1, -1),
            IntPoint(0, -1),
            IntPoint(1, -1),
            IntPoint(1, 0),
            IntPoint(1, 1),
            IntPoint(0, 1),
            IntPoint(-1, 1),
            IntPoint(-1, 0)
        )
        val start = findBoundaryStart(mask, width, height) ?: return emptyList()
        val startBacktrack = IntPoint(start.x - 1, start.y)
        val contour = mutableListOf<IntPoint>()
        var current = start
        var backtrack = startBacktrack
        var guard = 0
        val maxSteps = (width * height * 4).coerceAtLeast(16)

        do {
            contour += current

            val backtrackIndex = directionIndex(
                from = current,
                to = backtrack,
                directions = directions
            )

            var nextPoint: IntPoint? = null
            var nextDirectionIndex = -1
            for (offset in 1..directions.size) {
                val directionIndex = (backtrackIndex + offset) % directions.size
                val direction = directions[directionIndex]
                val candidate = IntPoint(
                    x = current.x + direction.x,
                    y = current.y + direction.y
                )
                if (isMaskFilled(mask, width, height, candidate.x, candidate.y)) {
                    nextPoint = candidate
                    nextDirectionIndex = directionIndex
                    break
                }
            }

            if (nextPoint == null) break

            val previousDirection = directions[(nextDirectionIndex + directions.size - 1) % directions.size]
            backtrack = IntPoint(
                x = current.x + previousDirection.x,
                y = current.y + previousDirection.y
            )
            current = nextPoint
            guard++
        } while (
            guard <= maxSteps &&
            !(current == start && backtrack == startBacktrack)
        )

        return contour
    }

    private fun findBoundaryStart(
        mask: BooleanArray,
        width: Int,
        height: Int
    ): IntPoint? {
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (isBoundaryPixel(mask, width, height, x, y)) {
                    return IntPoint(x, y)
                }
            }
        }
        return null
    }

    private fun isBoundaryPixel(
        mask: BooleanArray,
        width: Int,
        height: Int,
        x: Int,
        y: Int
    ): Boolean {
        if (!isMaskFilled(mask, width, height, x, y)) return false

        for (ny in y - 1..y + 1) {
            for (nx in x - 1..x + 1) {
                if (nx == x && ny == y) continue
                if (!isMaskFilled(mask, width, height, nx, ny)) {
                    return true
                }
            }
        }
        return false
    }

    private fun isMaskFilled(
        mask: BooleanArray,
        width: Int,
        height: Int,
        x: Int,
        y: Int
    ): Boolean {
        if (x !in 0 until width || y !in 0 until height) return false
        return mask[y * width + x]
    }

    private fun directionIndex(
        from: IntPoint,
        to: IntPoint,
        directions: Array<IntPoint>
    ): Int {
        val dx = (to.x - from.x).coerceIn(-1, 1)
        val dy = (to.y - from.y).coerceIn(-1, 1)
        return directions.indexOfFirst { it.x == dx && it.y == dy }
            .takeIf { it >= 0 }
            ?: directions.lastIndex
    }

    private fun simplifyBoundaryLoop(points: List<IntPoint>): List<IntPoint> {
        if (points.size < 3) return emptyList()

        val deduped = mutableListOf<IntPoint>()
        points.forEach { point ->
            if (deduped.lastOrNull() != point) deduped += point
        }
        if (deduped.firstOrNull() == deduped.lastOrNull()) {
            deduped.removeAt(deduped.lastIndex)
        }

        if (deduped.size < 3) return emptyList()

        var simplified = deduped.toList()
        var changed: Boolean
        do {
            changed = false
            if (simplified.size < 3) break

            val nextLoop = mutableListOf<IntPoint>()
            for (index in simplified.indices) {
                val prev = simplified[(index - 1 + simplified.size) % simplified.size]
                val current = simplified[index]
                val next = simplified[(index + 1) % simplified.size]

                if (isCollinear(prev, current, next)) {
                    changed = true
                    continue
                }
                nextLoop += current
            }
            simplified = nextLoop
        } while (changed && simplified.size >= 3)

        return limitPolygonPoints(simplified, MAX_SEGMENTATION_POLYGON_POINTS)
    }

    private fun isCollinear(a: IntPoint, b: IntPoint, c: IntPoint): Boolean {
        val abx = b.x - a.x
        val aby = b.y - a.y
        val bcx = c.x - b.x
        val bcy = c.y - b.y
        return (abx * bcy) - (aby * bcx) == 0
    }

    private fun limitPolygonPoints(points: List<IntPoint>, maxPoints: Int): List<IntPoint> {
        if (points.size <= maxPoints) return points

        val simplified = mutableListOf<IntPoint>()
        for (index in 0 until maxPoints) {
            val sourceIndex = floor(index * points.size / maxPoints.toFloat()).toInt()
                .coerceIn(0, points.lastIndex)
            val point = points[sourceIndex]
            if (simplified.lastOrNull() != point) {
                simplified += point
            }
        }

        if (simplified.size >= 3) return simplified
        return points.take(maxPoints)
    }

    private fun polygonAreaAbs(points: List<IntPoint>): Float {
        if (points.size < 3) return 0f

        var total = 0.0
        for (index in points.indices) {
            val current = points[index]
            val next = points[(index + 1) % points.size]
            total += current.x.toDouble() * next.y.toDouble()
            total -= next.x.toDouble() * current.y.toDouble()
        }
        return abs((total / 2.0).toFloat())
    }

    private fun fallbackPolygonFromBox(box: RawDetection): List<IntPoint> {
        val x1 = floor(box.x1).toInt()
        val y1 = floor(box.y1).toInt()
        val x2 = ceil(box.x2).toInt().coerceAtLeast(x1 + 1)
        val y2 = ceil(box.y2).toInt().coerceAtLeast(y1 + 1)

        return listOf(
            IntPoint(x1, y1),
            IntPoint(x2, y1),
            IntPoint(x2, y2),
            IntPoint(x1, y2)
        )
    }

    private fun sigmoid(value: Float): Float {
        val clipped = value.coerceIn(-50f, 50f)
        return (1.0 / (1.0 + kotlin.math.exp((-clipped).toDouble()))).toFloat()
    }

    private fun squeezeShape(shape: IntArray): IntArray {
        val squeezed = shape.filter { it != 1 }
        return if (squeezed.isEmpty()) intArrayOf(1) else squeezed.toIntArray()
    }

    private fun Int.saturatingSub(other: Int): Int = (this - other).coerceAtLeast(0)
}
