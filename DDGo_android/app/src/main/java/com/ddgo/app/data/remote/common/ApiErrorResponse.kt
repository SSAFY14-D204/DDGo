package com.ddgo.app.data.remote.common

import kotlinx.serialization.Serializable

@Serializable
data class ApiErrorResponse(
    val success: Boolean? = null,
    val code: String? = null,
    val message: String? = null
)
