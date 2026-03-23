package com.ddgo.app.feature.auth

import com.kakao.sdk.user.UserApiClient
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

internal data class KakaoProfile(
    val nickname: String?
)

internal suspend fun loadKakaoProfile(): Result<KakaoProfile> {
    return suspendCancellableCoroutine { continuation ->
        UserApiClient.instance.me { user, error ->
            when {
                error != null -> continuation.resume(Result.failure(error))
                user != null -> {
                    continuation.resume(
                        Result.success(
                            KakaoProfile(
                                nickname = user.kakaoAccount?.profile?.nickname
                                    ?.trim()
                                    ?.takeUnless { it.isBlank() }
                            )
                        )
                    )
                }

                else -> continuation.resume(Result.failure(IllegalStateException("카카오 프로필이 없습니다.")))
            }
        }
    }
}
