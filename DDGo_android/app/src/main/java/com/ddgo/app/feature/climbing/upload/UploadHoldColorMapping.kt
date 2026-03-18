package com.ddgo.app.feature.climbing.upload

import android.graphics.Color as AndroidColor

private data class HoldColorFamily(
    val key: String,
    val classifierKey: String,
    val displayName: String,
    val aliases: Set<String>,
    val referenceHexes: Set<String>
)

private val holdColorFamilies = listOf(
    HoldColorFamily(
        key = "black",
        classifierKey = "black",
        displayName = "검정",
        aliases = setOf("black", "검정", "검은", "charcoal"),
        referenceHexes = setOf("292929", "0A0A12")
    ),
    HoldColorFamily(
        key = "gray",
        classifierKey = "gray",
        displayName = "회색",
        aliases = setOf("gray", "grey", "회색", "slategray"),
        referenceHexes = setOf("505050", "5C5C5C")
    ),
    HoldColorFamily(
        key = "white",
        classifierKey = "white",
        displayName = "흰색",
        aliases = setOf("white", "흰색", "ivory", "아이보리"),
        referenceHexes = setOf("FFFFFF", "F5F1F1", "F0F0F0")
    ),
    HoldColorFamily(
        key = "brown",
        classifierKey = "brown",
        displayName = "갈색",
        aliases = setOf("brown", "갈색", "tan", "beige"),
        referenceHexes = setOf("6B3E1C", "8A4B16")
    ),
    HoldColorFamily(
        key = "purple",
        classifierKey = "purple",
        displayName = "보라",
        aliases = setOf("purple", "보라", "violet"),
        referenceHexes = setOf("876FFF", "8265EE")
    ),
    HoldColorFamily(
        key = "navy",
        classifierKey = "blue",
        displayName = "남색",
        aliases = setOf("navy", "남색", "indigo"),
        referenceHexes = setOf("373FD7", "3F43DB")
    ),
    HoldColorFamily(
        key = "blue",
        classifierKey = "blue",
        displayName = "파랑",
        aliases = setOf("blue", "파랑", "파란", "cyan", "sky", "skyblue", "sky blue", "하늘색", "하늘"),
        referenceHexes = setOf("4396FB", "1FC4E2")
    ),
    HoldColorFamily(
        key = "green",
        classifierKey = "green",
        displayName = "초록",
        aliases = setOf("green", "초록", "초록색", "lime"),
        referenceHexes = setOf("65B969", "48BE5C")
    ),
    HoldColorFamily(
        key = "orange",
        classifierKey = "orange",
        displayName = "주황",
        aliases = setOf("orange", "주황", "amber"),
        referenceHexes = setOf("FF7700", "FF7A00")
    ),
    HoldColorFamily(
        key = "red",
        classifierKey = "red",
        displayName = "빨강",
        aliases = setOf("red", "빨강", "빨간", "crimson"),
        referenceHexes = setOf("FF0000", "FF1208")
    ),
    HoldColorFamily(
        key = "yellow",
        classifierKey = "yellow",
        displayName = "노랑",
        aliases = setOf("yellow", "노랑", "노란", "gold"),
        referenceHexes = setOf("FED500", "FFCB12")
    ),
    HoldColorFamily(
        key = "pink",
        classifierKey = "pink",
        displayName = "핑크",
        aliases = setOf("pink", "핑크", "magenta", "rose", "hotpink"),
        referenceHexes = setOf("FF56A8", "FF43AC")
    )
)

private val holdColorFamilyByKey = holdColorFamilies.associateBy { it.key }

internal fun resolveHoldColorKey(
    colorName: String?,
    colorHex: String?
): String? {
    val normalizedName = colorName?.trim()?.lowercase().orEmpty()
    if (normalizedName.isNotBlank()) {
        holdColorFamilies.firstOrNull { normalizedName in it.aliases }?.let { return it.key }
    }

    val normalizedHex = colorHex
        ?.trim()
        ?.removePrefix("#")
        ?.uppercase()
        ?.takeIf { it.isNotBlank() }
        ?: return null

    holdColorFamilies.firstOrNull { normalizedHex in it.referenceHexes }?.let { return it.key }
    return resolveNearestHoldColorKey(normalizedHex)
}

internal fun resolveClassifierHoldColor(
    colorName: String?,
    colorHex: String?
): String? {
    val key = resolveHoldColorKey(colorName = colorName, colorHex = colorHex) ?: return null
    return holdColorFamilyByKey[key]?.classifierKey
}

internal fun resolveHoldColorDisplayName(
    colorName: String?,
    colorHex: String?
): String {
    val key = resolveHoldColorKey(colorName = colorName, colorHex = colorHex)
    return holdColorFamilyByKey[key]?.displayName
        ?: colorName?.trim().takeUnless { it.isNullOrBlank() }
        ?: ""
}

private fun resolveNearestHoldColorKey(normalizedHex: String): String? {
    val targetArgb = parseArgb(normalizedHex) ?: return null
    val nearest = holdColorFamilies
        .mapNotNull { family ->
            family.referenceHexes
                .mapNotNull(::parseArgb)
                .minOfOrNull { referenceArgb ->
                    colorDistance(targetArgb, referenceArgb)
                }
                ?.let { family.key to it }
        }
        .minByOrNull { (_, distance) -> distance }
        ?: return null

    return nearest.first.takeIf { nearest.second <= MAX_COLOR_DISTANCE }
}

private fun parseArgb(normalizedHex: String): Int? {
    val withPrefix = "#$normalizedHex"
    return runCatching { AndroidColor.parseColor(withPrefix) }.getOrNull()
}

private fun colorDistance(a: Int, b: Int): Int {
    val dr = AndroidColor.red(a) - AndroidColor.red(b)
    val dg = AndroidColor.green(a) - AndroidColor.green(b)
    val db = AndroidColor.blue(a) - AndroidColor.blue(b)
    return (dr * dr) + (dg * dg) + (db * db)
}

private const val MAX_COLOR_DISTANCE = 12000
