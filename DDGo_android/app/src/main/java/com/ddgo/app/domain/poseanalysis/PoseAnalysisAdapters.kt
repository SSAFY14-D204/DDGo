package com.ddgo.app.domain.poseanalysis

import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.model.PoseLandmark

fun Pose.toPoseFrame(
    worldLandmarks: List<Landmark> = emptyList()
): PoseFrame = PoseFrame(
    frameTimeMs = frameTimeMs,
    landmarks = landmarks.map { landmark -> landmark.toPoseAnalysisLandmark() },
    worldLandmarks = worldLandmarks
)

fun PoseLandmark.toPoseAnalysisLandmark(): Landmark = Landmark(
    index = index,
    x = x.toDouble(),
    y = y.toDouble(),
    z = z.toDouble(),
    visibility = visibility?.toDouble(),
    presence = presence?.toDouble()
)
