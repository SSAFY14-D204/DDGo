package com.ddgo.app.domain.model

import java.util.Locale
import kotlin.math.pow

enum class HoldDifficultyColor(
    val key: String,
    val displayName: String,
    val aliases: Set<String>,
    val referenceHexes: Set<String>
) {
    RED(
        key = "red",
        displayName = "빨강",
        aliases = setOf("red", "빨강", "빨간", "레드", "crimson"),
        referenceHexes = setOf("FF0000", "FF1208")
    ),
    ORANGE(
        key = "orange",
        displayName = "주황",
        aliases = setOf("orange", "주황", "오렌지", "amber"),
        referenceHexes = setOf("FF7700", "FF7A00")
    ),
    YELLOW(
        key = "yellow",
        displayName = "노랑",
        aliases = setOf("yellow", "노랑", "노란", "노란색", "옐로", "gold"),
        referenceHexes = setOf("FED500", "FFCB12")
    ),
    GREEN(
        key = "green",
        displayName = "초록",
        aliases = setOf("green", "초록", "초록색", "그린", "lime"),
        referenceHexes = setOf("65B969", "48BE5C")
    ),
    SKYBLUE(
        key = "skyblue",
        displayName = "하늘색",
        aliases = setOf(
            "skyblue",
            "sky blue",
            "sky",
            "하늘색",
            "하늘",
            "하늘빛",
            "연파랑",
            "cyan",
            "blue",
            "파랑",
            "파란",
            "파란색",
            "블루",
            "스카이"
        ),
        referenceHexes = setOf("6DCCF7", "4396FB", "1FC4E2", "87CEEB", "00BFFF")
    ),
    NAVY(
        key = "navy",
        displayName = "남색",
        aliases = setOf("navy", "남색", "네이비", "indigo"),
        referenceHexes = setOf("3757D3", "373FD7", "3F43DB", "0000FF", "1A1AFF")
    ),
    PURPLE(
        key = "purple",
        displayName = "보라",
        aliases = setOf("purple", "보라", "보라색", "퍼플", "violet"),
        referenceHexes = setOf("876FFF", "8265EE")
    ),
    BROWN(
        key = "brown",
        displayName = "갈색",
        aliases = setOf("brown", "갈색", "브라운", "tan", "beige"),
        referenceHexes = setOf("8E5E2C", "8A4B16", "6B3E1C")
    ),
    PINK(
        key = "pink",
        displayName = "분홍",
        aliases = setOf("pink", "분홍", "핑크", "magenta", "rose", "hotpink"),
        referenceHexes = setOf("FF56A8", "FF43AC")
    ),
    WHITE(
        key = "white",
        displayName = "흰색",
        aliases = setOf("white", "흰색", "하양", "화이트", "ivory", "아이보리"),
        referenceHexes = setOf("F7F4F4", "F5F1F1", "FFFFFF", "F0F0F0")
    ),
    GRAY(
        key = "gray",
        displayName = "회색",
        aliases = setOf("gray", "grey", "회색", "그레이", "slategray"),
        referenceHexes = setOf("999999", "5C5C5C", "505050")
    ),
    BLACK(
        key = "black",
        displayName = "검정",
        aliases = setOf("black", "검정", "검은", "검은색", "블랙", "charcoal"),
        referenceHexes = setOf("0B0B0E", "0A0A12", "292929")
    );

    companion object {
        private val all = entries
        private val byKey = all.associateBy { it.key }

        fun byKey(key: String?): HoldDifficultyColor? {
            return key?.trim()?.lowercase(Locale.ROOT)?.let(byKey::get)
        }

        fun resolve(
            colorName: String?,
            colorHex: String?
        ): HoldDifficultyColor? {
            val normalizedName = colorName?.trim()?.lowercase(Locale.ROOT).orEmpty()
            if (normalizedName.isNotBlank()) {
                all.firstOrNull { normalizedName in it.aliases }?.let { return it }
            }

            val normalizedHex = normalizeHex(colorHex) ?: return null
            all.firstOrNull { normalizedHex in it.referenceHexes }?.let { return it }
            return resolveNearest(normalizedHex)
        }

        private fun normalizeHex(colorHex: String?): String? {
            return colorHex
                ?.trim()
                ?.removePrefix("#")
                ?.uppercase(Locale.ROOT)
                ?.takeIf { it.isNotBlank() }
        }

        private fun resolveNearest(normalizedHex: String): HoldDifficultyColor? {
            val targetRgb = parseRgb(normalizedHex) ?: return null
            val nearest = all
                .mapNotNull { color ->
                    color.referenceHexes
                        .mapNotNull(::parseRgb)
                        .minOfOrNull { referenceRgb ->
                            colorDistance(targetRgb, referenceRgb)
                        }
                        ?.let { color to it }
                }
                .minByOrNull { (_, distance) -> distance }
                ?: return null

            return nearest.first.takeIf { nearest.second <= MAX_COLOR_DISTANCE }
        }

        private fun parseRgb(normalizedHex: String): Triple<Int, Int, Int>? {
            val rgbHex = when (normalizedHex.length) {
                6 -> normalizedHex
                8 -> normalizedHex.takeLast(6)
                else -> return null
            }

            return runCatching {
                Triple(
                    rgbHex.substring(0, 2).toInt(16),
                    rgbHex.substring(2, 4).toInt(16),
                    rgbHex.substring(4, 6).toInt(16)
                )
            }.getOrNull()
        }

        private fun colorDistance(
            a: Triple<Int, Int, Int>,
            b: Triple<Int, Int, Int>
        ): Int {
            return ((a.first - b.first).toDouble().pow(2.0) +
                (a.second - b.second).toDouble().pow(2.0) +
                (a.third - b.third).toDouble().pow(2.0)).toInt()
        }

        private const val MAX_COLOR_DISTANCE = 12000
    }
}
