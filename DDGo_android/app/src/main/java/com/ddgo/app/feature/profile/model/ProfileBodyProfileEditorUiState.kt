package com.ddgo.app.feature.profile.model

import com.ddgo.app.feature.profile.ProfileStrings

/**
 * 신체 정보 입력/수정 다이얼로그에 필요한 UI 상태입니다.
 *
 * 역할:
 * - 입력 폼에 필요한 값과 저장 진행 상태를 함께 보관합니다.
 * - 화면에서는 이 모델만 바라보고 form 값을 그리도록 단순화합니다.
 */
data class ProfileBodyProfileEditorUiState(
    val title: String,
    val description: String,
    val submitLabel: String,
    val sex: ProfileSexOption? = null,
    val heightCmInput: String = "",
    val weightKgInput: String = "",
    val wingspanCmInput: String = "",
    val errorMessage: String? = null,
    val isSaving: Boolean = false
)

/**
 * 신체 정보 입력 폼에서 사용하는 성별 선택 값입니다.
 *
 * 역할:
 * - API에 전달할 값과 화면에 표시할 값을 함께 들고 있습니다.
 * - 입력 화면에서는 enum 기준으로 선택 상태를 안정적으로 유지합니다.
 */
enum class ProfileSexOption(
    val apiValue: String,
    val label: String
) {
    Male(apiValue = "M", label = ProfileStrings.SexMale),
    Female(apiValue = "F", label = ProfileStrings.SexFemale);

    companion object {
        /** API 값을 프로필 화면 전용 enum으로 변환합니다. */
        fun fromApiValue(value: String?): ProfileSexOption? {
            return entries.firstOrNull { it.apiValue == value }
        }
    }
}
