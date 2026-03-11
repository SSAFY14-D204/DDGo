package com.ddgo.app.feature.debug

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.FileNotFoundException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class DebugPoseViewModel @Inject constructor(
    private val debugPoseVideoAnalyzer: DebugPoseVideoAnalyzer
) : ViewModel() {

    private val _uiState = MutableStateFlow(DebugPoseUiState())
    val uiState: StateFlow<DebugPoseUiState> = _uiState.asStateFlow()

    fun analyzeVideo(
        uri: Uri,
        displayName: String
    ) {
        _uiState.value = DebugPoseUiState(
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
    val poseFrames: List<com.ddgo.app.domain.model.Pose> = emptyList(),
    val errorMessage: String? = null
)
