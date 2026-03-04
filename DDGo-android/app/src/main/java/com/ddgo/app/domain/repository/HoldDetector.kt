package com.ddgo.app.domain.repository

import com.ddgo.app.domain.model.Hold

/**
 * 홀드 감지 AI 계약서.
 *
 * 구현체는 data/ml/yolo/HoldDetectorImpl에 있습니다.
 * domain 계층은 YOLO, TFLite의 존재를 모릅니다.
 */
interface HoldDetector {
    /**
     * 비디오에서 클라이밍 홀드를 검출합니다.
     * @param videoUri 로컬 비디오 파일 URI
     * @return 검출된 Hold 리스트
     */
    suspend fun detectFromVideo(videoUri: String): List<Hold>
}
