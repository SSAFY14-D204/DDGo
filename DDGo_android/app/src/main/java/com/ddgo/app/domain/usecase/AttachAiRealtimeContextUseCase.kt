package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.repository.AiRealtimeSessionAck
import com.ddgo.app.domain.repository.AiRealtimeSessionContextRequest
import com.ddgo.app.domain.repository.AiRealtimeSessionHandle
import com.ddgo.app.domain.repository.AiRealtimeSessionRepository
import javax.inject.Inject

class AttachAiRealtimeContextUseCase @Inject constructor(
    private val repository: AiRealtimeSessionRepository
) {
    suspend operator fun invoke(
        session: AiRealtimeSessionHandle,
        request: AiRealtimeSessionContextRequest
    ): Result<AiRealtimeSessionAck> = repository.attachContext(session, request)
}
