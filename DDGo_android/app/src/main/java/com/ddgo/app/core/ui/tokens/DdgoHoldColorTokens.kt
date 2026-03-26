package com.ddgo.app.core.ui.tokens

import androidx.annotation.ColorInt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.ddgo.app.domain.model.HoldDifficultyColor

data class DdgoHoldColorToken(
    val key: String,
    val displayName: String,
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

object DdgoHoldColorTokens {
    val Red = token(HoldDifficultyColor.RED, Color(0xFFFF0000))
    val Orange = token(HoldDifficultyColor.ORANGE, Color(0xFFFF7700))
    val Yellow = token(HoldDifficultyColor.YELLOW, Color(0xFFFED500))
    val Green = token(HoldDifficultyColor.GREEN, Color(0xFF65B969))
    val SkyBlue = token(HoldDifficultyColor.SKYBLUE, Color(0xFF6DCCF7))
    val Navy = token(HoldDifficultyColor.NAVY, Color(0xFF3757D3))
    val Purple = token(HoldDifficultyColor.PURPLE, Color(0xFF876FFF))
    val Brown = token(HoldDifficultyColor.BROWN, Color(0xFF8E5E2C))
    val Pink = token(HoldDifficultyColor.PINK, Color(0xFFFF56A8))
    val White = token(HoldDifficultyColor.WHITE, Color(0xFFF7F4F4), borderColor = Color(0xFFE0D9D9))
    val Gray = token(HoldDifficultyColor.GRAY, Color(0xFF999999))
    val Black = token(HoldDifficultyColor.BLACK, Color(0xFF0B0B0E))

    val All = listOf(
        Red,
        Orange,
        Yellow,
        Green,
        SkyBlue,
        Navy,
        Purple,
        Brown,
        Pink,
        White,
        Gray,
        Black
    )

    private val tokensByKey = All.associateBy { it.key }
    private val tokensByHoldDifficultyColor = mapOf(
        HoldDifficultyColor.RED to Red,
        HoldDifficultyColor.ORANGE to Orange,
        HoldDifficultyColor.YELLOW to Yellow,
        HoldDifficultyColor.GREEN to Green,
        HoldDifficultyColor.SKYBLUE to SkyBlue,
        HoldDifficultyColor.NAVY to Navy,
        HoldDifficultyColor.PURPLE to Purple,
        HoldDifficultyColor.BROWN to Brown,
        HoldDifficultyColor.PINK to Pink,
        HoldDifficultyColor.WHITE to White,
        HoldDifficultyColor.GRAY to Gray,
        HoldDifficultyColor.BLACK to Black
    )

    fun byKey(key: String?): DdgoHoldColorToken? {
        return key?.trim()?.lowercase()?.let(tokensByKey::get)
    }

    fun byHoldDifficultyColor(color: HoldDifficultyColor?): DdgoHoldColorToken? {
        return color?.let(tokensByHoldDifficultyColor::get)
    }

    fun resolveToken(
        colorName: String?,
        colorHex: String?
    ): DdgoHoldColorToken? {
        return byHoldDifficultyColor(HoldDifficultyColor.resolve(colorName, colorHex))
    }

    fun resolveKey(
        colorName: String?,
        colorHex: String?
    ): String? {
        return resolveToken(colorName = colorName, colorHex = colorHex)?.key
    }

    fun resolveDisplayName(
        colorName: String?,
        colorHex: String?
    ): String {
        return resolveToken(colorName = colorName, colorHex = colorHex)?.displayName
            ?: colorName?.trim().takeUnless { it.isNullOrBlank() }
            ?: ""
    }

    fun resolveColor(
        colorName: String?,
        colorHex: String?
    ): Color {
        return resolveToken(colorName = colorName, colorHex = colorHex)?.color
            ?: SkyBlue.color
    }

    fun resolveDisplayNameByKey(key: String): String? {
        return byKey(key)?.displayName
    }

    fun resolveColorByKey(key: String): Color? {
        return byKey(key)?.color
    }

    fun resolveBorderColorByKey(key: String): Color? {
        return byKey(key)?.borderColor
    }

    val difficultyReferenceColors: List<Color>
        get() = All.map { it.color }

    private fun token(
        holdDifficultyColor: HoldDifficultyColor,
        color: Color,
        borderColor: Color? = null
    ): DdgoHoldColorToken {
        return DdgoHoldColorToken(
            key = holdDifficultyColor.key,
            displayName = holdDifficultyColor.displayName,
            color = color,
            borderColor = borderColor,
            aliases = holdDifficultyColor.aliases,
            referenceHexes = holdDifficultyColor.referenceHexes
        )
    }
}
