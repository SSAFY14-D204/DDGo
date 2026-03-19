package com.ddgo.app.domain.repository

import com.ddgo.app.domain.model.AiAnalysisRequestContext
import com.ddgo.app.domain.model.AiAnalysisResult

interface AiAnalysisRepository {
    suspend fun analyze(context: AiAnalysisRequestContext): Result<AiAnalysisResult>
}
