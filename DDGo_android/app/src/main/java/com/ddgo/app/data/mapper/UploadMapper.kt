package com.ddgo.app.data.mapper

import com.ddgo.app.data.remote.upload.UploadResponseDto
import com.ddgo.app.domain.model.AttemptReport

/**
 * Upload 관련 DTO → Domain Model 변환 매퍼.
 */
object UploadMapper {

    /** 업로드 응답 DTO → AttemptReport 도메인 모델로 변환 */
    fun UploadResponseDto.toAttemptReport(): AttemptReport = AttemptReport(
        climbId = climbId,
        videoUrl = videoUrl,
        uploadedAt = uploadedAt,
        // 업로드 직후에는 분석 결과가 없으므로 기본값
        success = false,
        failTimeMs = null,
        holdCount = 0,
        poses = emptyList()
    )
}
