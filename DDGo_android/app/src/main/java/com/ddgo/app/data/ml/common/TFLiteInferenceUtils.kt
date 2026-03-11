package com.ddgo.app.data.ml.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * TFLite 추론 공통 유틸리티 (one_frame_draw.py + find_best_frame.py 공통 로직).
 *
 * 이 파일은 domain 계층을 import 하지 않습니다.
 * PersonDetectorImpl과 HoldDetectorImpl 양쪽에서 공유합니다.
 *
 * 파이프라인:
 * letterbox → normalizeToByteBuffer → createInterpreter + run → parseOutputTensor
 *   → filterByConfidence → applyNms → scaleToNormalized
 *
 * 또는 runInference()로 전체 파이프라인을 한 번에 실행합니다.
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
}
