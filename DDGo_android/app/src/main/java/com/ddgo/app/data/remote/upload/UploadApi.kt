package com.ddgo.app.data.remote.upload

import com.ddgo.app.data.remote.common.ApiResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * 영상 업로드 관련 API 엔드포인트.
 * Multipart form data를 사용해 영상 파일을 전송합니다.
 */
interface UploadApi {

    @Multipart
    @POST("climbs/upload")
    suspend fun uploadVideo(
        @Part video: MultipartBody.Part,
        @Part("metadata") metadata: RequestBody
    ): ApiResponse<UploadResponseDto>
}
