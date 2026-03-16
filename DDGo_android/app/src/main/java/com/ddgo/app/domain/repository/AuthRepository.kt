package com.ddgo.app.domain.repository

import com.ddgo.app.domain.model.AuthToken
import com.ddgo.app.domain.model.User

/**
 * 인증 관련 비즈니스 규칙 계약서.
 *
 * domain 계층에 있으므로 Android 프레임워크를 import 해서는 안 됩니다.
 * 구현체는 data/repository/AuthRepositoryImpl에 있습니다.
 */
interface AuthRepository {
    suspend fun register(username: String ,password:String, nickname:String) : Result<Unit>
    suspend fun login(username : String, password: String) : Result<AuthToken>
    suspend fun refreshToken(refreshToken: String): Result<AuthToken>
    suspend fun logout(): Result<Unit>
    suspend fun getMyInfo(): Result<User>
    suspend fun deleteMe(): Result<Unit>
}
