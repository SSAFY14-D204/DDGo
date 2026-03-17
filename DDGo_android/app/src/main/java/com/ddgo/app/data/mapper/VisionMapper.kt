package com.ddgo.app.data.mapper

import com.ddgo.app.domain.model.Hold
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.model.PoseLandmark

/**
 * AI 모델 출력(Raw 좌표) → Domain Model 변환 매퍼.
 *
 * AI 핵심 원칙:
 * - MediaPipe, TFLite가 반환하는 데이터는 안드로이드/구글 라이브러리에 종속된 "더러운" 데이터입니다.
 * - VisionMapper가 이를 순수 Kotlin 데이터 클래스(Pose, Hold)로 "세탁"합니다.
 * - domain 계층은 절대 NormalizedLandmark 같은 외부 라이브러리 타입을 알아서는 안 됩니다.
 */
object VisionMapper {

    /**
     * MediaPipe NormalizedLandmark 리스트 → Pose 도메인 모델로 변환.
     *
     * 실제 구현 시: results.landmarks()[0]을 전달하세요.
     * @param frameTimeMs 해당 프레임의 타임스탬프 (ms)
     * @param rawLandmarks 정규화된 랜드마크 좌표 리스트 (x, y, z 각 0~1 범위)
     */
    fun toPose(
        frameTimeMs: Long,
        rawLandmarks: List<Triple<Float, Float, Float>>
    ): Pose = Pose(
        frameTimeMs = frameTimeMs,
        landmarks = rawLandmarks.mapIndexed { index, (x, y, z) ->
            PoseLandmark(
                index = index,
                x = x,
                y = y,
                z = z
            )
        }
    )

    /**
     * YOLO 바운딩박스 결과 → Hold 도메인 모델로 변환.
     *
     * @param left, top, right, bottom: 이미지 기준 정규화 좌표 (0~1)
     * @param confidence: 신뢰도 점수 (0~1)
     */
    fun toHold(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        confidence: Float,
        polygon: List<Hold.Point> = emptyList()
    ): Hold = Hold(
        boundingBox = Hold.BoundingBox(left, top, right, bottom),
        confidence = confidence,
        polygon = polygon
    )
}
