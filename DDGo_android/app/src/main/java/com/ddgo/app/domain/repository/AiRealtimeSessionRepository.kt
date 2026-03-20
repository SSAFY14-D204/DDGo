package com.ddgo.app.domain.repository

import com.ddgo.app.domain.model.AiAnalysisMode
import com.ddgo.app.domain.model.AiAnalysisResult
import com.ddgo.app.domain.model.AiPoseFrame
import com.ddgo.app.domain.model.AiUserBodyProfile
import com.ddgo.app.domain.model.AiVideoMetadata
import com.ddgo.app.domain.model.Hold

data class AiRealtimeSessionHandle(
    val sessionId: String,
    val requestedMode: AiAnalysisMode,
    val effectiveMode: AiAnalysisMode = requestedMode
)

data class AiRealtimeSessionStartRequest(
    val mode: AiAnalysisMode,
    val userBodyProfile: AiUserBodyProfile,
    val videoMetadata: AiVideoMetadata,
    val topKCrux: Int = 3,
    val frameStep: Int = 1
)

data class AiRealtimeSessionContextRequest(
    val holds: List<Hold>,
    val videoMetadata: AiVideoMetadata
)

data class AiRealtimeSessionAck(
    val sessionId: String,
    val acceptedFrames: Int = 0,
    val lastFrameIndex: Int = -1,
    val status: String = "",
    val message: String? = null
)

interface AiRealtimeSessionRepository {
    suspend fun startSession(request: AiRealtimeSessionStartRequest): Result<AiRealtimeSessionHandle>

    suspend fun appendPoseFrames(
        session: AiRealtimeSessionHandle,
        frames: List<AiPoseFrame>
    ): Result<AiRealtimeSessionAck>

    suspend fun attachContext(
        session: AiRealtimeSessionHandle,
        request: AiRealtimeSessionContextRequest
    ): Result<AiRealtimeSessionAck>

    suspend fun finalizeSession(session: AiRealtimeSessionHandle): Result<AiAnalysisResult>

    suspend fun abortSession(session: AiRealtimeSessionHandle): Result<AiRealtimeSessionAck>
}
