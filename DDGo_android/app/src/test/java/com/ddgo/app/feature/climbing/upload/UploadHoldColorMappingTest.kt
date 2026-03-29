package com.ddgo.app.feature.climbing.upload

import com.ddgo.app.core.ui.tokens.DdgoHoldColorPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UploadHoldColorMappingTest {

    @Test
    fun `upload hold colors resolve exact palette keys by name`() {
        assertEquals("red", resolveHoldColorKey(colorName = "red", colorHex = null))
        assertEquals("skyblue", resolveHoldColorKey(colorName = "skyblue", colorHex = null))
        assertEquals("blue", resolveHoldColorKey(colorName = "blue", colorHex = null))
        assertEquals("navy", resolveHoldColorKey(colorName = "navy", colorHex = null))
        assertEquals("white", resolveHoldColorKey(colorName = "white", colorHex = null))
    }

    @Test
    fun `upload hold colors resolve exact palette hex values`() {
        assertEquals("red", resolveHoldColorKey(colorName = null, colorHex = "#${DdgoHoldColorPalette.Red.referenceHexes.first()}"))
        assertEquals("skyblue", resolveHoldColorKey(colorName = null, colorHex = "#${DdgoHoldColorPalette.Sky.referenceHexes.first()}"))
        assertEquals("blue", resolveHoldColorKey(colorName = null, colorHex = "#${DdgoHoldColorPalette.Blue.referenceHexes.first()}"))
        assertEquals("navy", resolveHoldColorKey(colorName = null, colorHex = "#${DdgoHoldColorPalette.Navy.referenceHexes.first()}"))
        assertEquals("white", resolveHoldColorKey(colorName = null, colorHex = "#${DdgoHoldColorPalette.Ivory.referenceHexes.first()}"))
    }

    @Test
    fun `classifier bridge preserves legacy upload mapping`() {
        assertEquals("navy", resolveClassifierHoldColor(colorName = "blue", colorHex = null))
        assertEquals("skyblue", resolveClassifierHoldColor(colorName = "skyblue", colorHex = null))
        assertEquals("pink", resolveClassifierHoldColor(colorName = "pink", colorHex = null))
    }

    @Test
    fun `display name falls back to original value when mapping fails`() {
        assertEquals("mystery", resolveHoldColorDisplayName(colorName = "mystery", colorHex = null))
        assertNull(resolveHoldColorKey(colorName = "mystery", colorHex = "#123456"))
    }
}
