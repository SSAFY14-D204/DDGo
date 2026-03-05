package com.ddgo.app.feature.upload

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ddgo.app.data.work.VideoAnalyzeWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Upload 화면의 상태 관리 ViewModel.
 *
 * 핵심 역할: UI와 백그라운드 Worker를 연결하는 "중개자"
 *
 * ── 아키텍처 포인트 ────────────────────────────────────────────────
 * ViewModel은 WorkManager에게 "실행 요청"만 합니다.
 * 실제 AI 분석 + 업로드는 VideoAnalyzeWorker가 담당합니다.
 * ──────────────────────────────────────────────────────────────────
 */
@HiltViewModel
class UploadViewModel @Inject constructor(
    private val workManager: WorkManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<UploadUiState>(UploadUiState.Idle)
    val uiState: StateFlow<UploadUiState> = _uiState.asStateFlow()

    private val _selectedVideoUri = MutableStateFlow<Uri?>(null)
    val selectedVideoUri: StateFlow<Uri?> = _selectedVideoUri.asStateFlow()

    fun selectVideo(uri: Uri) {
        _selectedVideoUri.value = uri
        _uiState.value = UploadUiState.VideoSelected(uri.toString())
    }

    /**
     * 백그라운드 분석 작업을 WorkManager에 등록합니다.
     * UI는 Worker 진행 상황과 무관하게 자유롭게 이동할 수 있습니다.
     */
    fun startAnalyze(grade: String) {
        val videoUri = _selectedVideoUri.value?.toString() ?: return

        val inputData = Data.Builder()
            .putString(VideoAnalyzeWorker.KEY_VIDEO_URI, videoUri)
            .putString(VideoAnalyzeWorker.KEY_WALL_GRADE, grade)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<VideoAnalyzeWorker>()
            .setInputData(inputData)
            .build()

        workManager.enqueue(workRequest)
        _uiState.value = UploadUiState.Analyzing
    }
}

/** Upload 화면의 UI 상태 */
sealed class UploadUiState {
    object Idle : UploadUiState()
    data class VideoSelected(val uriString: String) : UploadUiState()
    object Analyzing : UploadUiState()
    object Done : UploadUiState()
    data class Error(val message: String) : UploadUiState()
}
