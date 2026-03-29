package com.ddgo.app.feature.climbing.upload

import org.junit.Assert.assertEquals
import org.junit.Test

class UploadViewModelRotationTest {

    @Test
    fun `normalize rotation keeps supported right angle values`() {
        assertEquals(0, normalizeVideoRotationDegrees(0))
        assertEquals(90, normalizeVideoRotationDegrees(90))
        assertEquals(180, normalizeVideoRotationDegrees(180))
        assertEquals(270, normalizeVideoRotationDegrees(270))
    }

    @Test
    fun `normalize rotation wraps negative and overflow values`() {
        assertEquals(270, normalizeVideoRotationDegrees(-90))
        assertEquals(90, normalizeVideoRotationDegrees(450))
    }

    @Test
    fun `normalize rotation falls back to zero for unsupported degrees`() {
        assertEquals(0, normalizeVideoRotationDegrees(45))
        assertEquals(0, normalizeVideoRotationDegrees(135))
    }
}
