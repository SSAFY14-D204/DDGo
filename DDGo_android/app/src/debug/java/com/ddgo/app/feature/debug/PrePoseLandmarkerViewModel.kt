// [DEBUG ONLY] 이 파일은 디버그 전용 기능(포즈 분석 및 JSON 내보내기)을 위해 작성되었습니다.
package com.ddgo.app.feature.debug

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import kotlin.system.measureTimeMillis

private const val DEFAULT_ANALYSIS_FPS_LIMIT = 30
private val SUPPORTED_ANALYSIS_FPS_LIMITS = setOf(10, 20, 30)

@HiltViewModel
class PrePoseLandmarkerViewModel @Inject constructor(
    private val prePoseVideoAnalyzer: PrePoseVideoAnalyzer,
    private val optimizedPrePoseVideoAnalyzer: OptimizedPrePoseVideoAnalyzer
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrePoseUiState())
    val uiState: StateFlow<PrePoseUiState> = _uiState.asStateFlow()

    fun analyzeVideo(
        uri: Uri,
        displayName: String,
        useOptimized: Boolean = false,
        analysisFpsLimit: Int = DEFAULT_ANALYSIS_FPS_LIMIT
    ) {
        val normalizedAnalysisFpsLimit = normalizeAnalysisFpsLimit(analysisFpsLimit)

        _uiState.value = _uiState.value.copy(
            selectedVideoUri = uri,
            selectedVideoName = displayName,
            isAnalyzing = true,
            analysisProgress = 0f,
            poseFrames = emptyList(),
            errorMessage = null,
            analysisTimeMs = 0L,
            isOptimized = useOptimized,
            analysisFpsLimit = normalizedAnalysisFpsLimit
        )

        viewModelScope.launch {
            var poses: List<DebugPoseFrameResult> = emptyList()
            val time = measureTimeMillis {
                val result = if (useOptimized) {
                    optimizedPrePoseVideoAnalyzer(
                        videoUri = uri.toString(),
                        analysisFpsLimit = normalizedAnalysisFpsLimit
                    ) { progress ->
                        _uiState.value = _uiState.value.copy(analysisProgress = progress)
                    }
                } else {
                    prePoseVideoAnalyzer(
                        videoUri = uri.toString(),
                        analysisFpsLimit = normalizedAnalysisFpsLimit
                    ) { progress ->
                        _uiState.value = _uiState.value.copy(analysisProgress = progress)
                    }
                }
                
                result.onSuccess { poses = it }
                    .onFailure { error ->
                        _uiState.value = _uiState.value.copy(
                            isAnalyzing = false,
                            errorMessage = error.message ?: "분석 중 오류가 발생했습니다."
                        )
                        return@launch
                    }
            }

            _uiState.value = _uiState.value.copy(
                isAnalyzing = false,
                poseFrames = poses,
                analysisTimeMs = time,
                errorMessage = null
            )
        }
    }

    private fun normalizeAnalysisFpsLimit(value: Int): Int {
        return if (value in SUPPORTED_ANALYSIS_FPS_LIMITS) {
            value
        } else {
            DEFAULT_ANALYSIS_FPS_LIMIT
        }
    }

    fun exportPoseDataToJson(context: Context) {
        val currentPoseFrames = _uiState.value.poseFrames
        if (currentPoseFrames.isEmpty()) {
            Toast.makeText(context, "분석된 데이터가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            try {
                // 1. DTO 변환 (Bitmap 제외)
                val exportData = currentPoseFrames.map { frame ->
                    PoseExportDto(
                        frameTimeMs = frame.pose.frameTimeMs,
                        landmarks = frame.pose.landmarks.map { 
                            LandmarkDto(
                                index = it.index,
                                x = it.x,
                                y = it.y,
                                z = it.z,
                                visibility = it.visibility,
                                presence = it.presence
                            ) 
                        },
                        worldLandmarks = frame.worldLandmarks.map { 
                            LandmarkDto(
                                index = it.index,
                                x = it.x,
                                y = it.y,
                                z = it.z,
                                visibility = it.visibility,
                                presence = it.presence
                            ) 
                        }
                    )
                }

                // 2. JSON 직렬화 (Pretty Print)
                val json = Json { prettyPrint = true }
                val jsonString = json.encodeToString(exportData)

                // 3. 파일 저장 (export_{video_name}_{timestamp}.json)
                val videoName = _uiState.value.selectedVideoName?.substringBeforeLast(".") ?: "unknown"
                val timestamp = System.currentTimeMillis()
                val fileName = "export_${videoName}_$timestamp.json"
                
                val cacheFile = File(context.cacheDir, fileName)
                cacheFile.writeText(jsonString)

                // 4. FileProvider를 통한 공유 Intent 실행
                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    cacheFile
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                context.startActivity(Intent.createChooser(shareIntent, "Pose 데이터 내보내기"))
                
            } catch (e: Exception) {
                Toast.makeText(context, "내보내기 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

data class PrePoseUiState(
    val selectedVideoUri: Uri? = null,
    val selectedVideoName: String? = null,
    val isAnalyzing: Boolean = false,
    val analysisProgress: Float = 0f,
    val poseFrames: List<DebugPoseFrameResult> = emptyList(),
    val errorMessage: String? = null,
    val analysisTimeMs: Long = 0L,
    val isOptimized: Boolean = false,
    val analysisFpsLimit: Int = DEFAULT_ANALYSIS_FPS_LIMIT
)

@Serializable
data class PoseExportDto(
    val frameTimeMs: Long,
    val landmarks: List<LandmarkDto>,
    val worldLandmarks: List<LandmarkDto>
)

@Serializable
data class LandmarkDto(
    val index: Int,
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float? = null,
    val presence: Float? = null
)
