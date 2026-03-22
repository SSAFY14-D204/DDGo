package com.ddgo.wear.service

import com.ddgo.wear.data.RecordingStateSyncProcessor
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class RecordingStateListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        RecordingStateSyncProcessor.handleDataChanged(this, dataEvents)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        RecordingStateSyncProcessor.handleMessage(
            context = this,
            path = messageEvent.path,
            payload = messageEvent.data
        )
    }
}
