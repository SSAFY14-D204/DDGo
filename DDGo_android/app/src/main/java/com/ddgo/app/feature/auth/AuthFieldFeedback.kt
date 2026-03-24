package com.ddgo.app.feature.auth

enum class AuthFieldFeedbackTone {
    Neutral,
    Success,
    Error
}

data class AuthFieldFeedback(
    val message: String,
    val tone: AuthFieldFeedbackTone
)
