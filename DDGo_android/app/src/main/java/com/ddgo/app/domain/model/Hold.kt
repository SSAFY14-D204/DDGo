package com.ddgo.app.domain.model

/**
 * 클라이밍 홀드 도메인 모델.
 * YOLO 검출 결과를 VisionMapper를 통해 변환한 순수 Kotlin 모델입니다.
 *
 * @param boundingBox 이미지 내 홀드 위치 (정규화 좌표 0~1)
 * @param confidence 검출 신뢰도 (0~1)
 */
data class Hold(
    val boundingBox: BoundingBox,
    val confidence: Float
) {
    data class BoundingBox(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )
}
