package com.ddgo.app.feature.profile.model

data class ProfileFieldFeedback(
    val message: String,
    val tone: ProfileFieldFeedbackTone
)

enum class ProfileFieldFeedbackTone {
    Neutral,
    Success,
    Error
}
