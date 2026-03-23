package com.ddgo.app.domain.model

data class AuthToken(
    val accessToken: String,
    val refreshToken: String,
    val isNewUser: Boolean? = null,
    val needsOnboarding: Boolean? = null
)
