package com.ddgo.app.domain.repository

import com.ddgo.app.domain.model.AttemptReport

/**
 * 업로드 관련 계약서.
 * 구현체는 data/repository/UploadRepositoryImpl에 있습니다.
 */
interface UploadRepository {
    suspend fun uploadVideoDirectly(videoUri: String, grade: String): Result<AttemptReport>
}
