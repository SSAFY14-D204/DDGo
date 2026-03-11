package com.ddgo.app.feature.climbing.upload

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * flow
 * AttemptUploadScreen      : 영상 업로드
 * ChallengeCreateScreen    : 클라이밍장 일므 찾기 -> 난이도 -> 홀드색
 *  - in : 클라이밍장 이름(id), 난이도 레벨, 홀드 컬러
 * ChallengeHoldScreen      : 인식된 홀드 선택
 *  - in : 홀드 위치? 홀드 범위? 홀드 정보
 *
 * AttemptUploadScreen      : 챌린지에 대한 또 다른 영상 업로드
 *  - in : 추가 영상
 * AttemptResultScreen      : 모든 업로드에 대한 분석 영상
 *  - out : 영상에 대한 것들 결과들 보기
 */

@HiltViewModel
class UploadViewModel @Inject constructor(
    // TODO: 추가적인 UseCase, Repository 등을 주입받습니다.
) : ViewModel() {

    // UI 레이어에 노출할 상태 (로딩, 성공, 실패 등)
    private val _uiState = MutableStateFlow<UploadUiState>(UploadUiState.Idle)
    val uiState = _uiState.asStateFlow()

    // --- 1. AttemptUploadScreen (초기 영상 업로드) ---
    var videoUri by mutableStateOf<String?>(null)
        private set

    // --- 2. ChallengeCreateScreen (클라이밍장, 난이도, 홀드색) ---
    var gymId by mutableStateOf<Int?>(null)
        private set
    var gymName by mutableStateOf("")
        private set
    var difficultyLevel by mutableStateOf("")
        private set
    var holdColor by mutableStateOf("")
        private set

    // --- 3. ChallengeHoldScreen (홀드 정보) ---
    // TODO: 홀드 데이터 형태에 맞춰 타입 변경 (예: 데이터 클래스)
    var selectedHoldInfo by mutableStateOf<String?>(null)
        private set

    // --- 4. AttemptUploadScreen (추가 영상 업로드) ---
    var additionalVideoUri by mutableStateOf<String?>(null)
        private set

    // ====== 상태 업데이트 메서드 (이벤트 핸들러) ======

    fun updateVideoUri(uri: String) {
        videoUri = uri
    }

    fun updateGymInfo(id: Int, name: String) {
        gymId = id
        gymName = name
    }

    fun updateDifficulty(level: String) {
        difficultyLevel = level
    }

    fun updateHoldColor(color: String) {
        holdColor = color
    }

    fun updateSelectedHoldInfo(info: String) {
        selectedHoldInfo = info
    }

    fun updateAdditionalVideoUri(uri: String) {
        additionalVideoUri = uri
    }

    // ====== 비즈니스 로직 ======

    /**
     * 최종 챌린지 또는 영상을 서버에 제출합니다.
     */
    fun submitUpload() {
        viewModelScope.launch {
            _uiState.value = UploadUiState.Loading
            try {
                // TODO: API 호출 등 비즈니스 로직 연동
                // _uiState.value = UploadUiState.Success
            } catch (e: Exception) {
                _uiState.value = UploadUiState.Error(e.message ?: "알 수 없는 에러가 발생했습니다.")
            }
        }
    }

    fun resetState() {
        _uiState.value = UploadUiState.Idle
    }
}

sealed class UploadUiState {
    object Idle : UploadUiState()
    object Loading : UploadUiState()
    // TODO: 결과 화면 (AttemptResultScreen)에서 보여줄 분석 결과를 파라미터로 넣을 수도 있습니다.
    object Success : UploadUiState() 
    data class Error(val message: String) : UploadUiState()
}