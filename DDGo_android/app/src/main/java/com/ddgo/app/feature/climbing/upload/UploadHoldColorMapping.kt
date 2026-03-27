package com.ddgo.app.feature.climbing.upload

import com.ddgo.app.core.ui.tokens.DdgoHoldColorPalette
import java.util.Locale

private data class HoldColorFamily(
    val key: String,
    val classifierKey: String,
    val displayName: String,
    val aliases: Set<String>,
    val referenceHexes: Set<String>
)

private val holdColorFamilies = DdgoHoldColorPalette.all.map { token ->
    HoldColorFamily(
        key = token.key,
        classifierKey = token.classifierKey,
        displayName = token.displayName,
        aliases = buildSet {
            add(token.key.lowercase(Locale.ROOT))
            token.aliases.forEach { alias ->
                add(alias.trim().lowercase(Locale.ROOT))
            }
        },
        referenceHexes = token.referenceHexes.map { it.uppercase(Locale.ROOT) }.toSet()
    )
}

private val holdColorFamilyByKey = holdColorFamilies.associateBy(HoldColorFamily::key)

internal fun resolveHoldColorKey(
    colorName: String?,
    colorHex: String?
): String? {
    val normalizedName = colorName?.trim()?.lowercase(Locale.ROOT).orEmpty()
    if (normalizedName.isNotBlank()) {
        holdColorFamilies.firstOrNull { normalizedName in it.aliases }?.let { return it.key }
    }

    val normalizedHex = colorHex
        ?.trim()
        ?.removePrefix("#")
        ?.uppercase(Locale.ROOT)
        ?.takeIf(String::isNotBlank)
        ?: return null

    return holdColorFamilies.firstOrNull { normalizedHex in it.referenceHexes }?.key
}

internal fun resolveClassifierHoldColor(
    colorName: String?,
    colorHex: String?
): String? {
    val key = resolveHoldColorKey(colorName = colorName, colorHex = colorHex)
        ?: DdgoHoldColorPalette.tokenForKey(colorName)?.key
        ?: return null
    return holdColorFamilyByKey[key]?.classifierKey
}

internal fun resolveHoldColorDisplayName(
    colorName: String?,
    colorHex: String?
): String {
    val key = resolveHoldColorKey(colorName = colorName, colorHex = colorHex)
    return holdColorFamilyByKey[key]?.displayName
        ?: DdgoHoldColorPalette.displayNameForKey(colorName)
        ?: colorName?.trim().takeUnless { it.isNullOrBlank() }
        ?: ""
}
