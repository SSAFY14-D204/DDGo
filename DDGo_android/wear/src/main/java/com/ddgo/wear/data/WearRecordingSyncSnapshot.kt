package com.ddgo.wear.data

import com.ddgo.shared.model.RecordingState

data class WearRecordingSyncSnapshot(
    val recordingState: RecordingState? = null,
    val lastEventSource: RecordingStateEventSource = RecordingStateEventSource.NONE,
    val ignoredEventCount: Int = 0,
    val lastAppliedAt: Long? = null
) {
    val isRecording: Boolean
        get() = recordingState?.isRecording == true
}

enum class RecordingStateEventSource {
    NONE,
    DATA_ITEM,
    MESSAGE
}
