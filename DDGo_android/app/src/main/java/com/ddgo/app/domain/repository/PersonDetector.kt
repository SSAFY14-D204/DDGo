package com.ddgo.app.domain.repository

/**
 * 인물 감지 AI 계약서 (find_best_frame.py 대응).
 *
 * 비디오에서 "사람이 가장 적은 프레임"의 타임스탬프를 반환합니다.
 * 반환된 타임스탬프는 HoldDetector가 홀드 감지를 수행할 최적 프레임을 지정하는 데 사용됩니다.
 *
 * 아키텍처의 핵심 통찰: AI를 "또 하나의 데이터 소스"로 취급합니다.
 * - 구현체는 data/ml/persondetect/PersonDetectorImpl에 있습니다.
 * - domain 계층은 TFLite, MediaMetadataRetriever의 존재를 모릅니다.
 */
interface PersonDetector {
    /**
     * 비디오에서 홀드 탐지에 최적인 "최선 프레임"의 타임스탬프를 반환합니다.
     *
     * 선택 기준 (우선순위 순):
     * 1. 감지된 사람 수 최소
     * 2. 감지 신뢰도 합계 최소 (동률 시)
     * 3. 가장 이른 프레임 (동률 시)
     *
     * @param videoUri 로컬 비디오 파일 URI (content:// 또는 file://)
     * @return 최선 프레임의 타임스탬프 (마이크로초, MediaMetadataRetriever.getFrameAtTime 용)
     *         비디오를 읽을 수 없거나 프레임이 없을 경우 0L 반환
     */
    suspend fun findBestFrameTime(videoUri: String): Long
}
