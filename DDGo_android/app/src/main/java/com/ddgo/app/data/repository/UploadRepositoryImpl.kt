package com.ddgo.app.data.repository

import com.ddgo.app.data.remote.upload.UploadApi
import com.ddgo.app.domain.model.AttemptReport
import com.ddgo.app.domain.repository.UploadRepository
import javax.inject.Inject

/**
 * UploadRepository 인터페이스(domain)의 실제 구현체.
 *
 * 현재 제품 업로드 플로우는 AttemptRepository/UseCase 경로를 통해 처리됩니다.
 * 이 Repository의 direct upload 경로는 아직 구현되지 않은 레거시 진입점입니다.
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
        return Result.failure(NotImplementedError("직접 업로드 경로는 아직 구현되지 않았습니다."))
    }
}
