package com.ddgo.app.domain.model

/**
 * 한 번의 클라이밍 시도(Attempt) 분석 결과 리포트 도메인 모델.
 *
 * @param climbId 서버에서 관리하는 클라이밍 ID
 * @param videoUrl 업로드된 영상 URL
 * @param success 완등 성공 여부
 * @param failTimeMs 실패 시점 타임스탬프 (ms). success=true면 null
 * @param holdCount 잡은 홀드 수
 * @param poses 비디오 전체 프레임에서 추출된 포즈 시퀀스
 * @param uploadedAt ISO-8601 형식 업로드 시각
 */
data class AttemptReport(
    val climbId: String,
    val videoUrl: String,
    val uploadedAt: String,
    val success: Boolean,
    val failTimeMs: Long?,
    val holdCount: Int,
    val poses: List<Pose>
)
