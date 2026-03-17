package com.ddgo.app.domain.model

/**
 * 클라이밍 홀드 도메인 모델.
 * YOLO 검출 결과를 VisionMapper를 통해 변환한 순수 Kotlin 모델입니다.
 *
 * @param boundingBox 이미지 내 홀드 위치 (정규화 좌표 0~1)
 * @param confidence  검출 신뢰도 (0~1)
 * @param polygon     세그멘테이션 경계점 목록 (정규화 좌표 0~1)
 * @param colorLabel  HSV 후처리로 분류된 색상 이름 (기본값 "unknown")
 * @param colorScore  색상 분류 신뢰도 (0~1, 기본값 0)
 */
data class Hold(
    val boundingBox: BoundingBox,
    val confidence: Float,
    val polygon: List<Point> = emptyList(),
    val colorLabel: String = "unknown",
    val colorScore: Float = 0f
) {
    data class BoundingBox(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )

    data class Point(
        val x: Float,
        val y: Float
    )
}
