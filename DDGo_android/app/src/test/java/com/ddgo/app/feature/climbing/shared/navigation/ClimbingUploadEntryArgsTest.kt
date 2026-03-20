package com.ddgo.app.feature.climbing.shared.navigation

import android.os.Bundle
import com.ddgo.app.feature.climbing.shared.model.ClimbingRecordThumbnailFrame
import com.ddgo.app.feature.climbing.shared.model.ClimbingRecordedAttemptDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClimbingUploadEntryArgsTest {

    @Test
    fun `draft converts to upload entry args with recorded uri and realtime session`() {
        val draft = ClimbingRecordedAttemptDraft(
            videoUri = "file:///recorded_attempt.mp4",
            thumbnailFrame = ClimbingRecordThumbnailFrame(
                frameIndex = 12,
                timestampMs = 800L,
                width = 720,
                height = 1280,
                rotationDegrees = 90
            ),
            realtimeSessionId = "rt-session-42",
            frameWidthPx = 720,
            frameHeightPx = 1280
        )

        val args = draft.toClimbingUploadEntryArgs()

        assertEquals("file:///recorded_attempt.mp4", args.recordedVideoUri)
        assertEquals("rt-session-42", args.realtimeSessionId)
        assertTrue(args.hasAnyValue)
    }

    @Test
    fun `build and parse upload route preserves encoded values`() {
        val route = buildClimbingUploadRoute(
            baseRoute = "upload/attempt",
            recordedVideoUriArgName = "recordedVideoUri",
            realtimeSessionIdArgName = "realtimeSessionId",
            entryArgs = ClimbingUploadEntryArgs(
                recordedVideoUri = "content://media/external/video/media/42?name=a b.mp4",
                realtimeSessionId = "rt session/42"
            )
        )

        val parsedArgs = Bundle().apply {
            val query = route.substringAfter("?", missingDelimiterValue = "")
            query.split("&")
                .filter { it.contains("=") }
                .forEach { pair ->
                    val (key, value) = pair.split("=", limit = 2)
                    putString(key, value)
                }
        }.toClimbingUploadEntryArgs(
            recordedVideoUriArgName = "recordedVideoUri",
            realtimeSessionIdArgName = "realtimeSessionId"
        )

        assertTrue(route.startsWith("upload/attempt?"))
        assertEquals(
            "content://media/external/video/media/42?name=a b.mp4",
            parsedArgs.recordedVideoUri
        )
        assertEquals("rt session/42", parsedArgs.realtimeSessionId)
    }

    @Test
    fun `empty entry args keep base route without query`() {
        val route = buildClimbingUploadRoute(
            baseRoute = "upload/attempt",
            recordedVideoUriArgName = "recordedVideoUri",
            realtimeSessionIdArgName = "realtimeSessionId"
        )

        assertEquals("upload/attempt", route)
        assertFalse(ClimbingUploadEntryArgs().hasAnyValue)
    }
}
