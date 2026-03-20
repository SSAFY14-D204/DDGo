package com.ddgo.app.feature.climbing.upload

internal fun normalizeVideoRotationDegrees(rotationDegrees: Int): Int {
    val normalized = ((rotationDegrees % 360) + 360) % 360
    return when (normalized) {
        90, 180, 270 -> normalized
        else -> 0
    }
}
