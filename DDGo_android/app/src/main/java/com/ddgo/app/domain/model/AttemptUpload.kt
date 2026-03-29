package com.ddgo.app.domain.model

/** presigned 업로드 티켓 domain 모델입니다. */
data class AttemptUploadTicket(
    val attemptId: Long,
    val uploadUrl: String,
    val objectKey: String
)

/**
 * 업로드가 완료된 시도 영상 결과 domain 모델입니다.
 *
 * 역할:
 * - 하나의 챌린지에 대해 시작되고 업로드까지 완료된 시도 1건을 나타냅니다.
 */
data class UploadedAttemptVideo(
    val challengeId: Long,
    val attemptId: Long,
    val attemptNo: Int,
    val videoUri: String,
    val objectKey: String
)
