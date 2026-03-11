package com.ddgo.app.domain.repository

import android.graphics.Bitmap
import com.ddgo.app.domain.model.Hold

/**
 * 홀드 감지 AI 계약서.
 *
 * 구현체는 data/ml/yolo/HoldDetectorImpl에 있습니다.
 * domain 계층은 YOLO, TFLite의 존재를 모릅니다.
 */
interface HoldDetector {
    /**
     * 이미지(비트맵)에서 클라이밍 홀드를 검출합니다.
     * @param bitmap 감지 대상 이미지 프레임
     * @return 검출된 Hold 리스트
     */
    suspend fun detectFromFrame(bitmap: Bitmap): List<Hold>
}
