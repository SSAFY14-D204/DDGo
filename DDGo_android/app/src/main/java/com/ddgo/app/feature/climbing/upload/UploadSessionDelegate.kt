package com.ddgo.app.feature.climbing.upload

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.withContext
import wseemann.media.FFmpegMediaMetadataRetriever

internal interface UploadSessionCallbacks {
    fun clearAttemptResultState(clearPublishedSession: Boolean)
    fun resetUploadSubmissionState()
    fun setUploadSubmissionLoading(message: String)
    fun currentAttemptIndex(): Int
    fun setCurrentAttemptIndex(index: Int)
    fun clearCurrentPoseLandmarks()
    fun syncDisplayedAnalysisPoints()
    fun onPrePoseBatchStateChanged()
}

/**
 * Retention/pre-pose/result-session state owner.
 *
 * This delegate owns upload artifacts and the keep-set used by cleanup.
 * Submission logic may publish or restore sessions through callbacks, but the
 * underlying session state remains here.
 */
internal class UploadSessionDelegate(
    private val context: Context,
    private val prePoseVideoAnalysisProvider: PrePoseVideoAnalysisProvider,
    private val analyzeHandPeakAndEndUseCase: AnalyzeHandPeakAndEndUseCase,
    private val detectStablePersonObservationUseCase: DetectStablePersonObservationUseCase,
    private val scope: CoroutineScope
) {

    var videoUri by mutableStateOf<String?>(null)
    var additionalVideoUris by mutableStateOf<List<String>>(emptyList())
    var attemptOnlyVideoUris by mutableStateOf<List<String>>(emptyList())
    var uploadFlowMode by mutableStateOf(UploadFlowMode.FullChallenge)
    var resultPlaybackUris by mutableStateOf<List<String>>(emptyList())
    var thumbnail by mutableStateOf<Bitmap?>(null)
    var videoFileName by mutableStateOf<String?>(null)
    var videoDuration by mutableStateOf<String?>(null)
    var selectionGeneration by mutableStateOf(0L)
    var prePoseBatchState by mutableStateOf(PrePoseBatchState())
    var primaryManagedVideo by mutableStateOf<ManagedAttemptVideo?>(null)
    var additionalManagedVideos by mutableStateOf<List<ManagedAttemptVideo>>(emptyList())
    var attemptOnlyManagedVideos by mutableStateOf<List<ManagedAttemptVideo>>(emptyList())
    var prePoseCacheEntries by mutableStateOf<Map<String, PrePoseCacheEntry>>(emptyMap())
    var publishedAttemptResultSession by mutableStateOf<PublishedAttemptResultSession?>(null)

    var primarySelectionJob: Job? = null
    var additionalSelectionJob: Job? = null
    var attemptOnlySelectionJob: Job? = null
    var prePoseWorkerJob: Job? = null
    var latestCallbacks: UploadSessionCallbacks? = null

    val prePoseTaskQueue = ArrayDeque<PrePoseTask>()
    val managedTempFilePaths = mutableSetOf<String>()
    val managedVideosByPlaybackUri = mutableMapOf<String, ManagedAttemptVideo>()
    val activePrePosePlaybackUris = mutableSetOf<String>()
    var nextPrePoseTaskId = 0L

    val allAttemptUris: List<String>
        get() = when (uploadFlowMode) {
            UploadFlowMode.FullChallenge -> listOfNotNull(videoUri) + additionalVideoUris
            UploadFlowMode.AttemptOnly -> attemptOnlyVideoUris
        }

    val playbackAttemptUris: List<String>
        get() = resultPlaybackUris.ifEmpty { allAttemptUris }

    val isAttemptOnlyUploadMode: Boolean
        get() = uploadFlowMode == UploadFlowMode.AttemptOnly

    fun updateAdditionalVideoUris(
        uris: List<String>,
        callbacks: UploadSessionCallbacks
    ) {
        val generation = beginSelectionUpdate(
            callbacks = callbacks,
            preservePublishedResult = isAttemptOnlyUploadMode
        )

        if (isAttemptOnlyUploadMode) {
            attemptOnlySelectionJob?.cancel()
            if (uris.isEmpty()) {
                attemptOnlyManagedVideos = emptyList()
                attemptOnlyVideoUris = emptyList()
                refreshCurrentSelectionPrePoseTargets(generation)
                cleanupUnusedManagedTempFiles()
                return
            }

            attemptOnlySelectionJob = scope.launch(Dispatchers.IO) {
                val preparedVideos = prepareManagedVideos(
                    uris = uris,
                    filePrefix = "attempt_only"
                )

                withContext(Dispatchers.Main) {
                    if (generation != selectionGeneration) {
                        deleteManagedVideos(preparedVideos)
                        return@withContext
                    }

                    registerManagedVideos(preparedVideos)
                    attemptOnlyManagedVideos = preparedVideos
                    attemptOnlyVideoUris = preparedVideos.map { it.playbackUri }
                    refreshCurrentSelectionPrePoseTargets(generation)
                    cleanupUnusedManagedTempFiles()
                }
            }
            return
        }

        additionalSelectionJob?.cancel()
        if (uris.isEmpty()) {
            additionalManagedVideos = emptyList()
            additionalVideoUris = emptyList()
            refreshCurrentSelectionPrePoseTargets(generation)
            cleanupUnusedManagedTempFiles()
            return
        }

        additionalSelectionJob = scope.launch(Dispatchers.IO) {
            val preparedVideos = prepareManagedVideos(
                uris = uris,
                filePrefix = "attempt"
            )

            withContext(Dispatchers.Main) {
                if (generation != selectionGeneration) {
                    deleteManagedVideos(preparedVideos)
                    return@withContext
                }

                registerManagedVideos(preparedVideos)
                additionalManagedVideos = preparedVideos
                additionalVideoUris = preparedVideos.map { it.playbackUri }
                refreshCurrentSelectionPrePoseTargets(generation)
                cleanupUnusedManagedTempFiles()
            }
        }
    }

    fun updateVideoUri(
        uri: String,
        realtimeSessionId: String?,
        callbacks: UploadSessionCallbacks
    ) {
        val generation = beginSelectionUpdate(callbacks = callbacks)
        uploadFlowMode = UploadFlowMode.FullChallenge
        attemptOnlyVideoUris = emptyList()
        attemptOnlyManagedVideos = emptyList()
        additionalManagedVideos = emptyList()
        additionalVideoUris = emptyList()

        primarySelectionJob?.cancel()
        primarySelectionJob = scope.launch(Dispatchers.IO) {
            val managedVideo = normalizeToManagedVideo(
                uri = Uri.parse(uri),
                filePrefix = "primary",
                realtimeSessionId = realtimeSessionId
            )

            withContext(Dispatchers.Main) {
                if (generation != selectionGeneration) {
                    deleteManagedVideo(managedVideo)
                    return@withContext
                }

                registerManagedVideo(managedVideo)
                primaryManagedVideo = managedVideo
                videoUri = managedVideo.playbackUri
                refreshCurrentSelectionPrePoseTargets(generation)
                cleanupUnusedManagedTempFiles()
            }

            if (generation == selectionGeneration) {
                extractVideoMetadata(Uri.parse(managedVideo.playbackUri))
            }
        }
    }

    fun beginSelectionUpdate(
        callbacks: UploadSessionCallbacks,
        preservePublishedResult: Boolean = false
    ): Long {
        latestCallbacks = callbacks
        selectionGeneration += 1
        if (preservePublishedResult) {
            callbacks.setCurrentAttemptIndex(
                publishedAttemptResultSession?.currentAttemptIndex ?: callbacks.currentAttemptIndex()
            )
            callbacks.clearCurrentPoseLandmarks()
            callbacks.syncDisplayedAnalysisPoints()
        } else {
            callbacks.clearAttemptResultState(clearPublishedSession = true)
        }
        callbacks.resetUploadSubmissionState()
        updatePrePoseBatchState()
        return selectionGeneration
    }

    fun refreshCurrentSelectionPrePoseTargets(generation: Long = selectionGeneration) {
        val currentUris = allAttemptUris.distinct()
        val currentUriSet = currentUris.toSet()
        val updatedEntries = prePoseCacheEntries.toMutableMap()

        prePoseTaskQueue.removeAll { task -> task.playbackUri !in currentUriSet }

        currentUris.forEach { playbackUri ->
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

        val keepUris = currentUriSet + resultPlaybackUris.toSet()
        prePoseCacheEntries = updatedEntries.filter { (playbackUri, entry) ->
            playbackUri in keepUris || entry.status == PrePoseStatus.Running
        }

        updatePrePoseBatchState()
        ensurePrePoseWorkerRunning()
    }

    suspend fun awaitActiveSelectionPreparation() {
        listOfNotNull(
            primarySelectionJob,
            additionalSelectionJob,
            attemptOnlySelectionJob
        ).forEach { job ->
            job.join()
        }
    }

    suspend fun awaitPrePoseTerminal(
        playbackUris: List<String>,
        callbacks: UploadSessionCallbacks
    ): TerminalPrePoseSnapshot {
        if (playbackUris.isEmpty()) {
            return TerminalPrePoseSnapshot(
                generation = selectionGeneration,
                entriesByPlaybackUri = emptyMap()
            )
        }

        refreshCurrentSelectionPrePoseTargets(selectionGeneration)

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
                                aiPoseSequence = null,
                                poses = emptyList(),
                                processedFrames = emptyList(),
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
            callbacks.setUploadSubmissionLoading(
                "pre-pose 준비 중입니다. (${completedCount}/${playbackUris.size})"
            )
            delay(100L)
        }
    }

    suspend fun awaitSubmitReadyPrePose(
        playbackUris: List<String>,
        callbacks: UploadSessionCallbacks
    ): TerminalPrePoseSnapshot {
        var snapshot = awaitPrePoseTerminal(
            playbackUris = playbackUris,
            callbacks = callbacks
        )
        val retryPlaybackUris = playbackUris.filter { playbackUri ->
            snapshot.entriesByPlaybackUri[playbackUri].isReusableForSubmission().not()
        }
        if (retryPlaybackUris.isEmpty()) {
            return snapshot
        }

        retryPrePoseEntries(retryPlaybackUris)
        snapshot = awaitPrePoseTerminal(
            playbackUris = playbackUris,
            callbacks = callbacks
        )
        return snapshot
    }

    fun clearPosePrecomputeState(
        preservePlaybackUris: Set<String> = emptySet()
    ) {
        selectionGeneration += 1
        if (preservePlaybackUris.isEmpty()) {
            prePoseTaskQueue.clear()
            prePoseCacheEntries = emptyMap()
            prePoseBatchState = PrePoseBatchState()
            return
        }

        prePoseTaskQueue.removeAll { task -> task.playbackUri !in preservePlaybackUris }
        prePoseCacheEntries = prePoseCacheEntries.filterKeys { playbackUri ->
            playbackUri in preservePlaybackUris
        }
        updatePrePoseBatchState()
    }

    fun resetAllSelectionPreparationJobs() {
        primarySelectionJob?.cancel()
        additionalSelectionJob?.cancel()
        attemptOnlySelectionJob?.cancel()
        primarySelectionJob = null
        additionalSelectionJob = null
        attemptOnlySelectionJob = null
    }

    fun cleanupUnusedManagedTempFiles(forceDeleteAll: Boolean = false) {
        val referencedTempPaths = buildSet {
            if (!forceDeleteAll) {
                listOfNotNull(primaryManagedVideo)
                    .plus(additionalManagedVideos)
                    .plus(attemptOnlyManagedVideos)
                    .mapNotNullTo(this) { it.tempFilePath }

                resultPlaybackUris.mapNotNullTo(this) { playbackUri ->
                    managedVideosByPlaybackUri[playbackUri]?.tempFilePath
                }
                publishedAttemptResultSession
                    ?.resultPlaybackUris
                    ?.mapNotNullTo(this) { playbackUri ->
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

    fun publishedResultPlaybackUris(): Set<String> =
        publishedAttemptResultSession?.resultPlaybackUris?.toSet().orEmpty()

    private fun ensurePrePoseWorkerRunning() {
        if (prePoseWorkerJob?.isActive == true) return

        prePoseWorkerJob = scope.launch(Dispatchers.Default) {
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
                activePrePosePlaybackUris += task.playbackUri
                updatePrePoseBatchState()

                val result = runCatching {
                    prePoseVideoAnalysisProvider.analyze(
                        videoUri = task.playbackUri,
                        analysisFpsLimit = UPLOAD_PREPOSE_ANALYSIS_FPS
                    )
                }

                val latestEntry = prePoseCacheEntries[task.playbackUri]
                if (latestEntry?.taskId != task.taskId) {
                    activePrePosePlaybackUris -= task.playbackUri
                    continue
                }

                prePoseCacheEntries = prePoseCacheEntries.toMutableMap().apply {
                    put(
                        task.playbackUri,
                        if (result.isSuccess) {
                            val prePoseAnalysis = result.getOrNull()
                            val aiPoseSequence = prePoseAnalysis?.aiPoseSequence
                            val poses = prePoseAnalysis?.poses.orEmpty()
                            val processedFrames = prePoseAnalysis?.processedFrames.orEmpty()
                            val personObservationStartTimeMs = detectStablePersonObservationUseCase(
                                processedFrames
                            )
                            val handPeakAnnotation = runCatching {
                                analyzeHandPeakAndEndUseCase(poses.map { pose -> pose.toPoseFrame() })
                            }.onFailure { error ->
                                Log.w(TAG, "Pre-pose hand peak analysis failed: ${task.playbackUri}", error)
                            }.getOrNull()
                            latestEntry.copy(
                                status = PrePoseStatus.Ready,
                                aiPoseSequence = aiPoseSequence,
                                poses = poses,
                                processedFrames = processedFrames,
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
                                aiPoseSequence = null,
                                poses = emptyList(),
                                processedFrames = emptyList(),
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
                activePrePosePlaybackUris -= task.playbackUri
                updatePrePoseBatchState()
                cleanupUnusedManagedTempFiles()
            }
        }
    }

    private fun updatePrePoseBatchState() {
        val currentUris = allAttemptUris.distinct()
        if (currentUris.isEmpty()) {
            prePoseBatchState = PrePoseBatchState()
            callbacksOnPrePoseBatchStateChanged()
            return
        }

        val entries = currentUris.mapNotNull { prePoseCacheEntries[it] }
        prePoseBatchState = PrePoseBatchState(
            generation = selectionGeneration,
            totalCount = currentUris.size,
            pendingCount = entries.count { it.status == PrePoseStatus.Pending },
            runningCount = entries.count { it.status == PrePoseStatus.Running },
            readyCount = entries.count { it.status == PrePoseStatus.Ready },
            failedCount = entries.count { it.status == PrePoseStatus.Failed }
        )
        callbacksOnPrePoseBatchStateChanged()
    }

    private fun callbacksOnPrePoseBatchStateChanged() {
        scope.launch(Dispatchers.Main) {
            latestCallbacks?.onPrePoseBatchStateChanged()
        }
    }

    private suspend fun prepareManagedVideos(
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

    private fun normalizeToManagedVideo(
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
            Log.d(TAG, "Video cached for upload: ${tempFile.absolutePath}")

            ManagedAttemptVideo(
                sourceUri = uri.toString(),
                playbackUri = Uri.fromFile(tempFile).toString(),
                tempFilePath = tempFile.absolutePath,
                realtimeSessionId = realtimeSessionId
            )
        } catch (error: Exception) {
            Log.e(TAG, "Failed to cache video, using original uri: ${error.message}")
            ManagedAttemptVideo(
                sourceUri = uri.toString(),
                playbackUri = uri.toString(),
                tempFilePath = null,
                realtimeSessionId = realtimeSessionId
            )
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

    private fun deleteManagedVideo(video: ManagedAttemptVideo?) {
        deleteManagedVideos(listOfNotNull(video))
    }

    private fun deleteManagedVideos(videos: List<ManagedAttemptVideo>) {
        videos.mapNotNull { it.tempFilePath }.forEach(::deleteManagedTempFile)
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
            Log.w(TAG, "Failed to delete temp upload file: $path", error)
        }
    }

    private fun registerManagedVideo(video: ManagedAttemptVideo) {
        if (video.tempFilePath != null) {
            managedVideosByPlaybackUri[video.playbackUri] = video
        }
    }

    private fun registerManagedVideos(videos: List<ManagedAttemptVideo>) {
        videos.forEach(::registerManagedVideo)
    }

    private fun nextPrePoseTaskId(): Long {
        nextPrePoseTaskId += 1L
        return nextPrePoseTaskId
    }

    private fun retryPrePoseEntries(playbackUris: List<String>) {
        if (playbackUris.isEmpty()) {
            return
        }

        val currentUris = allAttemptUris.toSet()
        val updatedEntries = prePoseCacheEntries.toMutableMap()

        playbackUris
            .distinct()
            .filter { playbackUri -> playbackUri in currentUris }
            .forEach { playbackUri ->
                val taskId = nextPrePoseTaskId()
                prePoseTaskQueue.removeAll { task -> task.playbackUri == playbackUri }
                updatedEntries[playbackUri] = PrePoseCacheEntry(
                    playbackUri = playbackUri,
                    selectionGeneration = selectionGeneration,
                    status = PrePoseStatus.Pending,
                    aiPoseSequence = null,
                    poses = emptyList(),
                    processedFrames = emptyList(),
                    personObservationStartTimeMs = null,
                    climbEndDetection = null,
                    handPeakAnnotation = null,
                    timelinePoints = emptyList(),
                    errorMessage = null,
                    taskId = taskId
                )
                prePoseTaskQueue.addLast(
                    PrePoseTask(
                        playbackUri = playbackUri,
                        taskId = taskId
                    )
                )
            }

        prePoseCacheEntries = updatedEntries
        updatePrePoseBatchState()
        ensurePrePoseWorkerRunning()
    }

    private fun extractVideoMetadata(uri: Uri) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val name = if (uri.scheme == "file") {
                    uri.path?.let { File(it).name }
                } else {
                    context.contentResolver.query(
                        uri,
                        arrayOf(OpenableColumns.DISPLAY_NAME),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
                }

                val firstPts = getFirstActualPts(uri)
                Log.d(TAG, "extractVideoMetadata: first pts=${firstPts / 1000}ms")

                val retriever = FFmpegMediaMetadataRetriever()
                val (durationStr, frame) = try {
                    if (!setUploadRetrieverDataSource(
                            context = context,
                            retriever = retriever,
                            uri = uri,
                            logTag = TAG
                        )
                    ) {
                        return@runCatching null
                    }

                    val durationMs = retriever
                        .extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLong() ?: 0L
                    val duration = "%d:%02d".format(durationMs / 1000 / 60, (durationMs / 1000) % 60)
                    val rotationDegrees = readUploadVideoRotationDegrees(
                        context = context,
                        uri = uri,
                        logTag = TAG
                    )

                    val bitmap = retriever.getFrameAtTime(
                        firstPts,
                        FFmpegMediaMetadataRetriever.OPTION_CLOSEST
                    )?.let { rawBitmap ->
                        orientBitmapForUploadRotation(
                            bitmap = rawBitmap,
                            rotationDegrees = rotationDegrees
                        )
                    }

                    Pair(duration, bitmap)
                } finally {
                    retriever.release()
                }

                Triple(name, durationStr, frame)

            }.onSuccess { triple ->
                withContext(Dispatchers.Main) {
                    videoFileName = triple?.first
                    videoDuration = triple?.second
                    thumbnail = triple?.third
                }
            }.onFailure { error ->
                Log.e(TAG, "extractVideoMetadata failed", error)
            }
        }
    }

    private fun getFirstActualPts(uri: Uri): Long {
        val extractor = MediaExtractor()
        return try {
            if (uri.scheme == "file") {
                extractor.setDataSource(uri.path ?: return 0L)
            } else {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return 0L
                extractor.setDataSource(pfd.fileDescriptor)
                pfd.close()
            }

            var videoTrack = -1
            for (index in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("video/") == true) {
                    videoTrack = index
                    break
                }
            }
            if (videoTrack == -1) {
                Log.e(TAG, "No video track found while extracting metadata")
                return 0L
            }
            extractor.selectTrack(videoTrack)

            extractor.sampleTime.coerceAtLeast(0L)
        } catch (error: Exception) {
            Log.e(TAG, "Failed to read first actual pts: ${error.message}", error)
            0L
        } finally {
            extractor.release()
        }
    }

    companion object {
        private const val TAG = "UploadSessionDelegate"
    }
}

private fun TerminalPrePoseEntry?.isReusableForSubmission(): Boolean {
    return this != null && status == PrePoseStatus.Ready && aiPoseSequence != null
}
