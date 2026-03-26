package com.ddgo.app.feature.climbing.upload

import org.junit.Assert.assertEquals
import org.junit.Test

class UploadHoldColorMappingTest {

    @Test
    fun `blue aliases resolve to skyblue key and display name`() {
        assertEquals("skyblue", resolveHoldColorKey(colorName = "blue", colorHex = null))
        assertEquals("skyblue", resolveHoldColorKey(colorName = "파랑", colorHex = null))
        assertEquals("하늘색", resolveHoldColorDisplayName(colorName = "blue", colorHex = null))
    }

    @Test
    fun `legacy figma and app hex values resolve to expected keys`() {
        assertEquals("skyblue", resolveHoldColorKey(colorName = null, colorHex = "#4396FB"))
        assertEquals("navy", resolveHoldColorKey(colorName = null, colorHex = "#3757D3"))
        assertEquals("white", resolveHoldColorKey(colorName = null, colorHex = "#F7F4F4"))
    }
}
