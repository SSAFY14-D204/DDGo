package com.ddgo.app.domain.repository

import com.ddgo.app.domain.model.PrePoseVideoAnalysisResult

interface PrePoseVideoAnalysisProvider {
    suspend fun analyze(
        videoUri: String,
        analysisFpsLimit: Int = 10
    ): PrePoseVideoAnalysisResult
}
