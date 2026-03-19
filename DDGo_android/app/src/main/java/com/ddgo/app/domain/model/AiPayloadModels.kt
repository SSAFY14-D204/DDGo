package com.ddgo.app.domain.model

/**
 * AI payload source metadata shared by pose-sequence export and AI analysis requests.
 */
data class AiPayloadSource(
    val uri: String,
    val displayName: String? = null,
    val mimeType: String? = null,
    val path: String? = null,
    val legacySourceFile: String? = null,
    val videoUri: String = uri,
    val generator: String = "SequentialPoseVideoAnalyzer",
    val exportedAtIso: String = ""
)

/**
 * Basic user profile payload for AI analysis.
 */
data class AiUserProfile(
    val sex: String? = null,
    val heightCm: Float? = null,
    val weightKg: Float? = null,
    val wingspanCm: Float? = null
)

/**
 * Calibration-friendly body segment lengths for the physics pipeline.
 */
data class AiCalibrationCompat(
    val upperArmM: Double,
    val forearmM: Double,
    val thighM: Double,
    val shinM: Double,
    val shoulderWidthM: Double,
    val wingspanM: Double,
    val leftUpperArmM: Double,
    val rightUpperArmM: Double,
    val leftForearmM: Double,
    val rightForearmM: Double,
    val leftThighM: Double,
    val rightThighM: Double,
    val leftShinM: Double,
    val rightShinM: Double,
    val bodyMassKg: Double? = null
)

/**
 * User body payload for AI analysis.
 */
data class AiUserBodyProfile(
    val userProfile: AiUserProfile,
    val calibrationCompat: AiCalibrationCompat
)
