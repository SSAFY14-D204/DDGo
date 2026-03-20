package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.model.ProcessedPoseDetectionFrame
import javax.inject.Inject

class DetectStablePersonObservationUseCase @Inject constructor() {
    operator fun invoke(
        processedFrames: List<ProcessedPoseDetectionFrame>,
        minConsecutiveDetections: Int = DEFAULT_MIN_CONSECUTIVE_DETECTIONS
    ): Long? {
        if (processedFrames.isEmpty()) return null
        if (minConsecutiveDetections <= 1) {
            return processedFrames.firstOrNull { frame -> frame.poseDetected }?.timestampMs
        }

        var streakCount = 0
        var streakStartTimestampMs: Long? = null

        processedFrames.sortedBy { frame -> frame.timestampMs }.forEach { frame ->
            if (!frame.poseDetected) {
                streakCount = 0
                streakStartTimestampMs = null
                return@forEach
            }

            if (streakCount == 0) {
                streakStartTimestampMs = frame.timestampMs
            }
            streakCount += 1

            if (streakCount >= minConsecutiveDetections) {
                return streakStartTimestampMs
            }
        }

        return null
    }

    companion object {
        const val DEFAULT_MIN_CONSECUTIVE_DETECTIONS = 5
    }
}
