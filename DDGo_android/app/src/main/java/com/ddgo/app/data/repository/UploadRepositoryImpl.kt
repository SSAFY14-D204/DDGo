package com.ddgo.app.data.repository

import com.ddgo.app.data.remote.upload.UploadApi
import com.ddgo.app.domain.model.AttemptReport
import com.ddgo.app.domain.repository.UploadRepository
import javax.inject.Inject

/**
 * UploadRepository 인터페이스(domain)의 실제 구현체.
 *
 * 실제 업로드는 VideoAnalyzeWorker(data/work)에서 수행합니다.
 * 이 Repository는 업로드 상태를 조회하거나 직접 업로드할 때 사용합니다.
 */
class UploadRepositoryImpl @Inject constructor(
    private val uploadApi: UploadApi
) : UploadRepository {

    override suspend fun uploadVideoDirectly(
        videoUri: String,
        grade: String
    ): Result<AttemptReport> {
        // TODO: 실제 업로드 구현
        // 1. Uri → File 변환
        // 2. MultipartBody 생성
        // 3. uploadApi.uploadVideo() 호출
        // 4. UploadMapper.toAttemptReport()로 변환
        return Result.failure(NotImplementedError("VideoAnalyzeWorker를 통한 업로드를 권장합니다"))
    }
}
