package com.ddgo.app.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ddgo.app.core.ui.theme.DDGoTheme
import com.ddgo.app.feature.main.MainChromeDefaults
import com.ddgo.app.feature.profile.components.ProfileActionConfirmationDialog
import com.ddgo.app.feature.profile.components.ProfileBodyProfileEditorDialog
import com.ddgo.app.feature.profile.components.ProfileDangerZoneCard
import com.ddgo.app.feature.profile.components.ProfileHeroCard
import com.ddgo.app.feature.profile.components.ProfileInfoSection
import com.ddgo.app.feature.profile.components.ProfileNicknameEditorDialog
import com.ddgo.app.feature.profile.components.ProfilePasswordEditorDialog
import com.ddgo.app.feature.profile.components.ProfileTopBar
import com.ddgo.app.feature.profile.model.ProfileActionTone
import com.ddgo.app.feature.profile.model.ProfileActionType
import com.ddgo.app.feature.profile.model.ProfileUiEvent
import com.ddgo.app.feature.profile.model.ProfileUiState
import com.ddgo.app.feature.profile.style.ProfilePalette

/**
 * 프로필 화면 진입점입니다.
 *
 * 역할:
 * - ViewModel 상태를 구독해 편집 다이얼로그와 확인 다이얼로그를 조합합니다.
 * - 로그아웃/회원 탈퇴처럼 실수 비용이 큰 액션은 화면 레벨에서 한 번 더 확인받습니다.
 * - 인증 화면 이동과 스낵바 메시지는 UI 이벤트로만 처리합니다.
 */
@Composable
fun ProfileScreen(
    onNavigateToAuth: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmationAction by rememberSaveable { mutableStateOf<ProfileActionType?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                ProfileUiEvent.NavigateToAuth -> {
                    confirmationAction = null
                    onNavigateToAuth()
                }

                is ProfileUiEvent.ShowMessage -> {
                    confirmationAction = null
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    uiState.nicknameEditor?.let { editorState ->
        ProfileNicknameEditorDialog(
            state = editorState,
            onNicknameChanged = viewModel::updateNicknameInput,
            onConfirm = viewModel::submitNickname,
            onDismiss = viewModel::dismissNicknameEditor
        )
    }

    uiState.bodyProfileEditor?.let { editorState ->
        ProfileBodyProfileEditorDialog(
            state = editorState,
            onSexSelected = viewModel::updateBodyProfileSex,
            onHeightChanged = viewModel::updateHeightInput,
            onWeightChanged = viewModel::updateWeightInput,
            onWingspanChanged = viewModel::updateWingspanInput,
            onConfirm = viewModel::submitBodyProfile,
            onDismiss = viewModel::dismissBodyProfileEditor
        )
    }

    uiState.passwordEditor?.let { editorState ->
        ProfilePasswordEditorDialog(
            state = editorState,
            onCurrentPasswordChanged = viewModel::updateCurrentPasswordInput,
            onNewPasswordChanged = viewModel::updateNewPasswordInput,
            onConfirmPasswordChanged = viewModel::updateConfirmPasswordInput,
            onConfirm = viewModel::submitPasswordChange,
            onDismiss = viewModel::dismissPasswordEditor
        )
    }

    resolveConfirmationDialog(
        actionType = confirmationAction,
        uiState = uiState
    )?.let { dialogState ->
        ProfileActionConfirmationDialog(
            title = dialogState.title,
            message = dialogState.message,
            confirmLabel = dialogState.confirmLabel,
            dismissLabel = ProfileStrings.ActionCancel,
            confirmTone = dialogState.confirmTone,
            isLoading = dialogState.isLoading,
            onConfirm = { viewModel.onActionClick(dialogState.actionType) },
            onDismiss = { confirmationAction = null }
        )
    }

    ProfileScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onActionClick = { actionType ->
            when (actionType) {
                ProfileActionType.Logout,
                ProfileActionType.DeleteAccount -> confirmationAction = actionType

                else -> viewModel.onActionClick(actionType)
            }
        }
    )
}

/**
 * 프로필 메인 콘텐츠를 렌더링합니다.
 *
 * 카드 배치와 배경 스타일은 여기에서만 담당하고,
 * 실제 액션 로직은 상위 Screen 또는 ViewModel로 위임합니다.
 */
@Composable
internal fun ProfileScreenContent(
    uiState: ProfileUiState,
    snackbarHostState: SnackbarHostState,
    onActionClick: (ProfileActionType) -> Unit
) {
    val actionsEnabled = !uiState.isLoggingOut && !uiState.isDeletingAccount

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ProfilePalette.BackgroundTop)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 20.dp,
                bottom = MainChromeDefaults.ContentBottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                ProfileTopBar(title = uiState.title)
            }

            item {
                ProfileHeroCard(header = uiState.header)
            }

            items(
                items = uiState.infoSections,
                key = { it.title }
            ) { section ->
                ProfileInfoSection(
                    section = section,
                    actionsEnabled = actionsEnabled,
                    onActionClick = onActionClick
                )
            }

            item {
                ProfileDangerZoneCard(
                    dangerZone = uiState.dangerZone,
                    actionEnabled = actionsEnabled,
                    onActionClick = onActionClick
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 28.dp)
        )
    }
}

/**
 * 로그아웃/회원 탈퇴 확인창에 필요한 화면용 데이터입니다.
 *
 * 확인 대상이 달라도 다이얼로그 UI 자체는 같기 때문에,
 * 노출 문구와 강조 톤만 별도 모델로 정리합니다.
 */
private data class ProfileConfirmationDialogUiState(
    val actionType: ProfileActionType,
    val title: String,
    val message: String,
    val confirmLabel: String,
    val confirmTone: ProfileActionTone,
    val isLoading: Boolean
)

/** 현재 확인이 필요한 액션을 다이얼로그 상태로 변환합니다. */
private fun resolveConfirmationDialog(
    actionType: ProfileActionType?,
    uiState: ProfileUiState
): ProfileConfirmationDialogUiState? {
    return when (actionType) {
        ProfileActionType.Logout -> ProfileConfirmationDialogUiState(
            actionType = ProfileActionType.Logout,
            title = ProfileStrings.LogoutDialogTitle,
            message = ProfileStrings.LogoutDialogMessage,
            confirmLabel = ProfileStrings.LogoutAction,
            confirmTone = ProfileActionTone.Normal,
            isLoading = uiState.isLoggingOut
        )

        ProfileActionType.DeleteAccount -> ProfileConfirmationDialogUiState(
            actionType = ProfileActionType.DeleteAccount,
            title = ProfileStrings.DeleteAccountDialogTitle,
            message = ProfileStrings.DeleteAccountDialogMessage,
            confirmLabel = ProfileStrings.DangerZoneAction,
            confirmTone = ProfileActionTone.Danger,
            isLoading = uiState.isDeletingAccount
        )

        else -> null
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    DDGoTheme(darkTheme = false) {
        ProfileScreenContent(
            uiState = ProfilePreviewData.defaultUiState(),
            snackbarHostState = remember { SnackbarHostState() },
            onActionClick = {}
        )
    }
}
