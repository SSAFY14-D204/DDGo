package com.ddgo.app.feature.profile.model

/**
 * 신체 정보 저장 직후 잠시 유지할 로컬 스냅샷입니다.
 *
 * 역할:
 * - `/me` 재조회가 잠시 실패하더라도 방금 저장한 신체 정보가 사라지지 않게 합니다.
 * - 계정 정보와 분리된 보조 상태로만 사용되어 가짜 User 생성을 피합니다.
 */
data class ProfileBodyProfileSnapshot(
    val sex: String? = null,
    val heightCm: Float? = null,
    val weightKg: Float? = null,
    val wingspanCm: Float? = null
)
