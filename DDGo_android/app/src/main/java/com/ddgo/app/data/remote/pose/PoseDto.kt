package com.ddgo.app.data.remote.pose

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * MediaPipe pose 시퀀스를 JSON으로 주고받기 쉬운 형태로 감싼 DTO입니다.
 *
 * 규칙:
 * - 최상위 객체는 poses 배열을 가집니다.
 * - 각 pose는 프레임 시간과 이름 기반 2D/3D 랜드마크 맵을 가집니다.
 * - 서버 스펙이 정해지면 @SerialName만 조정하면 됩니다.
 */
@Serializable
data class PoseSequenceDto(
    val poses: List<PoseDto>
)

@Serializable
data class PoseDto(
    @SerialName("frame_time_ms")
    val frameTimeMs: Long,
    @SerialName("landmarks_px")
    val landmarksPx: Map<String, Point2dDto>,
    @SerialName("world_landmarks_sample")
    val worldLandmarksSample: Map<String, Point3dDto> = emptyMap()
) {
    @Serializable
    data class Point2dDto(
        val x: Float,
        val y: Float
    )

    @Serializable
    data class Point3dDto(
        val x: Float,
        val y: Float,
        val z: Float
    )
}
