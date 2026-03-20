package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.repository.AiRealtimeSessionHandle
import com.ddgo.app.domain.repository.AiRealtimeSessionRepository
import com.ddgo.app.domain.repository.AiRealtimeSessionStartRequest
import javax.inject.Inject

class StartAiRealtimeSessionUseCase @Inject constructor(
    private val repository: AiRealtimeSessionRepository
) {
    suspend operator fun invoke(
        request: AiRealtimeSessionStartRequest
    ): Result<AiRealtimeSessionHandle> = repository.startSession(request)
}
