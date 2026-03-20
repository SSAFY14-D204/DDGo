package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.model.AiPoseFrame
import com.ddgo.app.domain.repository.AiRealtimeSessionAck
import com.ddgo.app.domain.repository.AiRealtimeSessionHandle
import com.ddgo.app.domain.repository.AiRealtimeSessionRepository
import javax.inject.Inject

class AppendAiRealtimePoseChunkUseCase @Inject constructor(
    private val repository: AiRealtimeSessionRepository
) {
    suspend operator fun invoke(
        session: AiRealtimeSessionHandle,
        frames: List<AiPoseFrame>
    ): Result<AiRealtimeSessionAck> = repository.appendPoseFrames(session, frames)
}
