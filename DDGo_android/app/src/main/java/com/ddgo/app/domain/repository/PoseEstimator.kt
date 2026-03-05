package com.ddgo.app.domain.repository

import com.ddgo.app.domain.model.Pose

/**
 * 포즈 추정 AI 계약서.
 *
 * 아키텍처의 핵심 통찰: AI를 "또 하나의 데이터 소스"로 취급합니다.
 * - 구현체는 data/ml/mediapipe/PoseEstimatorImpl에 있습니다.
 * - domain 계층은 MediaPipe가 있는지조차 모릅니다.
 */
interface PoseEstimator {
    /**
     * 비디오 URI에서 각 프레임의 포즈를 추출합니다.
     * @param videoUri 로컬 비디오 파일 URI
     * @return 프레임별 Pose 리스트 (시간순 정렬)
     */
    suspend fun estimateFromVideo(videoUri: String): List<Pose>
}
