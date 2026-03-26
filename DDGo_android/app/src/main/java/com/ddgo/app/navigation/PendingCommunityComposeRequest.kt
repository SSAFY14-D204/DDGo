package com.ddgo.app.navigation

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val PENDING_COMMUNITY_COMPOSE_REQUEST_KEY = "pendingCommunityComposeRequest"

@Serializable
data class PendingCommunityComposeRequest(
    val requestId: Long,
    val gymId: Long? = null,
    val gymName: String? = null,
    val videos: List<PendingCommunityComposeVideo>
)

@Serializable
data class PendingCommunityComposeVideo(
    val attemptNo: Int,
    val videoUri: String
)

private val pendingCommunityComposeRequestJson = Json { ignoreUnknownKeys = true }

fun PendingCommunityComposeRequest.toSavedStateValue(): String {
    return pendingCommunityComposeRequestJson.encodeToString(this)
}

fun String.toPendingCommunityComposeRequestOrNull(): PendingCommunityComposeRequest? {
    return runCatching {
        pendingCommunityComposeRequestJson.decodeFromString<PendingCommunityComposeRequest>(this)
    }.getOrNull()
}
