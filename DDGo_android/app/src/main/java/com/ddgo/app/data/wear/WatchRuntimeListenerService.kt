package com.ddgo.app.data.wear

import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class WatchRuntimeListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        WatchRuntimeSyncProcessor.handleDataChanged(this, dataEvents)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        WatchRuntimeSyncProcessor.handleMessage(
            context = this,
            path = messageEvent.path,
            payload = messageEvent.data
        )
    }
}
