package com.ddgo.app.data.remote.common

private val TrailingExternalIdPattern = Regex("\\s*\\(\\d+\\)\\s*$")

object GymNameFormatter {

    fun sanitize(name: String?): String {
        val normalized = name?.trim().orEmpty()
        if (normalized.isEmpty()) {
            return normalized
        }

        return normalized.replace(TrailingExternalIdPattern, "").trim()
    }
}
