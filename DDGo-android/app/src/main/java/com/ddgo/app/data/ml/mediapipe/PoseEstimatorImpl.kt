package com.ddgo.app.data.ml.mediapipe

import android.content.Context
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.repository.PoseEstimator
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * MediaPipe Tasks를 사용한 PoseEstimator 구현체.
 *
 * 이 클래스는 안드로이드(Context, Bitmap)에 종속됩니다.
 * domain 계층은 PoseEstimator 인터페이스만 알고, 이 구현체는 모릅니다.
 *
 * ──────────────────────────────────────────────────
 * 실제 구현 시 작업 목록:
 * 1. assets/ 폴더에 MediaPipe .task 모델 파일을 추가합니다.
 * 2. PoseLandmarkerOptions를 설정합니다.
 * 3. 비디오 URI → Bitmap 변환 후 추론을 실행합니다.
 * 4. NormalizedLandmark 결과를 VisionMapper를 통해 Pose로 변환합니다.
 * ──────────────────────────────────────────────────
 */
class PoseEstimatorImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PoseEstimator {

    override suspend fun estimateFromVideo(videoUri: String): List<Pose> {
        // TODO: MediaPipe PoseLandmarker 실제 구현
        // 1. PoseLandmarker 초기화
        // 2. 비디오 프레임별 추론
        // 3. VisionMapper.toPose(result)로 변환 후 반환
        return emptyList()
    }
}
