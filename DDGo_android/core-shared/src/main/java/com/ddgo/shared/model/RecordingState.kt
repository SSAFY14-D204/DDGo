package com.ddgo.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class RecordingState(
    val sessionId: String,
    val isRecording: Boolean,
    val updatedAt: Long
)
