package com.ddgo.app.data.ml.yolo

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.ddgo.app.data.mapper.VisionMapper
import com.ddgo.app.data.ml.common.TFLiteInferenceUtils
import com.ddgo.app.domain.model.Hold
import com.ddgo.app.domain.repository.HoldDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * YOLO 세그멘테이션 TFLite를 사용한 HoldDetector 구현체.
 *
 * 프레임(Bitmap) 하나를 받아서 홀드를 탐지합니다.
 */
class HoldDetectorImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : HoldDetector {

    companion object {
        private const val TAG = "HoldDetectorImpl"
        private const val HOLD_MODEL_PATH = "models/hold_detect_seg_v11n_640_float32.tflite"
        private const val HOLD_SIZE = 640
        private const val NUM_CLASSES = 2
        private const val CONF_THRESHOLD = 0.18f
        private const val IOU_THRESHOLD = 0.45f
        private const val MAX_DETECTIONS = 300

        // one_frame_seg_draw.py와 동일하게 hold(class 0)만 유지합니다.
        private val TARGET_CLASS_INDICES = setOf(0)
    }

    override suspend fun detectFromFrame(bitmap: Bitmap): List<Hold> {
        Log.d(TAG, "▶ detectFromFrame 시작 (size: ${bitmap.width}x${bitmap.height})")

        val holdInterp = try {
            TFLiteInferenceUtils.createInterpreter(context, HOLD_MODEL_PATH)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 모델 로드 실패", e)
            return emptyList()
        }

        return try {
            val holdDetections = TFLiteInferenceUtils.runSegmentationInference(
                bitmap              = bitmap,
                interpreter         = holdInterp,
                modelSize           = HOLD_SIZE,
                numClasses          = NUM_CLASSES,
                confidenceThreshold = CONF_THRESHOLD,
                iouThreshold        = IOU_THRESHOLD,
                targetClassIndices  = TARGET_CLASS_INDICES,
                maxDetections       = MAX_DETECTIONS
            )

            Log.d(TAG, "✅ 감지된 홀드 수: ${holdDetections.size}")

            holdDetections.map { d ->
                VisionMapper.toHold(
                    left = d.left,
                    top = d.top,
                    right = d.right,
                    bottom = d.bottom,
                    confidence = d.confidence,
                    polygon = d.polygon.map { point ->
                        Hold.Point(point.x, point.y)
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 홀드 탐지 중 예외 발생", e)
            emptyList()
        } finally {
            holdInterp.close()
        }
    }
}
