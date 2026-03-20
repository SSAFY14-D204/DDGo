package com.ddgo.app.domain.poseanalysis

private val HAND_LANDMARKS = (15..22).toList()
private val TORSO_LANDMARKS = listOf(11, 12, 23, 24)
private val FOOT_LANDMARKS = (27..32).toList()

internal fun getLandmarks(frame: PoseFrame, source: LandmarkSource): List<Landmark> = when (source) {
    LandmarkSource.WORLD_LANDMARKS -> frame.worldLandmarks
    LandmarkSource.LANDMARKS -> frame.landmarks
}

internal fun buildLandmarkMap(
    landmarks: List<Landmark>,
    minVisibility: Double,
    minPresence: Double
): Map<Int, Landmark> = landmarks
    .asSequence()
    .filter { landmark ->
        (landmark.visibility == null || landmark.visibility >= minVisibility) &&
            (landmark.presence == null || landmark.presence >= minPresence)
    }
    .associateBy { landmark -> landmark.index }

internal fun computeGroupHeight(
    landmarkMap: Map<Int, Landmark>,
    indices: List<Int>,
    landmarkSource: LandmarkSource
): Double? {
    val yValues = indices.mapNotNull { index -> landmarkMap[index]?.y }
    if (yValues.isEmpty()) return null
    val averageY = yValues.average()
    return when (landmarkSource) {
        LandmarkSource.WORLD_LANDMARKS -> -averageY
        LandmarkSource.LANDMARKS -> 1.0 - averageY
    }
}

internal fun computeTorsoScale(landmarkMap: Map<Int, Landmark>): Double? {
    if (!TORSO_LANDMARKS.all(landmarkMap::containsKey)) return null
    val shoulderMidY = (landmarkMap.getValue(11).y + landmarkMap.getValue(12).y) / 2.0
    val hipMidY = (landmarkMap.getValue(23).y + landmarkMap.getValue(24).y) / 2.0
    return kotlin.math.abs(hipMidY - shoulderMidY)
}

internal fun computeTorsoOrientation(landmarkMap: Map<Int, Landmark>): TorsoOrientation {
    if (!TORSO_LANDMARKS.all(landmarkMap::containsKey)) return TorsoOrientation.UNKNOWN

    val leftShoulderX = landmarkMap.getValue(11).x
    val rightShoulderX = landmarkMap.getValue(12).x
    val leftHipX = landmarkMap.getValue(23).x
    val rightHipX = landmarkMap.getValue(24).x

    val shouldersFront = leftShoulderX > rightShoulderX
    val hipsFront = leftHipX > rightHipX
    val shouldersBack = leftShoulderX < rightShoulderX
    val hipsBack = leftHipX < rightHipX

    return when {
        shouldersFront && hipsFront -> TorsoOrientation.FRONT
        shouldersBack && hipsBack -> TorsoOrientation.BACK
        else -> TorsoOrientation.MIXED
    }
}

internal fun buildBodyPartHeightsPoint(
    frame: PoseFrame,
    config: HandPeakConfig
): FrameBodyPartHeights {
    val sourceLandmarkMap = buildLandmarkMap(
        landmarks = getLandmarks(frame, config.landmarkSource),
        minVisibility = config.minVisibility,
        minPresence = config.minPresence
    )
    val landmarkMap2d = buildLandmarkMap(
        landmarks = frame.landmarks,
        minVisibility = config.minVisibility,
        minPresence = config.minPresence
    )

    return FrameBodyPartHeights(
        frameTimeMs = frame.frameTimeMs,
        handHeight = computeGroupHeight(sourceLandmarkMap, HAND_LANDMARKS, config.landmarkSource),
        torsoHeight = computeGroupHeight(sourceLandmarkMap, TORSO_LANDMARKS, config.landmarkSource),
        footHeight = computeGroupHeight(sourceLandmarkMap, FOOT_LANDMARKS, config.landmarkSource),
        torsoScale = computeTorsoScale(landmarkMap2d),
        torsoOrientation = computeTorsoOrientation(landmarkMap2d)
    )
}
