package com.ddgo.app.domain.model

/**
 * 특정 프레임의 포즈 도메인 모델.
 * MediaPipe 결과를 VisionMapper를 통해 변환한 순수 Kotlin 모델입니다.
 *
 * @param frameTimeMs 이 포즈가 캡처된 비디오 타임스탬프 (밀리초)
 * @param landmarks 33개(MediaPipe 기준) 신체 부위 좌표 리스트
 */
data class Pose(
    val frameTimeMs: Long,
    val landmarks: List<PoseLandmark>,
    val landmarksPx: Map<String, PosePixelPoint> = emptyMap(),
    val worldLandmarksSample: Map<String, PoseWorldPoint> = emptyMap()
)

/**
 * 단일 신체 부위(랜드마크) 좌표.
 *
 * @param index MediaPipe 랜드마크 인덱스 (0=코, 11=왼어깨, 23=왼엉덩이 ...)
 * @param x, y, z 정규화된 좌표 (화면 너비/높이 기준 0~1)
 */
data class PoseLandmark(
    val index: Int,
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float? = null,
    val presence: Float? = null
)

/**
 * JSON 전송 및 후속 분석에 사용하는 2D 픽셀 좌표입니다.
 */
data class PosePixelPoint(
    val x: Float,
    val y: Float
)

/**
 * MediaPipe world landmark에서 추출한 3D 좌표 샘플입니다.
 */
data class PoseWorldPoint(
    val x: Float,
    val y: Float,
    val z: Float
)
