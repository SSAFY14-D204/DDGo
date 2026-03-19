package com.ddgo.app.feature.profile

import com.ddgo.app.domain.model.User
import com.ddgo.app.feature.profile.mapper.ProfileUiStateMapper
import com.ddgo.app.feature.profile.model.ProfileUiState

/**
 * 프로필 화면 preview 전용 샘플 데이터를 제공합니다.
 *
 * 역할:
 * - 실제 화면과 같은 mapper를 사용해 preview와 런타임 구성이 어긋나지 않게 합니다.
 * - preview에서는 샘플 사용자 정보만 만들고, 섹션 조립은 mapper에 맡깁니다.
 */
internal object ProfilePreviewData {

    /** 기본 프로필 화면 preview 상태를 만듭니다. */
    fun defaultUiState(): ProfileUiState {
        return ProfileUiStateMapper.create(
            user = sampleUser(),
            nicknameSnapshot = null,
            bodyProfileSnapshot = null,
            isLoadingProfile = false,
            isLoggingOut = false,
            isDeletingAccount = false,
            nicknameEditor = null,
            bodyProfileEditor = null,
            passwordEditor = null
        )
    }

    /** preview에서만 사용하는 샘플 사용자입니다. */
    private fun sampleUser(): User {
        return User(
            id = 1L,
            username = "griphunter",
            nickname = "Peak Hopper",
            sex = "M",
            heightCm = 176f,
            weightKg = 68f,
            wingspanCm = 181f
        )
    }
}
