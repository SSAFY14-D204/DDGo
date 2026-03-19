package com.ddgo.app.feature.profile.mapper

import com.ddgo.app.domain.model.User
import com.ddgo.app.feature.profile.ProfileStrings
import com.ddgo.app.feature.profile.model.ProfileActionTone
import com.ddgo.app.feature.profile.model.ProfileActionType
import com.ddgo.app.feature.profile.model.ProfileBodyProfileEditorUiState
import com.ddgo.app.feature.profile.model.ProfileBodyProfileSnapshot
import com.ddgo.app.feature.profile.model.ProfileDangerZoneUiModel
import com.ddgo.app.feature.profile.model.ProfileHeaderUiModel
import com.ddgo.app.feature.profile.model.ProfileInfoRowUiModel
import com.ddgo.app.feature.profile.model.ProfileInfoSectionUiModel
import com.ddgo.app.feature.profile.model.ProfileNicknameEditorUiState
import com.ddgo.app.feature.profile.model.ProfilePasswordEditorUiState
import com.ddgo.app.feature.profile.model.ProfileRowTrailing
import com.ddgo.app.feature.profile.model.ProfileSectionActionUiModel
import com.ddgo.app.feature.profile.model.ProfileSexOption
import com.ddgo.app.feature.profile.model.ProfileUiState

/**
 * 프로필 feature 내부 상태를 화면에서 바로 사용할 UI 모델로 변환합니다.
 *
 * 역할:
 * - 사용자 정보와 스냅샷 값을 병합해 화면 기준 상태를 만듭니다.
 * - 섹션 구성과 사용자 노출 문구를 한 곳에서 조립합니다.
 * - preview와 실제 화면이 같은 매핑 규칙을 쓰도록 기준점을 제공합니다.
 */
internal object ProfileUiStateMapper {

    /**
     * 프로필 화면 전체 상태를 조립합니다.
     *
     * [nicknameSnapshot], [bodyProfileSnapshot]은 저장 직후 `/me` 재조회가 늦더라도
     * 방금 반영한 값을 화면에서 유지하기 위한 임시 스냅샷입니다.
     */
    fun create(
        user: User?,
        nicknameSnapshot: String?,
        bodyProfileSnapshot: ProfileBodyProfileSnapshot?,
        isLoadingProfile: Boolean,
        isLoggingOut: Boolean,
        isDeletingAccount: Boolean,
        nicknameEditor: ProfileNicknameEditorUiState?,
        bodyProfileEditor: ProfileBodyProfileEditorUiState?,
        passwordEditor: ProfilePasswordEditorUiState?
    ): ProfileUiState {
        val resolvedNickname = resolveNickname(user, nicknameSnapshot)
        val bodyProfile = mergeBodyProfile(user, bodyProfileSnapshot)
        val hasBodyProfile = hasAnyBodyProfile(bodyProfile)

        return ProfileUiState(
            title = ProfileStrings.ScreenTitle,
            header = ProfileHeaderUiModel(
                nickname = when {
                    !resolvedNickname.isNullOrBlank() -> resolvedNickname
                    !user?.username.isNullOrBlank() -> user.username
                    isLoadingProfile -> ProfileStrings.Loading
                    else -> ProfileStrings.DefaultNickname
                },
                accountId = when {
                    !user?.username.isNullOrBlank() && resolvedNickname != user.username ->
                        "@${user.username}"
                    isLoadingProfile -> ProfileStrings.LoadingAccount
                    else -> ""
                }
            ),
            infoSections = listOf(
                buildAccountSection(
                    user = user,
                    resolvedNickname = resolvedNickname,
                    isLoadingProfile = isLoadingProfile
                ),
                buildBodyProfileSection(
                    bodyProfile = bodyProfile,
                    isLoadingProfile = isLoadingProfile,
                    hasBodyProfile = hasBodyProfile
                ),
                buildSecuritySection()
            ),
            dangerZone = ProfileDangerZoneUiModel(
                title = ProfileStrings.DangerZoneCardTitle,
                subtitle = ProfileStrings.DangerZoneCardSubtitle.takeIf { it.isNotBlank() },
                actionLabel = ProfileStrings.DangerZoneAction,
                actionType = ProfileActionType.DeleteAccount
            ),
            isLoadingProfile = isLoadingProfile,
            isLoggingOut = isLoggingOut,
            isDeletingAccount = isDeletingAccount,
            nicknameEditor = nicknameEditor,
            bodyProfileEditor = bodyProfileEditor,
            passwordEditor = passwordEditor
        )
    }

    /** 닉네임 입력 다이얼로그 상태를 만듭니다. */
    fun createNicknameEditor(
        user: User?,
        nicknameSnapshot: String?
    ): ProfileNicknameEditorUiState {
        val resolvedNickname = resolveNickname(user, nicknameSnapshot).orEmpty()
        val hasNickname = resolvedNickname.isNotBlank()

        return ProfileNicknameEditorUiState(
            title = ProfileStrings.nicknameEditorTitle(hasNickname),
            description = ProfileStrings.nicknameEditorDescription(hasNickname),
            submitLabel = ProfileStrings.ActionSave,
            nicknameInput = resolvedNickname
        )
    }

    /** 신체 정보 입력 다이얼로그 상태를 만듭니다. */
    fun createBodyProfileEditor(
        user: User?,
        bodyProfileSnapshot: ProfileBodyProfileSnapshot?
    ): ProfileBodyProfileEditorUiState {
        val bodyProfile = mergeBodyProfile(user, bodyProfileSnapshot)
        val hasBodyProfile = hasAnyBodyProfile(bodyProfile)

        return ProfileBodyProfileEditorUiState(
            title = ProfileStrings.bodyProfileEditorTitle(hasBodyProfile),
            description = ProfileStrings.bodyProfileEditorDescription(hasBodyProfile),
            submitLabel = ProfileStrings.bodyProfileSubmitLabel(hasBodyProfile),
            sex = ProfileSexOption.fromApiValue(bodyProfile.sex),
            heightCmInput = bodyProfile.heightCm.toEditableString(),
            weightKgInput = bodyProfile.weightKg.toEditableString(),
            wingspanCmInput = bodyProfile.wingspanCm.toEditableString()
        )
    }

    /** 비밀번호 변경 다이얼로그 상태를 만듭니다. */
    fun createPasswordEditor(): ProfilePasswordEditorUiState {
        return ProfilePasswordEditorUiState(
            title = ProfileStrings.ChangePasswordDialogTitle,
            description = ProfileStrings.ChangePasswordDialogDescription,
            submitLabel = ProfileStrings.ActionSave
        )
    }

    /** 계정 섹션을 조립합니다. */
    private fun buildAccountSection(
        user: User?,
        resolvedNickname: String?,
        isLoadingProfile: Boolean
    ): ProfileInfoSectionUiModel {
        val hasNickname = !resolvedNickname.isNullOrBlank()

        return ProfileInfoSectionUiModel(
            title = ProfileStrings.AccountSectionTitle,
            rows = listOf(
                ProfileInfoRowUiModel(
                    title = ProfileStrings.UsernameRowTitle,
                    value = user?.username ?: loadingOrDash(isLoadingProfile)
                ),
                ProfileInfoRowUiModel(
                    title = ProfileStrings.NicknameRowTitle,
                    value = when {
                        isLoadingProfile -> ProfileStrings.Loading
                        hasNickname -> resolvedNickname
                        else -> ProfileStrings.NicknameEmpty
                    },
                    actionType = if (isLoadingProfile) null else ProfileActionType.EditNickname,
                    trailing = if (isLoadingProfile) {
                        ProfileRowTrailing.None
                    } else {
                        ProfileRowTrailing.Action(
                            label = ProfileStrings.nicknameActionLabel(hasNickname),
                            tone = ProfileActionTone.Accent
                        )
                    }
                )
            )
        )
    }

    /** 신체 정보 섹션을 조립합니다. */
    private fun buildBodyProfileSection(
        bodyProfile: ProfileBodyProfileSnapshot,
        isLoadingProfile: Boolean,
        hasBodyProfile: Boolean
    ): ProfileInfoSectionUiModel {
        return ProfileInfoSectionUiModel(
            title = ProfileStrings.BodyProfileSectionTitle,
            rows = listOf(
                ProfileInfoRowUiModel(
                    title = ProfileStrings.SexRowTitle,
                    value = if (isLoadingProfile) {
                        ProfileStrings.BodyProfileFieldDescriptionLoading
                    } else {
                        formatSex(bodyProfile.sex)
                    }
                ),
                ProfileInfoRowUiModel(
                    title = ProfileStrings.HeightRowTitle,
                    value = if (isLoadingProfile) {
                        ProfileStrings.BodyProfileFieldDescriptionLoading
                    } else {
                        formatMeasure(bodyProfile.heightCm, "cm")
                    }
                ),
                ProfileInfoRowUiModel(
                    title = ProfileStrings.WeightRowTitle,
                    value = if (isLoadingProfile) {
                        ProfileStrings.BodyProfileFieldDescriptionLoading
                    } else {
                        formatMeasure(bodyProfile.weightKg, "kg")
                    }
                ),
                ProfileInfoRowUiModel(
                    title = ProfileStrings.WingspanRowTitle,
                    value = if (isLoadingProfile) {
                        ProfileStrings.BodyProfileFieldDescriptionLoading
                    } else {
                        formatMeasure(bodyProfile.wingspanCm, "cm")
                    }
                ),
                ProfileInfoRowUiModel(
                    title = ProfileStrings.BodyProfileEditRowTitle,
                    actionType = if (isLoadingProfile) null else ProfileActionType.EditBodyProfile,
                    trailing = if (isLoadingProfile) {
                        ProfileRowTrailing.None
                    } else {
                        ProfileRowTrailing.Action(
                            label = ProfileStrings.bodyProfileActionLabel(hasBodyProfile),
                            tone = ProfileActionTone.Accent
                        )
                    }
                )
            )
        )
    }

    /** 보안 섹션을 조립합니다. */
    private fun buildSecuritySection(): ProfileInfoSectionUiModel {
        return ProfileInfoSectionUiModel(
            title = ProfileStrings.SecuritySectionTitle,
            rows = listOf(
                ProfileInfoRowUiModel(
                    title = ProfileStrings.ChangePasswordRowTitle,
                    actionType = ProfileActionType.ChangePassword,
                    trailing = ProfileRowTrailing.Disclosure
                ),
                ProfileInfoRowUiModel(
                    title = ProfileStrings.LogoutRowTitle,
                    actionType = ProfileActionType.Logout,
                    trailing = ProfileRowTrailing.Action(label = ProfileStrings.LogoutAction)
                )
            )
        )
    }

    /** 서버 사용자 정보와 로컬 닉네임 스냅샷을 병합합니다. */
    private fun resolveNickname(
        user: User?,
        nicknameSnapshot: String?
    ): String? {
        return nicknameSnapshot?.takeUnless { it.isBlank() }
            ?: user?.nickname?.takeUnless { it.isBlank() }
    }

    /** 서버 사용자 정보와 로컬 신체 정보 스냅샷을 병합합니다. */
    private fun mergeBodyProfile(
        user: User?,
        bodyProfileSnapshot: ProfileBodyProfileSnapshot?
    ): ProfileBodyProfileSnapshot {
        return ProfileBodyProfileSnapshot(
            sex = bodyProfileSnapshot?.sex ?: user?.sex,
            heightCm = bodyProfileSnapshot?.heightCm ?: user?.heightCm,
            weightKg = bodyProfileSnapshot?.weightKg ?: user?.weightKg,
            wingspanCm = bodyProfileSnapshot?.wingspanCm ?: user?.wingspanCm
        )
    }

    /** 신체 정보 중 하나라도 입력되어 있는지 확인합니다. */
    private fun hasAnyBodyProfile(bodyProfile: ProfileBodyProfileSnapshot): Boolean {
        return bodyProfile.sex != null ||
            bodyProfile.heightCm != null ||
            bodyProfile.weightKg != null ||
            bodyProfile.wingspanCm != null
    }

    /** 성별 값을 화면용 문구로 바꿉니다. */
    private fun formatSex(value: String?): String {
        return when (value) {
            "M" -> ProfileStrings.SexMale
            "F" -> ProfileStrings.SexFemale
            else -> ProfileStrings.BodyProfileMissing
        }
    }

    /** 측정값을 단위가 포함된 화면 문구로 바꿉니다. */
    private fun formatMeasure(
        value: Float?,
        unit: String
    ): String {
        if (value == null) return ProfileStrings.BodyProfileMissing

        val display = if (value % 1f == 0f) {
            value.toInt().toString()
        } else {
            value.toString()
        }
        return "$display $unit"
    }

    /** TextField에 들어갈 수 있는 안전한 문자열로 변환합니다. */
    private fun Float?.toEditableString(): String {
        if (this == null) return ""
        return if (this % 1f == 0f) {
            this.toInt().toString()
        } else {
            this.toString()
        }
    }

    /** 로딩 중이면 로딩 문구를, 아니면 `-`를 반환합니다. */
    private fun loadingOrDash(isLoadingProfile: Boolean): String {
        return if (isLoadingProfile) ProfileStrings.Loading else ProfileStrings.Dash
    }
}
