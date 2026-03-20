package com.ddgo.app.feature.climbing.upload

import com.ddgo.app.domain.model.AiAnalysisMode
import com.ddgo.app.domain.model.AiAnalysisResult
import com.ddgo.app.domain.model.AnalysisPoint

internal fun defaultUploadAnalysisPoints(): List<AnalysisPoint> = listOf(
    AnalysisPoint(1, 21_000L, "2지점 상태가 길었어요"),
    AnalysisPoint(2, 48_000L, "오른쪽 팔에 과도한\n무게가 실렸어요"),
    AnalysisPoint(3, 66_000L, "무게 이동이 늦어졌어요")
)

internal fun AiAnalysisResult.toAnalysisPoints(): List<AnalysisPoint> {
    val candidates = cruxResult.topCandidates.ifEmpty {
        cruxResult.allCandidates.take(3)
    }

    return candidates.take(3).mapIndexed { index, candidate ->
        val reasonText = candidate.reasonTags
            .firstOrNull()
            ?.replace('_', ' ')
            ?.replace('-', ' ')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val description = buildString {
            append("홀드 ${candidate.holdId}")
            append(": ")
            append(
                reasonText ?: when (mode) {
                    AiAnalysisMode.FAST -> "머무는 시간이 길었어요"
                    AiAnalysisMode.PHYSICS -> "부하가 크게 걸렸어요"
                }
            )
        }

        AnalysisPoint(
            index = index + 1,
            timeMs = candidate.bestSegment?.startTimeMs ?: ((index + 1) * 15_000L),
            description = description
        )
    }
}
