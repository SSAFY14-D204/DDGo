package com.ddgo.app.domain.repository

import android.graphics.Bitmap
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.model.PoseLandmark

/**
 * 포즈 추정 AI 계약서.
 *
 * 아키텍처의 핵심 통찰: AI를 "또 하나의 데이터 소스"로 취급합니다.
 * - 구현체는 data/ml/mediapipe/PoseEstimatorImpl에 있습니다.
 * - domain 계층은 MediaPipe가 있는지조차 모릅니다.
 */
interface PoseEstimator {

    /**
     * 비디오 URI에서 전체 프레임의 포즈를 일괄 추출합니다.
     * 결과 화면 진입 시 백그라운드에서 한 번 실행됩니다.
     *
     * @param videoUri 로컬 비디오 파일 URI (file:// 권장)
     * @return 프레임별 Pose 리스트 (시간순 정렬)
     */
    suspend fun estimateFromVideo(videoUri: String): List<Pose>

    /**
     * 단일 Bitmap 프레임에서 포즈 랜드마크를 실시간 추출합니다.
     * AttemptResultScreen에서 ExoPlayer TextureView 캡처 프레임을 넘겨
     * 실시간 스켈레톤 오버레이에 사용합니다.
     *
     * @param bitmap TextureView.getBitmap()으로 캡처한 비디오 프레임
     * @return 33개 MediaPipe 랜드마크 리스트 (빈 리스트 = 포즈 미감지)
     */
    suspend fun estimateFromFrame(bitmap: Bitmap): List<PoseLandmark>
}
