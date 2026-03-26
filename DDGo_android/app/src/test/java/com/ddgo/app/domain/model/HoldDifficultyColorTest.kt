package com.ddgo.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HoldDifficultyColorTest {

    @Test
    fun `resolve matches figma hold colors by exact hex`() {
        assertEquals(HoldDifficultyColor.RED, HoldDifficultyColor.resolve(colorName = null, colorHex = "#FF0000"))
        assertEquals(HoldDifficultyColor.SKYBLUE, HoldDifficultyColor.resolve(colorName = null, colorHex = "#6DCCF7"))
        assertEquals(HoldDifficultyColor.NAVY, HoldDifficultyColor.resolve(colorName = null, colorHex = "#3757D3"))
        assertEquals(HoldDifficultyColor.WHITE, HoldDifficultyColor.resolve(colorName = null, colorHex = "#F7F4F4"))
        assertEquals(HoldDifficultyColor.BLACK, HoldDifficultyColor.resolve(colorName = null, colorHex = "#0B0B0E"))
    }

    @Test
    fun `resolve keeps legacy aliases and nearby hex values aligned to shared color families`() {
        assertEquals(HoldDifficultyColor.SKYBLUE, HoldDifficultyColor.resolve(colorName = "하늘색", colorHex = null))
        assertEquals(HoldDifficultyColor.SKYBLUE, HoldDifficultyColor.resolve(colorName = null, colorHex = "#1FC4E2"))
        assertEquals(HoldDifficultyColor.NAVY, HoldDifficultyColor.resolve(colorName = "네이비", colorHex = null))
        assertEquals(HoldDifficultyColor.NAVY, HoldDifficultyColor.resolve(colorName = null, colorHex = "#3F43DB"))
        assertEquals(HoldDifficultyColor.BROWN, HoldDifficultyColor.resolve(colorName = null, colorHex = "#8A4B16"))
    }

    @Test
    fun `byKey returns null for unknown colors`() {
        assertNull(HoldDifficultyColor.byKey("unknown"))
    }
}
