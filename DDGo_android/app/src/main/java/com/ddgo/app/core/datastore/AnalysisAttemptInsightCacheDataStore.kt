package com.ddgo.app.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ddgo.app.domain.model.AnalysisAttemptInsight
import com.ddgo.app.domain.model.AnalysisHeartRateSample
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AnalysisAttemptInsightCacheDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        private val KEY_ATTEMPT_INSIGHT_CACHE =
            stringPreferencesKey("analysis_attempt_insight_cache_json")
        private const val MAX_CACHE_ENTRY_COUNT = 200

        private val cacheJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }

    suspend fun saveAttemptInsight(
        attemptId: Long,
        insight: AnalysisAttemptInsight
    ) {
        if (attemptId <= 0L) return
        if (insight.stabilityTimeline.size < 2 && insight.heartRateSeries.isEmpty()) return

        context.ddgoPreferencesDataStore.edit { preferences ->
            val currentPayload = preferences[KEY_ATTEMPT_INSIGHT_CACHE]
                ?.let { encoded ->
                    runCatching {
                        cacheJson.decodeFromString<AnalysisAttemptInsightCachePayload>(encoded)
                    }.getOrNull()
                }
                ?: AnalysisAttemptInsightCachePayload()

            val updatedEntries = buildList {
                add(
                    AnalysisAttemptInsightEntryPayload(
                        attemptId = attemptId,
                        stabilityTimeline = insight.stabilityTimeline,
                        heartRateSeries = insight.heartRateSeries.map { point ->
                            HeartRatePointPayload(
                                timestampMs = point.timestampMs,
                                bpm = point.bpm
                            )
                        },
                        videoDurationMs = insight.videoDurationMs,
                        stabilityFocusFraction = insight.stabilityFocusFraction,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                addAll(currentPayload.entries.filterNot { it.attemptId == attemptId })
            }.sortedByDescending(AnalysisAttemptInsightEntryPayload::updatedAt)
                .take(MAX_CACHE_ENTRY_COUNT)

            preferences[KEY_ATTEMPT_INSIGHT_CACHE] = cacheJson.encodeToString(
                AnalysisAttemptInsightCachePayload(entries = updatedEntries)
            )
        }
    }

    suspend fun getAttemptInsights(
        attemptIds: Collection<Long>
    ): Map<Long, AnalysisAttemptInsight> {
        if (attemptIds.isEmpty()) return emptyMap()

        val payload = context.ddgoPreferencesDataStore.data.first()[KEY_ATTEMPT_INSIGHT_CACHE]
            ?.let { encoded ->
                runCatching {
                    cacheJson.decodeFromString<AnalysisAttemptInsightCachePayload>(encoded)
                }.getOrNull()
            }
            ?: return emptyMap()

        return payload.entries
            .asSequence()
            .filter { it.attemptId in attemptIds }
            .associate { entry ->
                entry.attemptId to AnalysisAttemptInsight(
                    stabilityTimeline = entry.stabilityTimeline,
                    heartRateSeries = entry.heartRateSeries.map { point ->
                        AnalysisHeartRateSample(
                            timestampMs = point.timestampMs,
                            bpm = point.bpm
                        )
                    },
                    videoDurationMs = entry.videoDurationMs,
                    stabilityFocusFraction = entry.stabilityFocusFraction
                )
            }
    }
}

@Serializable
private data class AnalysisAttemptInsightCachePayload(
    val entries: List<AnalysisAttemptInsightEntryPayload> = emptyList()
)

@Serializable
private data class AnalysisAttemptInsightEntryPayload(
    val attemptId: Long,
    val stabilityTimeline: List<Float> = emptyList(),
    val heartRateSeries: List<HeartRatePointPayload> = emptyList(),
    val videoDurationMs: Long? = null,
    val stabilityFocusFraction: Float? = null,
    val updatedAt: Long = 0L
)

@Serializable
private data class HeartRatePointPayload(
    val timestampMs: Long,
    val bpm: Int
)
