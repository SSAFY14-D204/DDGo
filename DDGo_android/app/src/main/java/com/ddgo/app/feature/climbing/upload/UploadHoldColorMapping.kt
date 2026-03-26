package com.ddgo.app.feature.climbing.upload

import com.ddgo.app.domain.model.HoldDifficultyColor

internal fun resolveHoldColorKey(
    colorName: String?,
    colorHex: String?
): String? {
    return HoldDifficultyColor.resolve(colorName = colorName, colorHex = colorHex)?.key
}

internal fun resolveClassifierHoldColor(
    colorName: String?,
    colorHex: String?
): String? {
    return resolveHoldColorKey(colorName = colorName, colorHex = colorHex)
}

internal fun resolveHoldColorDisplayName(
    colorName: String?,
    colorHex: String?
): String {
    return HoldDifficultyColor.resolve(colorName = colorName, colorHex = colorHex)?.displayName
        ?: colorName?.trim().takeUnless { it.isNullOrBlank() }
        ?: ""
}
