package com.ddgo.app.feature.climbing.shared.navigation

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ddgo.app.feature.climbing.shared.model.ClimbingRecordThumbnailFrame
import com.ddgo.app.feature.climbing.shared.model.ClimbingRecordedAttemptDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClimbingUploadEntryArgsTest {

    @Test
    fun draft_converts_to_upload_entry_args_with_recorded_uri_and_realtime_session() {
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
    fun build_and_parse_upload_route_preserves_encoded_values() {
        val route = buildClimbingUploadRoute(
            baseRoute = "upload/attempt",
            recordedVideoUriArgName = "recordedVideoUri",
            realtimeSessionIdArgName = "realtimeSessionId",
            entryArgs = ClimbingUploadEntryArgs(
                recordedVideoUri = "content://media/external/video/media/42?name=a b.mp4",
                realtimeSessionId = "rt session?42"
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
        assertTrue(route.contains("realtimeSessionId="))
        assertFalse(route.contains("rt session?42"))
        assertEquals(
            "content://media/external/video/media/42?name=a b.mp4",
            parsedArgs.recordedVideoUri
        )
        assertEquals("rt session?42", parsedArgs.realtimeSessionId)
    }

    @Test
    fun empty_entry_args_keep_base_route_without_query() {
        val route = buildClimbingUploadRoute(
            baseRoute = "upload/attempt",
            recordedVideoUriArgName = "recordedVideoUri",
            realtimeSessionIdArgName = "realtimeSessionId"
        )

        assertEquals("upload/attempt", route)
        assertFalse(ClimbingUploadEntryArgs().hasAnyValue)
    }

    @Test
    fun parse_normalizes_blank_encoded_values_back_to_null() {
        val parsedArgs = Bundle().apply {
            putString("recordedVideoUri", "")
            putString("realtimeSessionId", "rt-session-42")
        }.toClimbingUploadEntryArgs(
            recordedVideoUriArgName = "recordedVideoUri",
            realtimeSessionIdArgName = "realtimeSessionId"
        )

        assertEquals(null, parsedArgs.recordedVideoUri)
        assertEquals("rt-session-42", parsedArgs.realtimeSessionId)
        assertTrue(parsedArgs.hasAnyValue)
    }
}
