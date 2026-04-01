package com.ddgo.shared.contract

object DlPaths {
    const val DATA_RECORDING_STATE = "/ddigo/recording_state"
    const val DATA_LIVE_HR = "/ddigo/live_hr"
    const val DATA_WATCH_SESSION_STATUS = "/ddigo/watch_session_status"

    const val MSG_RECORDING_START = "/ddigo/msg/recording_start"
    const val MSG_RECORDING_STOP = "/ddigo/msg/recording_stop"
    const val MSG_MEASUREMENT_PREPARE_START = "/ddigo/msg/measurement_prepare_start"
    const val MSG_MEASUREMENT_PREPARE_STOP = "/ddigo/msg/measurement_prepare_stop"
    const val MSG_OPEN_APP = "/ddigo/msg/open_app"
    const val MSG_ALERT = "/ddigo/msg/alert"
    const val MSG_ACK = "/ddigo/msg/ack"

    const val CAPABILITY_WATCH = "ddigo_watch"
}
