// [DEBUG ONLY] 이 뷰모델은 포즈 랜드마크 데이터 디버깅 화면용입니다.
package com.ddgo.app.feature.debug

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.usecase.LogoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.FileNotFoundException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class DebugPoseViewModel @Inject constructor(
    private val debugPoseVideoAnalyzer: DebugPoseVideoAnalyzer,
    private val logoutUseCase: LogoutUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DebugPoseUiState())
    val uiState: StateFlow<DebugPoseUiState> = _uiState.asStateFlow()

    fun analyzeVideo(
        uri: Uri,
        displayName: String
    ) {
        _uiState.value = _uiState.value.copy(
            selectedVideoUri = uri,
            selectedVideoName = displayName,
            isAnalyzing = true
        )

        viewModelScope.launch {
            debugPoseVideoAnalyzer(uri.toString())
                .onSuccess { poses ->
                    _uiState.value = _uiState.value.copy(
                        isAnalyzing = false,
                        poseFrames = poses,
                        errorMessage = null
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isAnalyzing = false,
                        poseFrames = emptyList(),
                        errorMessage = error.toUserMessage()
                    )
                }
        }
    }

    fun logout(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoggingOut = true)
            logoutUseCase().onSuccess {
                _uiState.value = _uiState.value.copy(isLoggingOut = false, logoutSuccess = true)
                onSuccess()
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoggingOut = false,
                    errorMessage = "로그아웃 실패: ${error.message}"
                )
            }
        }
    }

    private fun Throwable.toUserMessage(): String = when (this) {
        is FileNotFoundException -> "assets/models/pose_landmarker_lite.task 파일이 없어 MediaPipe Pose를 시작할 수 없습니다."
        is IllegalArgumentException -> message ?: "선택한 비디오를 열 수 없습니다."
        else -> message ?: "MediaPipe Pose 분석에 실패했습니다."
    }
}

data class DebugPoseUiState(
    val selectedVideoUri: Uri? = null,
    val selectedVideoName: String? = null,
    val isAnalyzing: Boolean = false,
    val isLoggingOut: Boolean = false,
    val logoutSuccess: Boolean = false,
    val poseFrames: List<DebugPoseFrameResult> = emptyList(),
    val errorMessage: String? = null
)

data class DebugPoseFrameResult(
    val pose: Pose,
    val worldLandmarks: List<DebugPoseWorldLandmark>,
    val capturedBitmap: Bitmap? = null
)

data class DebugPoseWorldLandmark(
    val index: Int,
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float? = null,
    val presence: Float? = null
)
