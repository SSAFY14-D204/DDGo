package com.ddgo.app.core.ui.tokens

import androidx.annotation.ColorInt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

data class DdgoHoldColorToken(
    val key: String,
    val label: String,
    val displayName: String,
    val classifierKey: String,
    val color: Color,
    val borderColor: Color? = null,
    val aliases: Set<String> = emptySet(),
    val referenceHexes: Set<String> = emptySet()
) {
    @get:ColorInt
    val colorInt: Int
        get() = color.toArgb()

    @get:ColorInt
    val borderColorInt: Int?
        get() = borderColor?.toArgb()
}

object DdgoHoldColorPalette {
    val Red = DdgoHoldColorToken(
        key = "red",
        label = "Red",
        displayName = "빨강",
        classifierKey = "red",
        color = DdgoColorTokens.HoldRed,
        aliases = setOf("빨강"),
        referenceHexes = setOf("FF0000")
    )

    val Orange = DdgoHoldColorToken(
        key = "orange",
        label = "Orange",
        displayName = "주황",
        classifierKey = "orange",
        color = DdgoColorTokens.HoldOrange,
        aliases = setOf("주황"),
        referenceHexes = setOf("FF7700")
    )

    val Yellow = DdgoHoldColorToken(
        key = "yellow",
        label = "Yellow",
        displayName = "노랑",
        classifierKey = "yellow",
        color = DdgoColorTokens.HoldYellow,
        aliases = setOf("노랑"),
        referenceHexes = setOf("FED500")
    )

    val Green = DdgoHoldColorToken(
        key = "green",
        label = "Green",
        displayName = "초록",
        classifierKey = "green",
        color = DdgoColorTokens.HoldGreen,
        aliases = setOf("초록"),
        referenceHexes = setOf("65B969")
    )

    val Sky = DdgoHoldColorToken(
        key = "skyblue",
        label = "Sky",
        displayName = "하늘색",
        classifierKey = "skyblue",
        color = DdgoColorTokens.HoldSky,
        aliases = setOf("하늘색"),
        referenceHexes = setOf("60DCFF")
    )

    val Blue = DdgoHoldColorToken(
        key = "blue",
        label = "Blue",
        displayName = "파랑",
        // The current classifier does not expose a dedicated blue label.
        classifierKey = "navy",
        color = DdgoColorTokens.HoldBlue,
        aliases = setOf("파랑"),
        referenceHexes = setOf("53A6FF")
    )

    val Navy = DdgoHoldColorToken(
        key = "navy",
        label = "Navy",
        displayName = "남색",
        classifierKey = "navy",
        color = DdgoColorTokens.HoldNavy,
        aliases = setOf("남색"),
        referenceHexes = setOf("4E56E5")
    )

    val Purple = DdgoHoldColorToken(
        key = "purple",
        label = "Purple",
        displayName = "보라",
        classifierKey = "purple",
        color = DdgoColorTokens.HoldPurple,
        aliases = setOf("보라"),
        referenceHexes = setOf("876FFF")
    )

    val Brown = DdgoHoldColorToken(
        key = "brown",
        label = "Brown",
        displayName = "갈색",
        classifierKey = "brown",
        color = DdgoColorTokens.HoldBrown,
        aliases = setOf("갈색"),
        referenceHexes = setOf("6B3E1C")
    )

    val Pink = DdgoHoldColorToken(
        key = "pink",
        label = "Pink",
        displayName = "핑크",
        classifierKey = "pink",
        color = DdgoColorTokens.HoldPink,
        aliases = setOf("핑크"),
        referenceHexes = setOf("FF56A8")
    )

    val Ivory = DdgoHoldColorToken(
        key = "white",
        label = "Ivory",
        displayName = "흰색",
        classifierKey = "white",
        color = DdgoColorTokens.HoldIvory,
        borderColor = DdgoColorTokens.NeutralLightGray,
        aliases = setOf("흰색"),
        referenceHexes = setOf("F7F4F4")
    )

    val Gray = DdgoHoldColorToken(
        key = "gray",
        label = "Gray",
        displayName = "회색",
        classifierKey = "gray",
        color = DdgoColorTokens.HoldGray,
        aliases = setOf("회색"),
        referenceHexes = setOf("505050")
    )

    val Black = DdgoHoldColorToken(
        key = "black",
        label = "Black",
        displayName = "검정",
        classifierKey = "black",
        color = DdgoColorTokens.HoldBlack,
        aliases = setOf("검정"),
        referenceHexes = setOf("0B0B0E")
    )

    val White = Ivory

    val all = listOf(
        Red,
        Orange,
        Yellow,
        Green,
        Sky,
        Blue,
        Navy,
        Purple,
        Brown,
        Pink,
        Ivory,
        Gray,
        Black
    )

    private val tokensByAlias = buildMap {
        all.forEach { token ->
            put(token.key.lowercase(), token)
            token.aliases.forEach { alias ->
                put(alias.trim().lowercase(), token)
            }
        }
    }

    fun tokenForKey(key: String?): DdgoHoldColorToken? {
        val normalized = key?.trim()?.lowercase().orEmpty()
        if (normalized.isBlank()) {
            return null
        }
        return tokensByAlias[normalized]
    }

    fun colorForKey(key: String?): Color? = tokenForKey(key)?.color

    fun displayNameForKey(key: String?): String? = tokenForKey(key)?.displayName
}
