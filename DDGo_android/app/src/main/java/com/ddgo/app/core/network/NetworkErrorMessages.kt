package com.ddgo.app.core.network

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

private const val NETWORK_OFFLINE_MESSAGE = "네트워크에 연결되어 있지 않습니다. 연결 상태를 확인해 주세요."
private const val NETWORK_TIMEOUT_MESSAGE = "네트워크 연결이 원활하지 않습니다. 잠시 후 다시 시도해 주세요."

internal fun Throwable.toUserFacingNetworkMessageOrNull(): String? {
    var current: Throwable? = this
    while (current != null) {
        when (current) {
            is UnknownHostException,
            is ConnectException,
            is NoRouteToHostException -> return NETWORK_OFFLINE_MESSAGE
            is SocketTimeoutException -> return NETWORK_TIMEOUT_MESSAGE
        }
        current = current.cause
    }

    val rawMessage = message?.trim().orEmpty()
    return when {
        rawMessage.contains("Unable to resolve host", ignoreCase = true) -> NETWORK_OFFLINE_MESSAGE
        rawMessage.contains("Failed to connect", ignoreCase = true) -> NETWORK_OFFLINE_MESSAGE
        rawMessage.contains("No address associated with hostname", ignoreCase = true) -> NETWORK_OFFLINE_MESSAGE
        rawMessage.contains("timed out", ignoreCase = true) -> NETWORK_TIMEOUT_MESSAGE
        rawMessage.contains("timeout", ignoreCase = true) -> NETWORK_TIMEOUT_MESSAGE
        else -> null
    }
}
