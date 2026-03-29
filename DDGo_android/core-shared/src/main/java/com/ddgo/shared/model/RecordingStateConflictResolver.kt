package com.ddgo.shared.model

object RecordingStateConflictResolver {
    fun shouldApply(
        current: RecordingState?,
        incoming: RecordingState
    ): Boolean {
        if (current == null) {
            return true
        }
        return incoming.updatedAt > current.updatedAt
    }
}
