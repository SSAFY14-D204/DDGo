package com.ddgo.app.feature.climbing.upload

import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ddgo.app.data.remote.pose.PoseSequenceDto
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.model.UploadedAttemptVideo
import com.ddgo.app.domain.usecase.AttemptHoldReachResult
import com.ddgo.app.domain.usecase.OverallHoldReachSummary
import com.ddgo.app.domain.usecase.PolygonHoldContactDebugResult

data class AttemptResultSnapshot(
    val resultPlaybackUris: List<String>,
    val uploadedAttemptVideos: List<UploadedAttemptVideo>,
    val currentAttemptIndex: Int,
    val holdReachResults: List<AttemptHoldReachResult>,
    val attemptPoseDtos: List<PoseSequenceDto>,
    val attemptAnalyzedPoses: List<List<Pose>>,
    val attemptPolygonHoldContactDebugResults: List<PolygonHoldContactDebugResult>,
    val overallHoldReachSummary: OverallHoldReachSummary?
)

class AttemptResultSessionStore {

    val currentPlaybackUrisState: MutableState<List<String>> = mutableStateOf(emptyList())

    var currentPlaybackUris by currentPlaybackUrisState
        private set

    private var publishedSnapshot: AttemptResultSnapshot? = null

    fun replaceCurrentPlaybackUris(playbackUris: List<String>) {
        currentPlaybackUris = playbackUris
    }

    fun clearCurrentPlayback() {
        currentPlaybackUris = emptyList()
    }

    fun publish(snapshot: AttemptResultSnapshot) {
        currentPlaybackUris = snapshot.resultPlaybackUris
        publishedSnapshot = snapshot
    }

    fun capture(snapshot: AttemptResultSnapshot?) {
        if (snapshot == null) return
        publishedSnapshot = snapshot
    }

    fun restorePublished(): AttemptResultSnapshot? {
        val snapshot = publishedSnapshot ?: run {
            currentPlaybackUris = emptyList()
            return null
        }

        currentPlaybackUris = snapshot.resultPlaybackUris
        return snapshot
    }

    fun clearPublished() {
        publishedSnapshot = null
    }

    fun publishedSnapshot(): AttemptResultSnapshot? = publishedSnapshot

    fun publishedPlaybackUris(): Set<String> = publishedSnapshot?.resultPlaybackUris?.toSet().orEmpty()
}
