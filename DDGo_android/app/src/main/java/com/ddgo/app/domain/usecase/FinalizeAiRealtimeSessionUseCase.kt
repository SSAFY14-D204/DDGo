package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.model.AiAnalysisResult
import com.ddgo.app.domain.repository.AiRealtimeSessionHandle
import com.ddgo.app.domain.repository.AiRealtimeSessionRepository
import javax.inject.Inject

class FinalizeAiRealtimeSessionUseCase @Inject constructor(
    private val repository: AiRealtimeSessionRepository
) {
    suspend operator fun invoke(
        session: AiRealtimeSessionHandle
    ): Result<AiAnalysisResult> = repository.finalizeSession(session)
}
