package com.ddgo.app.data.remote.common

import kotlinx.serialization.Serializable

/**
 * 서버 API 응답의 공통 래퍼.
 *
 * 서버가 다음과 같은 형태로 응답할 때 사용합니다:
 * {
 *   "success": true,
 *   "message": "요청 성공",
 *   "data": { ... }
 * }
 *
 * @param T 실제 데이터 타입 (제네릭)
 */
@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val message: String = "",
    val data: T? = null
)
