package com.ddgo.app.core.network

import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * JWT payload의 exp 값을 읽어 만료 여부를 판단하는 유틸리티입니다.
 *
 * 서명 검증은 서버 책임으로 두고,
 * 클라이언트에서는 앱 시작 시 빠른 만료 판별에만 사용합니다.
 */
object JwtTokenParser {

    private val json = Json { ignoreUnknownKeys = true }

    /** JWT에서 exp(epoch seconds)를 읽습니다. 읽지 못하면 null을 반환합니다. */
    fun parseExpirationEpochSeconds(token: String): Long? {
        return runCatching {
            val parts = token.split(".")
            if (parts.size < 2) return null

            val normalizedPayload = parts[1]
                .replace('-', '+')
                .replace('_', '/')
                .let { payload -> payload + "=".repeat((4 - payload.length % 4) % 4) }

            val decodedPayload = String(Base64.getDecoder().decode(normalizedPayload))
            json.parseToJsonElement(decodedPayload)
                .jsonObject["exp"]
                ?.jsonPrimitive
                ?.longOrNull
        }.getOrNull()
    }

    /** 현재 시각 기준으로 토큰이 이미 만료되었는지 반환합니다. */
    fun isExpired(token: String, nowEpochSeconds: Long = System.currentTimeMillis() / 1000): Boolean {
        val expirationEpochSeconds = parseExpirationEpochSeconds(token) ?: return true
        return expirationEpochSeconds <= nowEpochSeconds
    }
}
