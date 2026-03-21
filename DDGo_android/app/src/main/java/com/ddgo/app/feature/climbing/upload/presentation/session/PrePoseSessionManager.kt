package com.ddgo.app.feature.climbing.upload

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.poseanalysis.toPoseFrame
import com.ddgo.app.domain.repository.PrePoseVideoAnalysisProvider
import com.ddgo.app.domain.usecase.AnalyzeHandPeakAndEndUseCase
import com.ddgo.app.domain.usecase.DetectStablePersonObservationUseCase
import java.io.File
import java.util.ArrayDeque
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PRE_POSE_MANAGER_TAG = "PrePoseSessionManager"

class PrePoseSessionManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val prePoseVideoAnalysisProvider: PrePoseVideoAnalysisProvider,
    private val analyzeHandPeakAndEndUseCase: AnalyzeHandPeakAndEndUseCase,
    private val detectStablePersonObservationUseCase: DetectStablePersonObservationUseCase,
    private val onActivePrePoseSetChanged: (() -> Unit)? = null
) {
    private data class CleanupRequest(
        val currentResultPlaybackUris: Set<String>,
        val publishedResultPlaybackUris: Set<String>,
        val forceDeleteAll: Boolean
    )


    val selectionGenerationState: MutableState<Long> = mutableStateOf(0L)
    var selectionGeneration by selectionGenerationState
        private set

    val prePoseBatchStateState: MutableState<PrePoseBatchState> = mutableStateOf(PrePoseBatchState())
    var prePoseBatchState by prePoseBatchStateState
        private set

    val primaryManagedVideoState: MutableState<ManagedAttemptVideo?> = mutableStateOf(null)
    private var primaryManagedVideo by primaryManagedVideoState

    val additionalManagedVideosState: MutableState<List<ManagedAttemptVideo>> =
        mutableStateOf(emptyList())
    private var additionalManagedVideos by additionalManagedVideosState

    val attemptOnlyManagedVideosState: MutableState<List<ManagedAttemptVideo>> =
        mutableStateOf(emptyList())
    private var attemptOnlyManagedVideos by attemptOnlyManagedVideosState

    private var trackedAttemptUris by mutableStateOf<List<String>>(emptyList())
    val prePoseCacheEntriesState: MutableState<Map<String, PrePoseCacheEntry>> =
        mutableStateOf(emptyMap())
    private var prePoseCacheEntries by prePoseCacheEntriesState
    private val prePoseTaskQueue = ArrayDeque<PrePoseTask>()
    private var prePoseWorkerJob: Job? = null
    private var nextPrePoseTaskId = 0L
    val managedTempFilePaths = mutableSetOf<String>()
    val managedVideosByPlaybackUri = mutableMapOf<String, ManagedAttemptVideo>()
    val activePrePosePlaybackUris = mutableSetOf<String>()
    private var lastCleanupRequest: CleanupRequest? = null

    fun beginSelectionGeneration(): Long {
        selectionGeneration += 1L
        return selectionGeneration
    }

    fun primaryRealtimeSessionId(): String? =
        primaryManagedVideo?.realtimeSessionId?.takeIf { it.isNotBlank() }

    fun primaryPlaybackUri(): String? = primaryManagedVideo?.playbackUri

    fun additionalPlaybackUris(): List<String> = additionalManagedVideos.map { it.playbackUri }

    fun attemptOnlyPlaybackUris(): List<String> = attemptOnlyManagedVideos.map { it.playbackUri }

    fun prePoseEntry(playbackUri: String?): PrePoseCacheEntry? = playbackUri?.let(prePoseCacheEntries::get)

    fun readyPoseSequence(playbackUri: String?): List<Pose> {
        return prePoseEntry(playbackUri)
            ?.takeIf { it.status == PrePoseStatus.Ready }
            ?.poses
            .orEmpty()
    }

    fun replacePrimaryManagedVideo(video: ManagedAttemptVideo?) {
        primaryManagedVideo = video
    }

    fun replaceAdditionalManagedVideos(videos: List<ManagedAttemptVideo>) {
        additionalManagedVideos = videos
    }

    fun replaceAttemptOnlyManagedVideos(videos: List<ManagedAttemptVideo>) {
        attemptOnlyManagedVideos = videos
    }

    fun clearPrimaryManagedVideo() {
        primaryManagedVideo = null
    }

    fun clearAdditionalManagedVideos() {
        additionalManagedVideos = emptyList()
    }

    fun clearAttemptOnlyManagedVideos() {
        attemptOnlyManagedVideos = emptyList()
    }

    fun clearAllManagedSelections() {
        primaryManagedVideo = null
        additionalManagedVideos = emptyList()
        attemptOnlyManagedVideos = emptyList()
    }

    suspend fun prepareManagedVideos(
        uris: List<String>,
        filePrefix: String
    ): List<ManagedAttemptVideo> {
        return uris.mapIndexed { index, uriString ->
            normalizeToManagedVideo(
                uri = Uri.parse(uriString),
                filePrefix = "${filePrefix}_${index + 1}",
                realtimeSessionId = null
            )
        }
    }

    fun normalizeToManagedVideo(
        uri: Uri,
        filePrefix: String,
        realtimeSessionId: String?
    ): ManagedAttemptVideo {
        if (uri.scheme == "file") {
            return ManagedAttemptVideo(
                sourceUri = uri.toString(),
                playbackUri = uri.toString(),
                tempFilePath = null,
                realtimeSessionId = realtimeSessionId
            )
        }

        return try {
            val displayName = resolveDisplayName(uri)
            val extension = displayName.substringAfterLast('.', "mp4")
                .takeIf { it.isNotBlank() }
                ?.let { ".${it.lowercase()}" }
                ?: ".mp4"
            val tempFile = File(
                context.cacheDir,
                "${filePrefix}_${UUID.randomUUID()}$extension"
            )
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            managedTempFilePaths += tempFile.absolutePath
            Log.d(PRE_POSE_MANAGER_TAG, "영상 캐시 복사 완료: ${tempFile.absolutePath}")

            ManagedAttemptVideo(
                sourceUri = uri.toString(),
                playbackUri = Uri.fromFile(tempFile).toString(),
                tempFilePath = tempFile.absolutePath,
                realtimeSessionId = realtimeSessionId
            )
        } catch (e: Exception) {
            Log.e(PRE_POSE_MANAGER_TAG, "캐시 복사 실패, 원본 URI 사용: ${e.message}")
            ManagedAttemptVideo(
                sourceUri = uri.toString(),
                playbackUri = uri.toString(),
                tempFilePath = null,
                realtimeSessionId = realtimeSessionId
            )
        }
    }

    fun deleteManagedVideo(video: ManagedAttemptVideo?) {
        deleteManagedVideos(listOfNotNull(video))
    }

    fun deleteManagedVideos(videos: List<ManagedAttemptVideo>) {
        videos.mapNotNull { it.tempFilePath }.forEach(::deleteManagedTempFile)
    }

    fun registerManagedVideo(video: ManagedAttemptVideo) {
        if (video.tempFilePath != null) {
            managedVideosByPlaybackUri[video.playbackUri] = video
        }
    }

    fun registerManagedVideos(videos: List<ManagedAttemptVideo>) {
        videos.forEach(::registerManagedVideo)
    }

    fun refreshCurrentSelectionPrePoseTargets(
        currentAttemptUris: List<String>,
        currentResultPlaybackUris: Set<String>,
        generation: Long = selectionGeneration
    ) {
        trackedAttemptUris = currentAttemptUris.distinct()
        val currentUriSet = trackedAttemptUris.toSet()
        val updatedEntries = prePoseCacheEntries.toMutableMap()

        prePoseTaskQueue.removeAll { task -> task.playbackUri !in currentUriSet }

        trackedAttemptUris.forEach { playbackUri ->
            val existingEntry = updatedEntries[playbackUri]
            if (existingEntry == null) {
                val taskId = nextPrePoseTaskId()
                updatedEntries[playbackUri] = PrePoseCacheEntry(
                    playbackUri = playbackUri,
                    selectionGeneration = generation,
                    status = PrePoseStatus.Pending,
                    taskId = taskId
                )
                prePoseTaskQueue.addLast(
                    PrePoseTask(
                        playbackUri = playbackUri,
                        taskId = taskId
                    )
                )
                return@forEach
            }

            updatedEntries[playbackUri] = existingEntry.copy(
                selectionGeneration = generation
            )

            if (
                existingEntry.status == PrePoseStatus.Pending &&
                existingEntry.taskId != null &&
                prePoseTaskQueue.none { queued ->
                    queued.playbackUri == playbackUri && queued.taskId == existingEntry.taskId
                }
            ) {
                prePoseTaskQueue.addLast(
                    PrePoseTask(
                        playbackUri = playbackUri,
                        taskId = existingEntry.taskId
                    )
                )
            }
        }

        val keepUris = currentUriSet + currentResultPlaybackUris
        prePoseCacheEntries = updatedEntries.filter { (playbackUri, entry) ->
            playbackUri in keepUris || entry.status == PrePoseStatus.Running
        }

        updatePrePoseBatchState()
        ensurePrePoseWorkerRunning()
    }

    fun syncBatchState(currentAttemptUris: List<String>) {
        trackedAttemptUris = currentAttemptUris.distinct()
        updatePrePoseBatchState()
    }

    suspend fun awaitTerminal(
        playbackUris: List<String>,
        onProgress: (completedCount: Int, totalCount: Int) -> Unit = { _, _ -> }
    ): TerminalPrePoseSnapshot {
        if (playbackUris.isEmpty()) {
            return TerminalPrePoseSnapshot(
                generation = selectionGeneration,
                entriesByPlaybackUri = emptyMap()
            )
        }

        while (true) {
            val entries = playbackUris.mapNotNull { prePoseCacheEntries[it] }
            val activeCount = entries.count { entry ->
                entry.status == PrePoseStatus.Pending || entry.status == PrePoseStatus.Running
            }
            if (entries.size == playbackUris.size && activeCount == 0) {
                updatePrePoseBatchState()
                return TerminalPrePoseSnapshot(
                    generation = selectionGeneration,
                    entriesByPlaybackUri = playbackUris.associateWith { playbackUri ->
                        prePoseCacheEntries[playbackUri]?.toTerminalEntry()
                            ?: TerminalPrePoseEntry(
                                playbackUri = playbackUri,
                                selectionGeneration = selectionGeneration,
                                status = PrePoseStatus.Failed,
                                poses = emptyList(),
                                personObservationStartTimeMs = null,
                                climbEndDetection = null,
                                handPeakAnnotation = null,
                                timelinePoints = emptyList(),
                                errorMessage = "Missing pre-pose cache entry."
                            )
                    }
                )
            }

            val completedCount = entries.count { entry ->
                entry.status == PrePoseStatus.Ready || entry.status == PrePoseStatus.Failed
            }
            onProgress(completedCount, playbackUris.size)
            delay(100L)
        }
    }

    fun clearPosePrecomputeState(
        preservePlaybackUris: Set<String> = emptySet()
    ) {
        selectionGeneration += 1L
        if (preservePlaybackUris.isEmpty()) {
            trackedAttemptUris = emptyList()
            prePoseTaskQueue.clear()
            prePoseCacheEntries = emptyMap()
            prePoseBatchState = PrePoseBatchState()
            return
        }

        trackedAttemptUris = trackedAttemptUris.filter { it in preservePlaybackUris }
        prePoseTaskQueue.removeAll { task -> task.playbackUri !in preservePlaybackUris }
        prePoseCacheEntries = prePoseCacheEntries.filterKeys { playbackUri ->
            playbackUri in preservePlaybackUris
        }
        updatePrePoseBatchState()
    }

    fun cleanupUnusedManagedTempFiles(
        currentResultPlaybackUris: Set<String>,
        publishedResultPlaybackUris: Set<String>,
        forceDeleteAll: Boolean = false
    ) {
        lastCleanupRequest = CleanupRequest(
            currentResultPlaybackUris = currentResultPlaybackUris,
            publishedResultPlaybackUris = publishedResultPlaybackUris,
            forceDeleteAll = forceDeleteAll
        )
        performCleanupUnusedManagedTempFiles(
            currentResultPlaybackUris = currentResultPlaybackUris,
            publishedResultPlaybackUris = publishedResultPlaybackUris,
            forceDeleteAll = forceDeleteAll
        )
    }

    private fun performCleanupUnusedManagedTempFiles(
        currentResultPlaybackUris: Set<String>,
        publishedResultPlaybackUris: Set<String>,
        forceDeleteAll: Boolean
    ) {
        val referencedTempPaths = buildSet {
            if (!forceDeleteAll) {
                listOfNotNull(primaryManagedVideo)
                    .plus(additionalManagedVideos)
                    .plus(attemptOnlyManagedVideos)
                    .mapNotNullTo(this) { it.tempFilePath }

                currentResultPlaybackUris.mapNotNullTo(this) { playbackUri ->
                    managedVideosByPlaybackUri[playbackUri]?.tempFilePath
                }
                publishedResultPlaybackUris.mapNotNullTo(this) { playbackUri ->
                    managedVideosByPlaybackUri[playbackUri]?.tempFilePath
                }
            }

            activePrePosePlaybackUris.mapNotNullTo(this) { playbackUri ->
                managedVideosByPlaybackUri[playbackUri]?.tempFilePath
            }
        }

        managedTempFilePaths
            .toList()
            .filterNot { it in referencedTempPaths }
            .forEach(::deleteManagedTempFile)
    }

    private fun ensurePrePoseWorkerRunning() {
        if (prePoseWorkerJob?.isActive == true) return

        prePoseWorkerJob = coroutineScope.launch(Dispatchers.Default) {
            while (true) {
                val task = if (prePoseTaskQueue.isEmpty()) {
                    null
                } else {
                    prePoseTaskQueue.removeFirst()
                } ?: break
                val currentEntry = prePoseCacheEntries[task.playbackUri] ?: continue

                if (
                    currentEntry.status != PrePoseStatus.Pending ||
                    currentEntry.taskId != task.taskId
                ) {
                    continue
                }

                prePoseCacheEntries = prePoseCacheEntries.toMutableMap().apply {
                    put(
                        task.playbackUri,
                        currentEntry.copy(status = PrePoseStatus.Running)
                    )
                }
                addActivePrePosePlaybackUri(task.playbackUri)
                updatePrePoseBatchState()

                val result = runCatching {
                    prePoseVideoAnalysisProvider.analyze(task.playbackUri)
                }

                val latestEntry = prePoseCacheEntries[task.playbackUri]
                if (latestEntry?.taskId != task.taskId) {
                    removeActivePrePosePlaybackUri(task.playbackUri)
                    continue
                }

                prePoseCacheEntries = prePoseCacheEntries.toMutableMap().apply {
                    put(
                        task.playbackUri,
                        if (result.isSuccess) {
                            val prePoseAnalysis = result.getOrNull()
                            val poses = prePoseAnalysis?.poses.orEmpty()
                            val personObservationStartTimeMs = detectStablePersonObservationUseCase(
                                prePoseAnalysis?.processedFrames.orEmpty()
                            )
                            val handPeakAnnotation = runCatching {
                                analyzeHandPeakAndEndUseCase(poses.map { pose -> pose.toPoseFrame() })
                            }.onFailure { error ->
                                Log.w(
                                    PRE_POSE_MANAGER_TAG,
                                    "Pre-pose hand peak analysis failed: ${task.playbackUri}",
                                    error
                                )
                            }.getOrNull()
                            latestEntry.copy(
                                status = PrePoseStatus.Ready,
                                poses = poses,
                                personObservationStartTimeMs = personObservationStartTimeMs,
                                climbEndDetection = null,
                                handPeakAnnotation = handPeakAnnotation,
                                timelinePoints = buildAttemptTimelinePoints(
                                    personObservationStartTimeMs = personObservationStartTimeMs,
                                    endTimeMs = handPeakAnnotation?.endTimeMs
                                ),
                                errorMessage = null,
                                taskId = null
                            )
                        } else {
                            latestEntry.copy(
                                status = PrePoseStatus.Failed,
                                poses = emptyList(),
                                personObservationStartTimeMs = null,
                                climbEndDetection = null,
                                handPeakAnnotation = null,
                                timelinePoints = emptyList(),
                                errorMessage = result.exceptionOrNull()?.message,
                                taskId = null
                            )
                        }
                    )
                }
                removeActivePrePosePlaybackUri(task.playbackUri)
                updatePrePoseBatchState()
            }
        }
    }

    private fun updatePrePoseBatchState() {
        if (trackedAttemptUris.isEmpty()) {
            prePoseBatchState = PrePoseBatchState()
            return
        }

        val entries = trackedAttemptUris.mapNotNull { prePoseCacheEntries[it] }
        prePoseBatchState = PrePoseBatchState(
            generation = selectionGeneration,
            totalCount = trackedAttemptUris.size,
            pendingCount = entries.count { it.status == PrePoseStatus.Pending },
            runningCount = entries.count { it.status == PrePoseStatus.Running },
            readyCount = entries.count { it.status == PrePoseStatus.Ready },
            failedCount = entries.count { it.status == PrePoseStatus.Failed }
        )
    }

    private fun addActivePrePosePlaybackUri(playbackUri: String) {
        if (activePrePosePlaybackUris.add(playbackUri)) {
            onActivePrePoseSetChanged?.invoke()
        }
    }

    private fun removeActivePrePosePlaybackUri(playbackUri: String) {
        if (activePrePosePlaybackUris.remove(playbackUri)) {
            onActivePrePoseSetChanged?.invoke()
            lastCleanupRequest?.let { cleanupRequest ->
                performCleanupUnusedManagedTempFiles(
                    currentResultPlaybackUris = cleanupRequest.currentResultPlaybackUris,
                    publishedResultPlaybackUris = cleanupRequest.publishedResultPlaybackUris,
                    forceDeleteAll = cleanupRequest.forceDeleteAll
                )
            }
        }
    }

    private fun deleteManagedTempFile(path: String) {
        managedTempFilePaths.remove(path)
        managedVideosByPlaybackUri.entries.removeAll { (_, video) ->
            video.tempFilePath == path
        }
        runCatching {
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        }.onFailure { error ->
            Log.w(PRE_POSE_MANAGER_TAG, "임시 영상 삭제 실패: $path", error)
        }
    }

    private fun resolveDisplayName(uri: Uri): String {
        val fallback = uri.lastPathSegment?.substringAfterLast('/') ?: "selected_video"
        return context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else fallback
        } ?: fallback
    }

    private fun nextPrePoseTaskId(): Long {
        nextPrePoseTaskId += 1L
        return nextPrePoseTaskId
    }

    private data class PrePoseTask(
        val playbackUri: String,
        val taskId: Long
    )

    private fun PrePoseCacheEntry.toTerminalEntry(): TerminalPrePoseEntry = TerminalPrePoseEntry(
        playbackUri = playbackUri,
        selectionGeneration = selectionGeneration,
        status = status,
        poses = poses,
        personObservationStartTimeMs = personObservationStartTimeMs,
        climbEndDetection = climbEndDetection,
        handPeakAnnotation = handPeakAnnotation,
        timelinePoints = timelinePoints,
        errorMessage = errorMessage
    )
}
