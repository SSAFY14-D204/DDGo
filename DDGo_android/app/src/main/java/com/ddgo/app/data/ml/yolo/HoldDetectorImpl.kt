package com.ddgo.app.data.ml.yolo

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.ddgo.app.data.mapper.VisionMapper
import com.ddgo.app.data.ml.common.TFLiteInferenceUtils
import com.ddgo.app.domain.model.Hold
import com.ddgo.app.domain.repository.HoldDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import wseemann.media.FFmpegMediaMetadataRetriever
import javax.inject.Inject

/**
 * YOLO/TFLite를 사용한 HoldDetector 구현체.
 *
 * 프레임(Bitmap) 하나를 받아서 홀드를 탐지합니다.
 */
class HoldDetectorImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : HoldDetector {

    companion object {
        private const val TAG = "HoldDetectorImpl"
        private const val HOLD_MODEL_PATH = "models/best_float32_v8_640.tflite"
        private const val HOLD_SIZE       = 640
        private const val CONF_THRESHOLD  = 0.25f
        private const val IOU_THRESHOLD   = 0.45f
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
            val (holdDetections, _) = TFLiteInferenceUtils.runInference(
                bitmap              = bitmap,
                interpreter         = holdInterp,
                modelSize           = HOLD_SIZE,
                confidenceThreshold = CONF_THRESHOLD,
                iouThreshold        = IOU_THRESHOLD
            )

            Log.d(TAG, "✅ 감지된 홀드 수: ${holdDetections.size}")

            holdDetections.map { d ->
                VisionMapper.toHold(
                    left       = d[0],
                    top        = d[1],
                    right      = d[2],
                    bottom     = d[3],
                    confidence = d[4]
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
