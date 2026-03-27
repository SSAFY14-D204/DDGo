package com.ddgo.app.feature.climbing.upload

import com.ddgo.app.data.mapper.toPoseSequenceDto
import com.ddgo.app.data.remote.pose.PoseSequenceDto
import com.ddgo.app.domain.model.AiAnalysisFallbackReason
import com.ddgo.app.domain.model.AiAnalysisMode
import com.ddgo.app.domain.model.AiAnalysisResult
import com.ddgo.app.domain.model.AiAnalysisVideoMetadata
import com.ddgo.app.domain.model.AiCruxCandidate
import com.ddgo.app.domain.model.AiCruxResult
import com.ddgo.app.domain.model.AiCruxSegment
import com.ddgo.app.domain.model.GymGrade
import com.ddgo.app.domain.model.GymSummary
import com.ddgo.app.domain.model.Hold
import com.ddgo.app.domain.model.NearbyPlace
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.model.PoseLandmark
import com.ddgo.app.domain.model.PosePixelPoint
import com.ddgo.app.domain.model.PoseWorldPoint
import com.ddgo.app.domain.model.ResolvedGym
import com.ddgo.app.domain.model.UploadedAttemptVideo
import com.ddgo.app.domain.usecase.AttemptHoldReachResult
import com.ddgo.app.domain.usecase.HoldNumbered
import com.ddgo.app.domain.usecase.HoldRole
import com.ddgo.app.domain.usecase.OverallHoldReachSummary
import com.ddgo.app.domain.usecase.PolygonHoldContact
import com.ddgo.app.domain.usecase.PolygonHoldContactDebugResult
import com.ddgo.app.domain.usecase.PolygonHoldContactFrame
import com.ddgo.app.domain.usecase.PolygonLimbFrameState
import com.ddgo.app.domain.usecase.PolygonTrackedLimb
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

@Serializable
enum class UploadRecoveryRoute {
    ATTEMPT_UPLOAD,
    CHALLENGE_CREATE,
    CHALLENGE_HOLD,
    HOLD_SELECT,
    ADDITIONAL_UPLOAD,
    ANALYSIS_LOADING,
    ATTEMPT_RESULT,
    FINAL_ANALYSIS,
    CHALLENGE_FINAL_ANALYSIS,
    REALTIME_SETUP,
    REALTIME_HOLD,
    REALTIME_HOLD_SELECT,
    REALTIME_ANALYSIS_LOADING,
    REALTIME_ATTEMPT_RESULT
}

@Serializable
enum class UploadRecoveryCreateStep {
    GYM_NAME,
    LEVEL,
    COLOR
}

@Serializable
enum class UploadRecoveryHoldSelectionPhase {
    START,
    END
}

@Serializable
enum class UploadRecoveryAnalysisPhase {
    ATTEMPT_RESULT_PREPARATION,
    FINAL_ANALYSIS_PREPARATION
}

@Serializable
data class UploadRecoveryBoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

@Serializable
data class UploadRecoverySnapshotPayload(
    val userId: Long,
    val challengeId: Long? = null,
    val entryIntent: String,
    val lastRoute: UploadRecoveryRoute,
    val lastKnownDoneAttemptCount: Int = 0,
    val recoveredAt: Long,
    val managedPrimaryPlaybackUri: String? = null,
    val managedAdditionalPlaybackUris: List<String> = emptyList(),
    val managedAttemptOnlyPlaybackUris: List<String> = emptyList(),
    val gymId: Int? = null,
    val gymGradeId: Long? = null,
    val gymName: String = "",
    val problemColor: String = "",
    val gradeLabel: String? = null,
    val selectedHoldColorKey: String? = null,
    val searchQuery: String? = null,
    val createStep: UploadRecoveryCreateStep? = null,
    val realtimeSetupStep: String? = null,
    val analysisLoadingPhase: UploadRecoveryAnalysisPhase? = null,
    val holdSelectionPhase: UploadRecoveryHoldSelectionPhase? = null,
    val selectedStartHoldBoundingBox: UploadRecoveryBoundingBox? = null,
    val selectedEndHoldBoundingBox: UploadRecoveryBoundingBox? = null,
    val currentAttemptIndex: Int = 0,
    val isAttemptOnlyMode: Boolean = false,
    val startedAt: String? = null,
    val createdAt: String? = null,
    val selectedNearbyPlace: UploadRecoveryNearbyPlaceDto? = null,
    val resolvedGym: UploadRecoveryResolvedGymDto? = null,
    val lastSearchLatitude: Double? = null,
    val lastSearchLongitude: Double? = null,
    val publishedAttemptResultSession: UploadRecoveryPublishedAttemptResultSessionDto? = null
)

@Serializable
data class UploadRecoveryNearbyPlaceDto(
    val externalPlaceId: String,
    val placeName: String,
    val addressName: String? = null,
    val roadAddressName: String? = null,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Int? = null
)

@Serializable
data class UploadRecoveryResolvedGymDto(
    val matched: Boolean,
    val gymId: Int,
    val gradeSource: String,
    val matchStatus: String,
    val needsReview: Boolean,
    val gym: UploadRecoveryGymSummaryDto,
    val grades: List<UploadRecoveryGymGradeDto>
)

@Serializable
data class UploadRecoveryGymSummaryDto(
    val id: Int,
    val displayName: String,
    val region: String? = null,
    val logoBucket: String? = null,
    val logoObjectKey: String? = null,
    val brandLogoBucket: String? = null,
    val brandLogoObjectKey: String? = null
)

@Serializable
data class UploadRecoveryGymGradeDto(
    val gymGradeId: Int,
    val colorName: String,
    val sortOrder: Int,
    val colorHex: String? = null,
    val gradeLabel: String? = null
)

@Serializable
data class UploadRecoveryPublishedAttemptResultSessionDto(
    val resultPlaybackUris: List<String>,
    val uploadedAttemptVideos: List<UploadRecoveryUploadedAttemptVideoDto>,
    val currentAttemptIndex: Int,
    val attemptAlignedHoldSets: List<UploadRecoveryAttemptAlignedHoldSetDto>,
    val holdReachResults: List<UploadRecoveryAttemptHoldReachResultDto>,
    val attemptAiAnalysisResults: List<UploadRecoveryAiAnalysisResultDto?>,
    val attemptPoseDtos: List<PoseSequenceDto>,
    val attemptAnalyzedPoses: List<List<UploadRecoveryPoseDto>>,
    val attemptPolygonHoldContactDebugResults: List<UploadRecoveryPolygonHoldContactDebugResultDto>,
    val overallHoldReachSummary: UploadRecoveryOverallHoldReachSummaryDto?
)

@Serializable
data class UploadRecoveryUploadedAttemptVideoDto(
    val challengeId: Long,
    val attemptId: Long,
    val attemptNo: Int,
    val videoUri: String,
    val objectKey: String
)

@Serializable
data class UploadRecoveryAttemptAlignedHoldSetDto(
    val playbackUri: String,
    val frameWidthPx: Int,
    val frameHeightPx: Int,
    val mode: String,
    val confidence: Float,
    val matchedHoldCount: Int,
    val warpOnlyHoldCount: Int,
    val alignedHolds: List<UploadRecoveryHoldNumberedDto>,
    val rawCropBounds: UploadRecoveryRawVerticalCropBoundsDto? = null,
    val debugSummary: String
)

@Serializable
data class UploadRecoveryRawVerticalCropBoundsDto(
    val topFraction: Float,
    val bottomFraction: Float
)

@Serializable
data class UploadRecoveryHoldNumberedDto(
    val hold: UploadRecoveryHoldDto,
    val progress: Float,
    val axisDistance: Float,
    val role: String
)

@Serializable
data class UploadRecoveryHoldDto(
    val holdNo: Int = 0,
    val boundingBox: UploadRecoveryBoundingBox,
    val confidence: Float,
    val polygon: List<UploadRecoveryHoldPointDto> = emptyList(),
    val colorLabel: String = "unknown",
    val colorScore: Float = 0f
)

@Serializable
data class UploadRecoveryHoldPointDto(
    val x: Float,
    val y: Float
)

@Serializable
data class UploadRecoveryAttemptHoldReachResultDto(
    val highestReachedHold: UploadRecoveryHoldNumberedDto? = null,
    val highestReachedHoldNo: Int,
    val highestReachedFrameTimeMs: Long? = null,
    val totalHoldCount: Int,
    val contactedHoldNos: Set<Int>,
    val reachedRatio: Float,
    val completedWithBothHandsOnEndHold: Boolean = false
)

@Serializable
data class UploadRecoveryOverallHoldReachSummaryDto(
    val attempts: List<UploadRecoveryAttemptHoldReachResultDto>,
    val averageHighestReachedHoldNo: Float,
    val roundedAverageHighestReachedHoldNo: Int,
    val totalHoldCount: Int,
    val averageReachedRatio: Float
)

@Serializable
data class UploadRecoveryPolygonHoldContactDebugResultDto(
    val frames: List<UploadRecoveryPolygonHoldContactFrameDto>,
    val highestReachedHoldNo: Int,
    val highestReachedFrameTimeMs: Long? = null,
    val contactedHoldNos: Set<Int>
)

@Serializable
data class UploadRecoveryPolygonHoldContactFrameDto(
    val frameTimeMs: Long,
    val limbStates: List<UploadRecoveryPolygonLimbFrameStateDto>,
    val activeContacts: List<UploadRecoveryPolygonHoldContactDto>
)

@Serializable
data class UploadRecoveryPolygonLimbFrameStateDto(
    val limb: String,
    val state: String,
    val activeHoldNo: Int? = null,
    val candidateHoldNo: Int? = null,
    val distancePx: Float? = null,
    val speedPxPerSec: Float,
    val transition: String? = null,
    val insidePolygon: Boolean? = null,
    val contactPointNormalized: UploadRecoveryHoldPointDto? = null
)

@Serializable
data class UploadRecoveryPolygonHoldContactDto(
    val hold: UploadRecoveryHoldNumberedDto,
    val limb: String,
    val state: String,
    val insidePolygon: Boolean,
    val distancePx: Float,
    val speedPxPerSec: Float,
    val contactPointNormalized: UploadRecoveryHoldPointDto? = null
)

@Serializable
data class UploadRecoveryAiAnalysisResultDto(
    val mode: String,
    val requestedMode: String,
    val schemaVersion: String,
    val videoMetadata: UploadRecoveryAiAnalysisVideoMetadataDto? = null,
    val timingsSeconds: Map<String, Double>,
    val correctionSummary: JsonObject? = null,
    val cruxResult: UploadRecoveryAiCruxResultDto,
    val holdStateSummary: JsonObject? = null,
    val physicsSummary: JsonObject? = null,
    val physicsPipelineBenchmarkTimingsSeconds: JsonObject? = null,
    val physicsResult: JsonObject? = null,
    val fallbackReason: String? = null,
    val rawResponse: JsonObject
)

@Serializable
data class UploadRecoveryAiAnalysisVideoMetadataDto(
    val frameWidth: Int,
    val frameHeight: Int,
    val fps: Float? = null,
    val totalFrames: Int,
    val processedFrames: Int,
    val frameStep: Int
)

@Serializable
data class UploadRecoveryAiCruxResultDto(
    val candidateCount: Int,
    val topCandidates: List<UploadRecoveryAiCruxCandidateDto>,
    val allCandidates: List<UploadRecoveryAiCruxCandidateDto>
)

@Serializable
data class UploadRecoveryAiCruxCandidateDto(
    val holdId: Int,
    val segmentCount: Int,
    val engagementCount: Int,
    val totalActiveTimeSeconds: Double,
    val longestContinuousDwellSeconds: Double,
    val reasonTags: List<String>,
    val bestSegment: UploadRecoveryAiCruxSegmentDto? = null,
    val fastCruxScore: Double? = null,
    val physicsCruxScore: Double? = null
)

@Serializable
data class UploadRecoveryAiCruxSegmentDto(
    val startFrame: Int,
    val endFrame: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val durationSeconds: Double,
    val dominantLimbs: List<String>,
    val dominantModes: List<String>,
    val meanTotalBodyLoad: Double? = null,
    val meanCoreLoad: Double? = null,
    val meanNegativeMarginCm: Double? = null,
    val meanLoadShiftProxy: Double? = null,
    val meanConfidenceWeight: Double? = null,
    val okFraction: Double? = null,
    val segmentCruxScore: Double? = null,
    val reasonTags: List<String> = emptyList()
)

@Serializable
data class UploadRecoveryPoseDto(
    val frameTimeMs: Long,
    val landmarks: List<UploadRecoveryPoseLandmarkDto>,
    val landmarksPx: Map<String, UploadRecoveryPosePixelPointDto> = emptyMap(),
    val worldLandmarksSample: Map<String, UploadRecoveryPoseWorldPointDto> = emptyMap()
)

@Serializable
data class UploadRecoveryPoseLandmarkDto(
    val index: Int,
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float? = null,
    val presence: Float? = null
)

@Serializable
data class UploadRecoveryPosePixelPointDto(
    val x: Float,
    val y: Float
)

@Serializable
data class UploadRecoveryPoseWorldPointDto(
    val x: Float,
    val y: Float,
    val z: Float
)

internal fun NearbyPlace.toRecoveryDto(): UploadRecoveryNearbyPlaceDto =
    UploadRecoveryNearbyPlaceDto(
        externalPlaceId = externalPlaceId,
        placeName = placeName,
        addressName = addressName,
        roadAddressName = roadAddressName,
        latitude = latitude,
        longitude = longitude,
        distanceMeters = distanceMeters
    )

internal fun UploadRecoveryNearbyPlaceDto.toDomain(): NearbyPlace =
    NearbyPlace(
        externalPlaceId = externalPlaceId,
        placeName = placeName,
        addressName = addressName,
        roadAddressName = roadAddressName,
        latitude = latitude,
        longitude = longitude,
        distanceMeters = distanceMeters
    )

private fun GymGrade.toRecoveryDto(): UploadRecoveryGymGradeDto =
    UploadRecoveryGymGradeDto(
        gymGradeId = gymGradeId,
        colorName = colorName,
        sortOrder = sortOrder,
        colorHex = colorHex,
        gradeLabel = gradeLabel
    )

internal fun UploadRecoveryGymGradeDto.toDomain(): GymGrade =
    GymGrade(
        gymGradeId = gymGradeId,
        colorName = colorName,
        sortOrder = sortOrder,
        colorHex = colorHex,
        gradeLabel = gradeLabel
    )

internal fun ResolvedGym.toRecoveryDto(): UploadRecoveryResolvedGymDto =
    UploadRecoveryResolvedGymDto(
        matched = matched,
        gymId = gymId,
        gradeSource = gradeSource,
        matchStatus = matchStatus,
        needsReview = needsReview,
        gym = UploadRecoveryGymSummaryDto(
            id = gym.id,
            displayName = gym.displayName,
            region = gym.region,
            logoBucket = gym.logoBucket,
            logoObjectKey = gym.logoObjectKey,
            brandLogoBucket = gym.brandLogoBucket,
            brandLogoObjectKey = gym.brandLogoObjectKey
        ),
        grades = grades.map(GymGrade::toRecoveryDto)
    )

internal fun UploadRecoveryResolvedGymDto.toDomain(): ResolvedGym =
    ResolvedGym(
        matched = matched,
        gymId = gymId,
        gradeSource = gradeSource,
        matchStatus = matchStatus,
        needsReview = needsReview,
        gym = GymSummary(
            id = gym.id,
            displayName = gym.displayName,
            region = gym.region,
            logoBucket = gym.logoBucket,
            logoObjectKey = gym.logoObjectKey,
            brandLogoBucket = gym.brandLogoBucket,
            brandLogoObjectKey = gym.brandLogoObjectKey
        ),
        grades = grades.map(UploadRecoveryGymGradeDto::toDomain)
    )

internal fun Hold.BoundingBox.toRecoveryDto(): UploadRecoveryBoundingBox =
    UploadRecoveryBoundingBox(
        left = left,
        top = top,
        right = right,
        bottom = bottom
    )

internal fun UploadRecoveryBoundingBox.toDomainBoundingBox(): Hold.BoundingBox =
    Hold.BoundingBox(
        left = left,
        top = top,
        right = right,
        bottom = bottom
    )

private fun Hold.toRecoveryDto(): UploadRecoveryHoldDto =
    UploadRecoveryHoldDto(
        holdNo = holdNo,
        boundingBox = boundingBox.toRecoveryDto(),
        confidence = confidence,
        polygon = polygon.map { UploadRecoveryHoldPointDto(x = it.x, y = it.y) },
        colorLabel = colorLabel,
        colorScore = colorScore
    )

private fun UploadRecoveryHoldDto.toDomain(): Hold =
    Hold(
        holdNo = holdNo,
        boundingBox = boundingBox.toDomainBoundingBox(),
        confidence = confidence,
        polygon = polygon.map { Hold.Point(x = it.x, y = it.y) },
        colorLabel = colorLabel,
        colorScore = colorScore
    )

private fun HoldNumbered.toRecoveryDto(): UploadRecoveryHoldNumberedDto =
    UploadRecoveryHoldNumberedDto(
        hold = hold.toRecoveryDto(),
        progress = progress,
        axisDistance = axisDistance,
        role = role.name
    )

private fun UploadRecoveryHoldNumberedDto.toDomain(): HoldNumbered =
    HoldNumbered(
        hold = hold.toDomain(),
        progress = progress,
        axisDistance = axisDistance,
        role = HoldRole.valueOf(role)
    )

private fun UploadedAttemptVideo.toRecoveryDto(): UploadRecoveryUploadedAttemptVideoDto =
    UploadRecoveryUploadedAttemptVideoDto(
        challengeId = challengeId,
        attemptId = attemptId,
        attemptNo = attemptNo,
        videoUri = videoUri,
        objectKey = objectKey
    )

private fun UploadRecoveryUploadedAttemptVideoDto.toDomain(): UploadedAttemptVideo =
    UploadedAttemptVideo(
        challengeId = challengeId,
        attemptId = attemptId,
        attemptNo = attemptNo,
        videoUri = videoUri,
        objectKey = objectKey
    )

internal fun Pose.toRecovery(): UploadRecoveryPoseDto =
    UploadRecoveryPoseDto(
        frameTimeMs = frameTimeMs,
        landmarks = landmarks.map(PoseLandmark::toRecovery),
        landmarksPx = landmarksPx.mapValues { (_, point) ->
            UploadRecoveryPosePixelPointDto(x = point.x, y = point.y)
        },
        worldLandmarksSample = worldLandmarksSample.mapValues { (_, point) ->
            UploadRecoveryPoseWorldPointDto(x = point.x, y = point.y, z = point.z)
        }
    )

internal fun UploadRecoveryPoseDto.toDomain(): Pose =
    Pose(
        frameTimeMs = frameTimeMs,
        landmarks = landmarks.map(UploadRecoveryPoseLandmarkDto::toDomain),
        landmarksPx = landmarksPx.mapValues { (_, point) ->
            PosePixelPoint(x = point.x, y = point.y)
        },
        worldLandmarksSample = worldLandmarksSample.mapValues { (_, point) ->
            PoseWorldPoint(x = point.x, y = point.y, z = point.z)
        }
    )

private fun PoseLandmark.toRecovery(): UploadRecoveryPoseLandmarkDto =
    UploadRecoveryPoseLandmarkDto(
        index = index,
        x = x,
        y = y,
        z = z,
        visibility = visibility,
        presence = presence
    )

private fun UploadRecoveryPoseLandmarkDto.toDomain(): PoseLandmark =
    PoseLandmark(
        index = index,
        x = x,
        y = y,
        z = z,
        visibility = visibility,
        presence = presence
    )

private fun RawVerticalCropBounds.toRecoveryDto(): UploadRecoveryRawVerticalCropBoundsDto =
    UploadRecoveryRawVerticalCropBoundsDto(
        topFraction = topFraction,
        bottomFraction = bottomFraction
    )

private fun UploadRecoveryRawVerticalCropBoundsDto.toDomain(): RawVerticalCropBounds =
    RawVerticalCropBounds(
        topFraction = topFraction,
        bottomFraction = bottomFraction
    )

internal fun AttemptAlignedHoldSet.toRecoveryDto(): UploadRecoveryAttemptAlignedHoldSetDto =
    UploadRecoveryAttemptAlignedHoldSetDto(
        playbackUri = playbackUri,
        frameWidthPx = frameWidthPx,
        frameHeightPx = frameHeightPx,
        mode = mode.name,
        confidence = confidence,
        matchedHoldCount = matchedHoldCount,
        warpOnlyHoldCount = warpOnlyHoldCount,
        alignedHolds = alignedHolds.map(HoldNumbered::toRecoveryDto),
        rawCropBounds = rawCropBounds?.toRecoveryDto(),
        debugSummary = debugSummary
    )

internal fun UploadRecoveryAttemptAlignedHoldSetDto.toDomain(): AttemptAlignedHoldSet =
    AttemptAlignedHoldSet(
        playbackUri = playbackUri,
        frameWidthPx = frameWidthPx,
        frameHeightPx = frameHeightPx,
        mode = AttemptHoldAlignmentMode.valueOf(mode),
        confidence = confidence,
        matchedHoldCount = matchedHoldCount,
        warpOnlyHoldCount = warpOnlyHoldCount,
        alignedHolds = alignedHolds.map(UploadRecoveryHoldNumberedDto::toDomain),
        rawCropBounds = rawCropBounds?.toDomain(),
        debugSummary = debugSummary
    )

internal fun AttemptHoldReachResult.toRecoveryDto(): UploadRecoveryAttemptHoldReachResultDto =
    UploadRecoveryAttemptHoldReachResultDto(
        highestReachedHold = highestReachedHold?.toRecoveryDto(),
        highestReachedHoldNo = highestReachedHoldNo,
        highestReachedFrameTimeMs = highestReachedFrameTimeMs,
        totalHoldCount = totalHoldCount,
        contactedHoldNos = contactedHoldNos,
        reachedRatio = reachedRatio,
        completedWithBothHandsOnEndHold = completedWithBothHandsOnEndHold
    )

internal fun UploadRecoveryAttemptHoldReachResultDto.toDomain(): AttemptHoldReachResult =
    AttemptHoldReachResult(
        highestReachedHold = highestReachedHold?.toDomain(),
        highestReachedHoldNo = highestReachedHoldNo,
        highestReachedFrameTimeMs = highestReachedFrameTimeMs,
        totalHoldCount = totalHoldCount,
        contactedHoldNos = contactedHoldNos,
        reachedRatio = reachedRatio,
        completedWithBothHandsOnEndHold = completedWithBothHandsOnEndHold
    )

internal fun OverallHoldReachSummary.toRecoveryDto(): UploadRecoveryOverallHoldReachSummaryDto =
    UploadRecoveryOverallHoldReachSummaryDto(
        attempts = attempts.map(AttemptHoldReachResult::toRecoveryDto),
        averageHighestReachedHoldNo = averageHighestReachedHoldNo,
        roundedAverageHighestReachedHoldNo = roundedAverageHighestReachedHoldNo,
        totalHoldCount = totalHoldCount,
        averageReachedRatio = averageReachedRatio
    )

internal fun UploadRecoveryOverallHoldReachSummaryDto.toDomain(): OverallHoldReachSummary =
    OverallHoldReachSummary(
        attempts = attempts.map(UploadRecoveryAttemptHoldReachResultDto::toDomain),
        averageHighestReachedHoldNo = averageHighestReachedHoldNo,
        roundedAverageHighestReachedHoldNo = roundedAverageHighestReachedHoldNo,
        totalHoldCount = totalHoldCount,
        averageReachedRatio = averageReachedRatio
    )

private fun Hold.Point.toRecoveryDto(): UploadRecoveryHoldPointDto =
    UploadRecoveryHoldPointDto(x = x, y = y)

private fun UploadRecoveryHoldPointDto.toDomainPoint(): Hold.Point =
    Hold.Point(x = x, y = y)

private fun PolygonHoldContact.toRecoveryDto(): UploadRecoveryPolygonHoldContactDto =
    UploadRecoveryPolygonHoldContactDto(
        hold = hold.toRecoveryDto(),
        limb = limb.name,
        state = state,
        insidePolygon = insidePolygon,
        distancePx = distancePx,
        speedPxPerSec = speedPxPerSec,
        contactPointNormalized = contactPointNormalized?.toRecoveryDto()
    )

private fun UploadRecoveryPolygonHoldContactDto.toDomain(): PolygonHoldContact =
    PolygonHoldContact(
        hold = hold.toDomain(),
        limb = PolygonTrackedLimb.valueOf(limb),
        state = state,
        insidePolygon = insidePolygon,
        distancePx = distancePx,
        speedPxPerSec = speedPxPerSec,
        contactPointNormalized = contactPointNormalized?.toDomainPoint()
    )

private fun PolygonLimbFrameState.toRecoveryDto(): UploadRecoveryPolygonLimbFrameStateDto =
    UploadRecoveryPolygonLimbFrameStateDto(
        limb = limb.name,
        state = state,
        activeHoldNo = activeHoldNo,
        candidateHoldNo = candidateHoldNo,
        distancePx = distancePx,
        speedPxPerSec = speedPxPerSec,
        transition = transition,
        insidePolygon = insidePolygon,
        contactPointNormalized = contactPointNormalized?.toRecoveryDto()
    )

private fun UploadRecoveryPolygonLimbFrameStateDto.toDomain(): PolygonLimbFrameState =
    PolygonLimbFrameState(
        limb = PolygonTrackedLimb.valueOf(limb),
        state = state,
        activeHoldNo = activeHoldNo,
        candidateHoldNo = candidateHoldNo,
        distancePx = distancePx,
        speedPxPerSec = speedPxPerSec,
        transition = transition,
        insidePolygon = insidePolygon,
        contactPointNormalized = contactPointNormalized?.toDomainPoint()
    )

private fun PolygonHoldContactFrame.toRecoveryDto(): UploadRecoveryPolygonHoldContactFrameDto =
    UploadRecoveryPolygonHoldContactFrameDto(
        frameTimeMs = frameTimeMs,
        limbStates = limbStates.map(PolygonLimbFrameState::toRecoveryDto),
        activeContacts = activeContacts.map(PolygonHoldContact::toRecoveryDto)
    )

private fun UploadRecoveryPolygonHoldContactFrameDto.toDomain(): PolygonHoldContactFrame =
    PolygonHoldContactFrame(
        frameTimeMs = frameTimeMs,
        limbStates = limbStates.map(UploadRecoveryPolygonLimbFrameStateDto::toDomain),
        activeContacts = activeContacts.map(UploadRecoveryPolygonHoldContactDto::toDomain)
    )

internal fun PolygonHoldContactDebugResult.toRecoveryDto(): UploadRecoveryPolygonHoldContactDebugResultDto =
    UploadRecoveryPolygonHoldContactDebugResultDto(
        frames = frames.map(PolygonHoldContactFrame::toRecoveryDto),
        highestReachedHoldNo = highestReachedHoldNo,
        highestReachedFrameTimeMs = highestReachedFrameTimeMs,
        contactedHoldNos = contactedHoldNos
    )

internal fun UploadRecoveryPolygonHoldContactDebugResultDto.toDomain(): PolygonHoldContactDebugResult =
    PolygonHoldContactDebugResult(
        frames = frames.map(UploadRecoveryPolygonHoldContactFrameDto::toDomain),
        highestReachedHoldNo = highestReachedHoldNo,
        highestReachedFrameTimeMs = highestReachedFrameTimeMs,
        contactedHoldNos = contactedHoldNos
    )

private fun AiAnalysisVideoMetadata.toRecoveryDto(): UploadRecoveryAiAnalysisVideoMetadataDto =
    UploadRecoveryAiAnalysisVideoMetadataDto(
        frameWidth = frameWidth,
        frameHeight = frameHeight,
        fps = fps,
        totalFrames = totalFrames,
        processedFrames = processedFrames,
        frameStep = frameStep
    )

private fun UploadRecoveryAiAnalysisVideoMetadataDto.toDomain(): AiAnalysisVideoMetadata =
    AiAnalysisVideoMetadata(
        frameWidth = frameWidth,
        frameHeight = frameHeight,
        fps = fps,
        totalFrames = totalFrames,
        processedFrames = processedFrames,
        frameStep = frameStep
    )

private fun AiCruxSegment.toRecoveryDto(): UploadRecoveryAiCruxSegmentDto =
    UploadRecoveryAiCruxSegmentDto(
        startFrame = startFrame,
        endFrame = endFrame,
        startTimeMs = startTimeMs,
        endTimeMs = endTimeMs,
        durationSeconds = durationSeconds,
        dominantLimbs = dominantLimbs,
        dominantModes = dominantModes,
        meanTotalBodyLoad = meanTotalBodyLoad,
        meanCoreLoad = meanCoreLoad,
        meanNegativeMarginCm = meanNegativeMarginCm,
        meanLoadShiftProxy = meanLoadShiftProxy,
        meanConfidenceWeight = meanConfidenceWeight,
        okFraction = okFraction,
        segmentCruxScore = segmentCruxScore,
        reasonTags = reasonTags
    )

private fun UploadRecoveryAiCruxSegmentDto.toDomain(): AiCruxSegment =
    AiCruxSegment(
        startFrame = startFrame,
        endFrame = endFrame,
        startTimeMs = startTimeMs,
        endTimeMs = endTimeMs,
        durationSeconds = durationSeconds,
        dominantLimbs = dominantLimbs,
        dominantModes = dominantModes,
        meanTotalBodyLoad = meanTotalBodyLoad,
        meanCoreLoad = meanCoreLoad,
        meanNegativeMarginCm = meanNegativeMarginCm,
        meanLoadShiftProxy = meanLoadShiftProxy,
        meanConfidenceWeight = meanConfidenceWeight,
        okFraction = okFraction,
        segmentCruxScore = segmentCruxScore,
        reasonTags = reasonTags
    )

private fun AiCruxCandidate.toRecoveryDto(): UploadRecoveryAiCruxCandidateDto =
    UploadRecoveryAiCruxCandidateDto(
        holdId = holdId,
        segmentCount = segmentCount,
        engagementCount = engagementCount,
        totalActiveTimeSeconds = totalActiveTimeSeconds,
        longestContinuousDwellSeconds = longestContinuousDwellSeconds,
        reasonTags = reasonTags,
        bestSegment = bestSegment?.toRecoveryDto(),
        fastCruxScore = fastCruxScore,
        physicsCruxScore = physicsCruxScore
    )

private fun UploadRecoveryAiCruxCandidateDto.toDomain(): AiCruxCandidate =
    AiCruxCandidate(
        holdId = holdId,
        segmentCount = segmentCount,
        engagementCount = engagementCount,
        totalActiveTimeSeconds = totalActiveTimeSeconds,
        longestContinuousDwellSeconds = longestContinuousDwellSeconds,
        reasonTags = reasonTags,
        bestSegment = bestSegment?.toDomain(),
        fastCruxScore = fastCruxScore,
        physicsCruxScore = physicsCruxScore
    )

private fun AiCruxResult.toRecoveryDto(): UploadRecoveryAiCruxResultDto =
    UploadRecoveryAiCruxResultDto(
        candidateCount = candidateCount,
        topCandidates = topCandidates.map(AiCruxCandidate::toRecoveryDto),
        allCandidates = allCandidates.map(AiCruxCandidate::toRecoveryDto)
    )

private fun UploadRecoveryAiCruxResultDto.toDomain(): AiCruxResult =
    AiCruxResult(
        candidateCount = candidateCount,
        topCandidates = topCandidates.map(UploadRecoveryAiCruxCandidateDto::toDomain),
        allCandidates = allCandidates.map(UploadRecoveryAiCruxCandidateDto::toDomain)
    )

internal fun AiAnalysisResult.toRecoveryDto(): UploadRecoveryAiAnalysisResultDto =
    UploadRecoveryAiAnalysisResultDto(
        mode = mode.name,
        requestedMode = requestedMode.name,
        schemaVersion = schemaVersion,
        videoMetadata = videoMetadata?.toRecoveryDto(),
        timingsSeconds = timingsSeconds,
        correctionSummary = correctionSummary,
        cruxResult = cruxResult.toRecoveryDto(),
        holdStateSummary = holdStateSummary,
        physicsSummary = physicsSummary,
        physicsPipelineBenchmarkTimingsSeconds = physicsPipelineBenchmarkTimingsSeconds,
        physicsResult = physicsResult,
        fallbackReason = fallbackReason?.name,
        rawResponse = rawResponse
    )

internal fun UploadRecoveryAiAnalysisResultDto.toDomain(): AiAnalysisResult =
    AiAnalysisResult(
        mode = AiAnalysisMode.valueOf(mode),
        requestedMode = AiAnalysisMode.valueOf(requestedMode),
        schemaVersion = schemaVersion,
        videoMetadata = videoMetadata?.toDomain(),
        timingsSeconds = timingsSeconds,
        correctionSummary = correctionSummary,
        cruxResult = cruxResult.toDomain(),
        holdStateSummary = holdStateSummary,
        physicsSummary = physicsSummary,
        physicsPipelineBenchmarkTimingsSeconds = physicsPipelineBenchmarkTimingsSeconds,
        physicsResult = physicsResult,
        fallbackReason = fallbackReason?.let(AiAnalysisFallbackReason::valueOf),
        rawResponse = rawResponse
    )

internal fun PublishedAttemptResultSession.toRecoveryDto(): UploadRecoveryPublishedAttemptResultSessionDto =
    UploadRecoveryPublishedAttemptResultSessionDto(
        resultPlaybackUris = resultPlaybackUris,
        uploadedAttemptVideos = uploadedAttemptVideos.map(UploadedAttemptVideo::toRecoveryDto),
        currentAttemptIndex = currentAttemptIndex,
        attemptAlignedHoldSets = attemptAlignedHoldSets.map(AttemptAlignedHoldSet::toRecoveryDto),
        holdReachResults = holdReachResults.map(AttemptHoldReachResult::toRecoveryDto),
        attemptAiAnalysisResults = attemptAiAnalysisResults.map { it?.toRecoveryDto() },
        attemptPoseDtos = attemptPoseDtos,
        attemptAnalyzedPoses = attemptAnalyzedPoses.map { poses -> poses.map(Pose::toRecovery) },
        attemptPolygonHoldContactDebugResults = attemptPolygonHoldContactDebugResults.map(
            PolygonHoldContactDebugResult::toRecoveryDto
        ),
        overallHoldReachSummary = overallHoldReachSummary?.toRecoveryDto()
    )

internal fun UploadRecoveryPublishedAttemptResultSessionDto.toDomain(): PublishedAttemptResultSession =
    PublishedAttemptResultSession(
        resultPlaybackUris = resultPlaybackUris,
        uploadedAttemptVideos = uploadedAttemptVideos.map(UploadRecoveryUploadedAttemptVideoDto::toDomain),
        currentAttemptIndex = currentAttemptIndex,
        attemptAlignedHoldSets = attemptAlignedHoldSets.map(UploadRecoveryAttemptAlignedHoldSetDto::toDomain),
        holdReachResults = holdReachResults.map(UploadRecoveryAttemptHoldReachResultDto::toDomain),
        attemptAiAnalysisResults = attemptAiAnalysisResults.map { it?.toDomain() },
        attemptPoseDtos = attemptPoseDtos,
        attemptAnalyzedPoses = attemptAnalyzedPoses.map { poses -> poses.map(UploadRecoveryPoseDto::toDomain) },
        attemptPolygonHoldContactDebugResults = attemptPolygonHoldContactDebugResults.map(
            UploadRecoveryPolygonHoldContactDebugResultDto::toDomain
        ),
        overallHoldReachSummary = overallHoldReachSummary?.toDomain()
    )

internal val uploadRecoveryJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

internal fun UploadRecoverySnapshotPayload.toJson(): String =
    uploadRecoveryJson.encodeToString(UploadRecoverySnapshotPayload.serializer(), this)

internal fun decodeUploadRecoverySnapshotPayloadOrNull(json: String?): UploadRecoverySnapshotPayload? =
    json
        ?.takeIf { it.isNotBlank() }
        ?.let { encoded ->
            runCatching {
                uploadRecoveryJson.decodeFromString(
                    UploadRecoverySnapshotPayload.serializer(),
                    encoded
                )
            }.getOrNull()
        }

internal fun ChallengeCreateEntryStep.toRecovery(): UploadRecoveryCreateStep =
    when (this) {
        ChallengeCreateEntryStep.GYM_NAME -> UploadRecoveryCreateStep.GYM_NAME
        ChallengeCreateEntryStep.LEVEL -> UploadRecoveryCreateStep.LEVEL
        ChallengeCreateEntryStep.COLOR -> UploadRecoveryCreateStep.COLOR
    }

internal fun UploadRecoveryCreateStep.toEntryStep(): ChallengeCreateEntryStep =
    when (this) {
        UploadRecoveryCreateStep.GYM_NAME -> ChallengeCreateEntryStep.GYM_NAME
        UploadRecoveryCreateStep.LEVEL -> ChallengeCreateEntryStep.LEVEL
        UploadRecoveryCreateStep.COLOR -> ChallengeCreateEntryStep.COLOR
    }

internal fun AnalysisLoadingPhase.toRecovery(): UploadRecoveryAnalysisPhase =
    when (this) {
        AnalysisLoadingPhase.AttemptResultPreparation ->
            UploadRecoveryAnalysisPhase.ATTEMPT_RESULT_PREPARATION

        AnalysisLoadingPhase.FinalAnalysisPreparation ->
            UploadRecoveryAnalysisPhase.FINAL_ANALYSIS_PREPARATION
    }

internal fun UploadRecoveryAnalysisPhase.toDomain(): AnalysisLoadingPhase =
    when (this) {
        UploadRecoveryAnalysisPhase.ATTEMPT_RESULT_PREPARATION ->
            AnalysisLoadingPhase.AttemptResultPreparation

        UploadRecoveryAnalysisPhase.FINAL_ANALYSIS_PREPARATION ->
            AnalysisLoadingPhase.FinalAnalysisPreparation
    }
