package com.ddgo.app.domain.repository

import com.ddgo.app.domain.model.AiPoseSequence

interface AiPoseSequenceProvider {
    suspend fun analyzePoseSequence(
        videoUri: String,
        analysisFpsLimit: Int = 10
    ): AiPoseSequence
}
