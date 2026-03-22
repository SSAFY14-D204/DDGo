package com.ddgo.app.data.wear

import com.ddgo.shared.model.HeartRateSnapshot
import com.ddgo.shared.model.WatchSessionStatus

data class WatchRuntimeSyncSnapshot(
    val heartRateSnapshot: HeartRateSnapshot? = null,
    val watchSessionStatus: WatchSessionStatus? = null,
    val isWatchConnected: Boolean = false,
    val lastConnectionCheckedAt: Long? = null,
    val lastAlertReceivedAt: Long? = null
)
