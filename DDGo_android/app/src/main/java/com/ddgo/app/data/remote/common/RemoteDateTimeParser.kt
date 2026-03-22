package com.ddgo.app.data.remote.common

import java.time.LocalDateTime
import java.time.OffsetDateTime

object RemoteDateTimeParser {

    fun parse(value: String?): LocalDateTime? {
        val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        return runCatching { LocalDateTime.parse(raw) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(raw).toLocalDateTime() }.getOrNull()
    }
}
