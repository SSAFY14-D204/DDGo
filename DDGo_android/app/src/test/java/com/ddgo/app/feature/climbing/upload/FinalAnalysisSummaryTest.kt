package com.ddgo.app.feature.climbing.upload

import com.ddgo.app.domain.model.AiAnalysisFallbackReason
import com.ddgo.app.domain.model.AiAnalysisMode
import com.ddgo.app.domain.model.AiAnalysisResult
import com.ddgo.app.domain.model.AiAnalysisVideoMetadata
import com.ddgo.app.domain.model.AiCruxCandidate
import com.ddgo.app.domain.model.AiCruxResult
import com.ddgo.app.domain.model.AiCruxSegment
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinalAnalysisSummaryTest {

    @Test
    fun `physics result is mapped into template summary`() {
        val summaries = buildFinalAnalysisAttemptSummaries(
            attemptCount = 1,
            totalHolds = 9,
            aiResults = listOf(samplePhysicsResult())
        )

        val summary = summaries.single()
        assertTrue(summary.hasAiResult)
        assertTrue(summary.isSuccess)
        assertEquals(9, summary.reachedHolds)
        assertEquals("9", summary.reachedHoldsText)
        assertEquals(90, summary.processedFrames)
        assertEquals("90", summary.processedFramesText)
        assertEquals(80, summary.highConfidenceRatio)
        assertEquals("80%", summary.highConfidenceRatioText)
        assertEquals(75, summary.insideSupportRatio)
        assertEquals("75%", summary.insideSupportRatioText)
        assertEquals(55, summary.stableContactFrameCount)
        assertEquals("55", summary.stableContactFrameCountText)
        assertEquals(61, summary.stableContactRatio)
        assertEquals("61%", summary.stableContactRatioText)
        assertEquals(28, summary.stabilityTimeline.size)
        assertNotNull(summary.stabilityFocusFraction)
        assertEquals("빠른 분석", summary.effectiveModeLabel)
        assertEquals("빠른 분석 대체", summary.fallbackLabel)
        assertTrue(summary.analysisPoints.first().description.contains("9번 홀드"))
        assertTrue(summary.stabilityHighlights.contains("손발 지지 안정도 61%"))
        assertTrue(summary.failureHighlights.contains("크럭스 홀드 9번"))
        assertEquals("몸통", summary.loadFocusLabel)
    }

    @Test
    fun `missing ai result keeps template but shows empty state`() {
        val summaries = buildFinalAnalysisAttemptSummaries(
            attemptCount = 2,
            aiResults = listOf(null)
        )

        assertEquals(2, summaries.size)
        assertEquals(false, summaries[0].hasAiResult)
        assertEquals("정보 없음", summaries[0].processedFramesText)
        assertEquals("정보 없음", summaries[0].highConfidenceRatioText)
        assertEquals("정보 없음", summaries[0].insideSupportRatioText)
        assertEquals("정보 없음", summaries[0].stableContactFrameCountText)
        assertEquals(28, summaries[0].stabilityTimeline.size)
    }
}

private fun samplePhysicsResult(): AiAnalysisResult {
    return AiAnalysisResult(
        mode = AiAnalysisMode.FAST,
        requestedMode = AiAnalysisMode.PHYSICS,
        schemaVersion = "2026-03-19",
        videoMetadata = AiAnalysisVideoMetadata(
            frameWidth = 1920,
            frameHeight = 1080,
            fps = 30f,
            totalFrames = 120,
            processedFrames = 90,
            frameStep = 1
        ),
        timingsSeconds = mapOf(
            "pose_correction" to 0.5,
            "crux_scoring" to 1.2
        ),
        correctionSummary = null,
        cruxResult = AiCruxResult(
            candidateCount = 1,
            topCandidates = listOf(
                AiCruxCandidate(
                    holdId = 9,
                    segmentCount = 1,
                    engagementCount = 2,
                    totalActiveTimeSeconds = 1.6,
                    longestContinuousDwellSeconds = 1.2,
                    reasonTags = listOf("long_dwell"),
                    bestSegment = AiCruxSegment(
                        startFrame = 30,
                        endFrame = 48,
                        startTimeMs = 1000L,
                        endTimeMs = 1600L,
                        durationSeconds = 0.6,
                        dominantLimbs = listOf("right_hand"),
                        dominantModes = listOf("pull"),
                        meanNegativeMarginCm = -8.0,
                        segmentCruxScore = 0.85
                    ),
                    fastCruxScore = 0.85,
                    physicsCruxScore = 0.9
                )
            ),
            allCandidates = emptyList()
        ),
        holdStateSummary = null,
        physicsSummary = buildJsonObject {
            put("processed_frames", JsonPrimitive(90))
            put("high_confidence_frame_count", JsonPrimitive(72))
            put("ok_contact_force_frame_count", JsonPrimitive(55))
            put("point_support_frame_count", JsonPrimitive(6))
            put("fit_mean_error_m", JsonPrimitive(0.032))
            put("recovery_ratio", JsonPrimitive(0.18))
        },
        physicsPipelineBenchmarkTimingsSeconds = null,
        physicsResult = buildJsonObject {
            put(
                "phase_counts",
                buildJsonObject {
                    put("static_support", JsonPrimitive(60))
                    put("loaded_transition", JsonPrimitive(20))
                    put("recovery", JsonPrimitive(10))
                }
            )
            put(
                "support_mode_counts",
                buildJsonObject {
                    put("active_contacts", JsonPrimitive(82))
                    put("fallback_all_limbs", JsonPrimitive(8))
                }
            )
            put(
                "body_load_summary",
                buildJsonObject {
                    put(
                        "core",
                        buildJsonObject {
                            put("max_abs_load_proxy", JsonPrimitive(320.4))
                        }
                    )
                    put(
                        "right_arm",
                        buildJsonObject {
                            put("max_abs_load_proxy", JsonPrimitive(180.2))
                        }
                    )
                }
            )
            put(
                "dynamic_sequence_gate",
                buildJsonObject {
                    put("fit_mean_error_m", JsonPrimitive(0.032))
                    put("recovery_ratio", JsonPrimitive(0.18))
                }
            )
            put(
                "frames",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put(
                                "active_hold_ids",
                                buildJsonObject {
                                    put("left_hand", JsonPrimitive(3))
                                    put("right_hand", JsonPrimitive(6))
                                }
                            )
                            put("analysis_confidence", JsonPrimitive("high"))
                            put("contact_force_status", JsonPrimitive("ok"))
                            put(
                                "support_stability",
                                buildJsonObject {
                                    put("stability_margin_m", JsonPrimitive(-0.10))
                                }
                            )
                        }
                    )
                    add(
                        buildJsonObject {
                            put(
                                "active_hold_ids",
                                buildJsonObject {
                                    put("left_foot", JsonPrimitive(4))
                                    put("right_foot", JsonPrimitive(9))
                                }
                            )
                            put("analysis_confidence", JsonPrimitive("high"))
                            put("contact_force_status", JsonPrimitive("ok"))
                            put(
                                "support_stability",
                                buildJsonObject {
                                    put("stability_margin_m", JsonPrimitive(0.08))
                                }
                            )
                        }
                    )
                }
            )
            put(
                "support_stability_summary",
                buildJsonObject {
                    put("inside_support_count", JsonPrimitive(3))
                    put("outside_support_count", JsonPrimitive(1))
                    put(
                        "support_type_counts",
                        buildJsonObject {
                            put("point_support", JsonPrimitive(6))
                        }
                    )
                }
            )
        },
        fallbackReason = AiAnalysisFallbackReason.PHYSICS_REQUEST_FAILED,
        rawResponse = buildJsonObject { }
    )
}
