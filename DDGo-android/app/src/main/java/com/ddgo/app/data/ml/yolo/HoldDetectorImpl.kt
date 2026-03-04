package com.ddgo.app.data.ml.yolo

import android.content.Context
import com.ddgo.app.domain.model.Hold
import com.ddgo.app.domain.repository.HoldDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * YOLO/TFLite를 사용한 HoldDetector 구현체.
 *
 * 클라이밍 홀드 객체 검출 모델입니다.
 * domain 계층은 HoldDetector 인터페이스만 알고, 이 구현체는 모릅니다.
 *
 * ──────────────────────────────────────────────────
 * 실제 구현 시 작업 목록:
 * 1. assets/ 폴더에 YOLO .tflite 모델 파일을 추가합니다.
 * 2. TFLite Interpreter를 초기화합니다 (GPU Delegate 선택적 적용).
 * 3. 비디오 URI → Bitmap 변환 후 추론 실행합니다.
 * 4. 바운딩박스 결과를 VisionMapper를 통해 Hold로 변환합니다.
 * ──────────────────────────────────────────────────
 */
class HoldDetectorImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : HoldDetector {

    override suspend fun detectFromVideo(videoUri: String): List<Hold> {
        // TODO: TFLite YOLO 실제 구현
        return emptyList()
    }
}
