package com.ddgo.app.feature.climbing.upload

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ddgo.app.data.ml.color.HoldColorClassifier
import com.ddgo.app.domain.model.Hold
import com.ddgo.app.domain.repository.HoldDetector
import com.ddgo.app.domain.repository.PersonDetector
import com.ddgo.app.domain.usecase.HoldNumbered
import com.ddgo.app.domain.usecase.assignHoldNumbers
import com.ddgo.app.domain.usecase.toHolds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt
import wseemann.media.FFmpegMediaMetadataRetriever

internal class UploadHoldDetectionDelegate(
    private val context: Context,
    private val personDetector: PersonDetector,
    private val holdDetector: HoldDetector,
    private val holdColorClassifier: HoldColorClassifier
) {

    var bestFrameBitmap by mutableStateOf<Bitmap?>(null)
    var debugBestFrameImageUri by mutableStateOf<String?>(null)
    var allRawHolds by mutableStateOf<List<Hold>>(emptyList())
    var detectedHolds by mutableStateOf<List<Hold>>(emptyList())
    var candidateHolds by mutableStateOf<List<Hold>>(emptyList())
    var showCandidatePopup by mutableStateOf(false)
    var selectedStartHold by mutableStateOf<Hold?>(null)
    var selectedEndHold by mutableStateOf<Hold?>(null)
    var numberedHolds by mutableStateOf<List<HoldNumbered>>(emptyList())
    private var lastSuccessfulDetectionInput: DetectionInputKey? = null
    private var holdDetectionPrecomputeEntry by mutableStateOf<HoldDetectionPrecomputeEntry?>(null)

    fun useDebugBestFrameImage(uri: String) {
        debugBestFrameImageUri = uri
        clearDetectionOutput(preserveDebugSource = true)
        clearSelectedHoldSelection()
    }

    fun resetHoldDetectionState(clearDebugSource: Boolean) {
        clearDetectionOutput(preserveDebugSource = !clearDebugSource)
        clearSelectedHoldSelection()
    }

    fun requestHoldPrecompute(
        selectionGeneration: Long,
        sourceVideoUri: String?
    ) {
        val sourceKey = buildPrecomputeSourceKey(sourceVideoUri) ?: return
        val existingEntry = holdDetectionPrecomputeEntry

        if (existingEntry != null && existingEntry.matches(selectionGeneration, sourceKey)) {
            if (existingEntry.status != HoldDetectionPrecomputeStatus.Failed) {
                return
            }
        }

        holdDetectionPrecomputeEntry = HoldDetectionPrecomputeEntry(
            selectionGeneration = selectionGeneration,
            sourceVideoUri = sourceKey.sourceVideoUri,
            debugBestFrameImageUri = sourceKey.debugBestFrameImageUri,
            status = HoldDetectionPrecomputeStatus.Waiting
        )
    }

    fun isPrecomputeRunning(
        selectionGeneration: Long,
        sourceVideoUri: String?
    ): Boolean {
        val sourceKey = buildPrecomputeSourceKey(sourceVideoUri) ?: return false
        return holdDetectionPrecomputeEntry?.matches(selectionGeneration, sourceKey) == true &&
            holdDetectionPrecomputeEntry?.status == HoldDetectionPrecomputeStatus.Running
    }

    fun isPrecomputeReady(
        selectionGeneration: Long,
        sourceVideoUri: String?
    ): Boolean {
        val sourceKey = buildPrecomputeSourceKey(sourceVideoUri) ?: return false
        return holdDetectionPrecomputeEntry?.matches(selectionGeneration, sourceKey) == true &&
            holdDetectionPrecomputeEntry?.status == HoldDetectionPrecomputeStatus.Ready
    }

    fun updateSelectedStartHold(hold: Hold) {
        selectedStartHold = hold
        selectedEndHold = null
        numberedHolds = emptyList()
    }

    fun updateSelectedEndHold(hold: Hold) {
        selectedEndHold = hold
        recomputeHoldNumbers()
    }

    fun findCandidatesNearTap(tapNormX: Float, tapNormY: Float) {
        val candidates = findNearbyCandidates(tapNormX, tapNormY)
        if (candidates.isNotEmpty()) {
            candidateHolds = candidates
            showCandidatePopup = true
        }
    }

    fun applyHoldChanges(toAdd: List<Hold>, toRemove: List<Hold>) {
        toAdd.forEach(::addManualHold)
        toRemove.forEach(::removeHold)
        dismissCandidatePopup()
        syncDetectedHoldsToPrecomputeEntry()
    }

    fun dismissCandidatePopup() {
        showCandidatePopup = false
        candidateHolds = emptyList()
    }

    fun removeHold(hold: Hold) {
        detectedHolds = detectedHolds.filter { existing ->
            existing.boundingBox != hold.boundingBox
        }
        syncDetectedHoldsToPrecomputeEntry()
        clearSelectedHoldSelection()
        Log.d(TAG, "removeHold: bbox=${hold.boundingBox}, color=${hold.colorLabel}")
    }

    suspend fun precomputeHoldDetection(
        selectionGeneration: Long,
        sourceVideoUri: String?,
        allowRetryOnFailure: Boolean = false
    ): Result<Unit> = runCatching {
        val sourceKey = buildPrecomputeSourceKey(sourceVideoUri)
            ?: throw IllegalStateException("videoUri/debugBestFrameImageUri ?놁쓬")
        val existingEntry = holdDetectionPrecomputeEntry

        if (existingEntry != null && existingEntry.matches(selectionGeneration, sourceKey)) {
            when (existingEntry.status) {
                HoldDetectionPrecomputeStatus.Ready -> {
                    syncPublicDetectionState(existingEntry)
                    return@runCatching
                }

                HoldDetectionPrecomputeStatus.Running -> {
                    Log.d(TAG, "precomputeHoldDetection: reuse running task for identical source")
                    return@runCatching
                }

                HoldDetectionPrecomputeStatus.Failed -> {
                    if (!allowRetryOnFailure) {
                        throw IllegalStateException(
                            existingEntry.errorMessage ?: "hold detection precompute failed."
                        )
                    }
                }

                HoldDetectionPrecomputeStatus.Idle,
                HoldDetectionPrecomputeStatus.Waiting -> Unit
            }
        }

        holdDetectionPrecomputeEntry = HoldDetectionPrecomputeEntry(
            selectionGeneration = selectionGeneration,
            sourceVideoUri = sourceKey.sourceVideoUri,
            debugBestFrameImageUri = sourceKey.debugBestFrameImageUri,
            status = HoldDetectionPrecomputeStatus.Running
        )

        val precomputed = withContext(Dispatchers.IO) {
            val preparedFrame = prepareBestFrame(sourceKey)
            val rawHolds = detectRawHoldsFromBestFrame(preparedFrame.bitmap)
            val classified = classifyAllHoldsFromBestFrame(
                bitmap = preparedFrame.bitmap,
                rawHolds = rawHolds
            )
            PreparedHoldPrecomputeResult(
                bitmap = preparedFrame.bitmap,
                bestFrameTimeUs = preparedFrame.bestFrameTimeUs,
                rawYoloHolds = rawHolds,
                classifiedAllRich = classified.classifiedHolds,
                allRawHolds = classified.allHolds
            )
        }

        val readyEntry = HoldDetectionPrecomputeEntry(
            selectionGeneration = selectionGeneration,
            sourceVideoUri = sourceKey.sourceVideoUri,
            debugBestFrameImageUri = sourceKey.debugBestFrameImageUri,
            status = HoldDetectionPrecomputeStatus.Ready,
            bestFrameBitmap = precomputed.bitmap,
            bestFrameTimeUs = precomputed.bestFrameTimeUs,
            rawYoloHolds = precomputed.rawYoloHolds,
            classifiedAllRich = precomputed.classifiedAllRich,
            allRawHolds = precomputed.allRawHolds,
            detectedHolds = emptyList(),
            errorMessage = null
        )
        holdDetectionPrecomputeEntry = readyEntry
        syncPublicDetectionState(readyEntry)
    }.onFailure { throwable ->
        val sourceKey = buildPrecomputeSourceKey(sourceVideoUri)
        holdDetectionPrecomputeEntry = HoldDetectionPrecomputeEntry(
            selectionGeneration = selectionGeneration,
            sourceVideoUri = sourceKey?.sourceVideoUri,
            debugBestFrameImageUri = sourceKey?.debugBestFrameImageUri,
            status = HoldDetectionPrecomputeStatus.Failed,
            errorMessage = throwable.message
        )
    }

    fun applyHoldColorFilter(
        selectionGeneration: Long,
        detectionTargetColor: String
    ): Result<Boolean> = runCatching {
        val currentEntry = holdDetectionPrecomputeEntry
            ?: throw IllegalStateException("hold detection precompute cache missing.")
        val normalizedColor = detectionTargetColor.trim().lowercase()

        if (
            currentEntry.selectionGeneration != selectionGeneration ||
            currentEntry.status != HoldDetectionPrecomputeStatus.Ready
        ) {
            throw IllegalStateException(
                currentEntry.errorMessage ?: "hold detection precompute is not ready."
            )
        }

        if (currentEntry.lastAppliedColorKey == normalizedColor) {
            syncPublicDetectionState(currentEntry)
            lastSuccessfulDetectionInput = DetectionInputKey(
                sourceVideoUri = currentEntry.sourceVideoUri,
                debugBestFrameImageUri = currentEntry.debugBestFrameImageUri,
                normalizedDetectionTargetColor = normalizedColor
            )
            return@runCatching false
        }

        val filteredHolds = holdColorClassifier.filterClassifiedHolds(
            classifiedHolds = currentEntry.classifiedAllRich,
            targetColorName = detectionTargetColor,
            scoreThreshold = 0.25f
        )
        val updatedEntry = currentEntry.copy(
            lastAppliedColorKey = normalizedColor,
            detectedHolds = filteredHolds,
            errorMessage = null
        )
        holdDetectionPrecomputeEntry = updatedEntry
        syncPublicDetectionState(updatedEntry)
        clearSelectedHoldSelection()
        lastSuccessfulDetectionInput = DetectionInputKey(
            sourceVideoUri = updatedEntry.sourceVideoUri,
            debugBestFrameImageUri = updatedEntry.debugBestFrameImageUri,
            normalizedDetectionTargetColor = normalizedColor
        )
        true
    }

    suspend fun runHoldDetection(
        sourceVideoUri: String?,
        detectionTargetColor: String,
        selectionGeneration: Long = holdDetectionPrecomputeEntry?.selectionGeneration ?: 0L
    ): Result<Unit> = runCatching {
        val debugImageUri = debugBestFrameImageUri
        val currentInput = DetectionInputKey(
            sourceVideoUri = sourceVideoUri,
            debugBestFrameImageUri = debugImageUri,
            normalizedDetectionTargetColor = detectionTargetColor.trim().lowercase()
        )
        if (debugImageUri == null && sourceVideoUri == null) {
            throw IllegalStateException("videoUri/debugBestFrameImageUri 없음")
        }

        if (canReuseDetectionResult(currentInput)) {
            Log.d(TAG, "runHoldDetection: reuse cached result for identical input")
            return@runCatching
        }

        precomputeHoldDetection(
            selectionGeneration = selectionGeneration,
            sourceVideoUri = sourceVideoUri,
            allowRetryOnFailure = true
        ).getOrThrow()
        applyHoldColorFilter(
            selectionGeneration = selectionGeneration,
            detectionTargetColor = detectionTargetColor
        ).getOrThrow()
        lastSuccessfulDetectionInput = currentInput
        return@runCatching

        val (bitmap, allHolds, filteredHolds) = withContext(Dispatchers.IO) {
            val preparedBitmap = if (debugImageUri != null) {
                Log.d(TAG, "runHoldDetection: use debug image as best frame, uri=$debugImageUri")
                loadBitmapFromUri(Uri.parse(debugImageUri))
            } else {
                val uri = sourceVideoUri
                    ?: throw IllegalStateException("videoUri 없음")

                Log.d(TAG, "runHoldDetection: PersonDetector start")
                val bestTimeUs = personDetector.findBestFrameTime(uri)
                Log.d(TAG, "runHoldDetection: best frame at ${bestTimeUs / 1000}ms")

                val retriever = FFmpegMediaMetadataRetriever()
                val parsedUri = Uri.parse(uri)
                val rotationDegrees = readUploadVideoRotationDegrees(
                    context = context,
                    uri = parsedUri,
                    logTag = TAG
                )
                try {
                    if (!setUploadRetrieverDataSource(
                            context = context,
                            retriever = retriever,
                            uri = parsedUri,
                            logTag = TAG
                        )
                    ) {
                        throw IllegalStateException("setDataSource 실패 (scheme=${parsedUri.scheme})")
                    }
                    retriever.getFrameAtTime(
                        bestTimeUs,
                        FFmpegMediaMetadataRetriever.OPTION_CLOSEST
                    )?.let { rawBitmap ->
                        orientBitmapForUploadRotation(
                            bitmap = rawBitmap,
                            rotationDegrees = rotationDegrees
                        )
                    } ?: throw IllegalStateException(
                        "getFrameAtTime returned null (PTS=${bestTimeUs / 1000}ms)"
                    )
                } finally {
                    retriever.release()
                }
            }

            Log.d(
                TAG,
                "runHoldDetection: prepared best frame (${preparedBitmap.width}x${preparedBitmap.height})"
            )

            val detectionResult = detectHoldsFromBestFrame(
                bitmap = preparedBitmap,
                detectionTargetColor = detectionTargetColor
            )
            Triple(preparedBitmap, detectionResult.allHolds, detectionResult.filteredHolds)
        }

        bestFrameBitmap = bitmap
        allRawHolds = allHolds
        detectedHolds = filteredHolds
        lastSuccessfulDetectionInput = currentInput
        clearSelectedHoldSelection()
    }

    fun clearSelectedHoldSelection() {
        selectedStartHold = null
        selectedEndHold = null
        numberedHolds = emptyList()
    }

    private fun clearDetectionOutput(preserveDebugSource: Boolean) {
        if (!preserveDebugSource) {
            debugBestFrameImageUri = null
        }
        bestFrameBitmap = null
        allRawHolds = emptyList()
        detectedHolds = emptyList()
        candidateHolds = emptyList()
        showCandidatePopup = false
        lastSuccessfulDetectionInput = null
        holdDetectionPrecomputeEntry = null
    }

    private fun canReuseDetectionResult(input: DetectionInputKey): Boolean {
        return lastSuccessfulDetectionInput == input && bestFrameBitmap != null
    }

    private fun syncPublicDetectionState(entry: HoldDetectionPrecomputeEntry) {
        bestFrameBitmap = entry.bestFrameBitmap
        allRawHolds = entry.allRawHolds
        detectedHolds = entry.detectedHolds
    }

    private fun syncDetectedHoldsToPrecomputeEntry() {
        val currentEntry = holdDetectionPrecomputeEntry ?: return
        if (currentEntry.status != HoldDetectionPrecomputeStatus.Ready) {
            return
        }
        holdDetectionPrecomputeEntry = currentEntry.copy(detectedHolds = detectedHolds)
    }

    private fun buildPrecomputeSourceKey(
        sourceVideoUri: String?
    ): HoldDetectionPrecomputeSourceKey? {
        val debugImageUri = debugBestFrameImageUri
        if (debugImageUri == null && sourceVideoUri == null) {
            return null
        }
        return HoldDetectionPrecomputeSourceKey(
            sourceVideoUri = sourceVideoUri,
            debugBestFrameImageUri = debugImageUri
        )
    }

    private fun addManualHold(hold: Hold) {
        val alreadyExists = detectedHolds.any { existing ->
            existing.boundingBox == hold.boundingBox
        }
        if (!alreadyExists) {
            detectedHolds = detectedHolds + hold
            syncDetectedHoldsToPrecomputeEntry()
            clearSelectedHoldSelection()
            Log.d(TAG, "addManualHold: bbox=${hold.boundingBox}, color=${hold.colorLabel}")
        }
    }

    private fun findNearbyCandidates(
        tapNormX: Float,
        tapNormY: Float,
        searchRadius: Float = 0.12f
    ): List<Hold> {
        return allRawHolds
            .filter { hold ->
                val cx = (hold.boundingBox.left + hold.boundingBox.right) / 2f
                val cy = (hold.boundingBox.top + hold.boundingBox.bottom) / 2f
                val dist = sqrt(
                    (cx - tapNormX) * (cx - tapNormX) + (cy - tapNormY) * (cy - tapNormY)
                )
                dist <= searchRadius
            }
            .sortedBy { hold ->
                val cx = (hold.boundingBox.left + hold.boundingBox.right) / 2f
                val cy = (hold.boundingBox.top + hold.boundingBox.bottom) / 2f
                sqrt((cx - tapNormX) * (cx - tapNormX) + (cy - tapNormY) * (cy - tapNormY))
            }
            .take(8)
    }

    private fun recomputeHoldNumbers() {
        val startHold = selectedStartHold ?: return
        val endHold = selectedEndHold ?: return

        runCatching {
            assignHoldNumbers(
                holds = detectedHolds,
                startHold = startHold,
                endHold = endHold
            )
        }.onSuccess { numbered ->
            numberedHolds = numbered
            detectedHolds = numbered.toHolds()
            syncDetectedHoldsToPrecomputeEntry()
            selectedStartHold = numbered.firstOrNull { it.isStart }?.hold
            selectedEndHold = numbered.firstOrNull { it.isEnd }?.hold
            Log.d(TAG, "recomputeHoldNumbers: success, count=${numbered.size}")
        }.onFailure { throwable ->
            Log.e(TAG, "recomputeHoldNumbers: failed", throwable)
            numberedHolds = emptyList()
        }
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap {
        if (uri.scheme == "file") {
            return BitmapFactory.decodeFile(uri.path)
                ?: throw IllegalStateException("선택한 이미지를 읽을 수 없습니다.")
        }

        val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input)
        }

        return bitmap ?: throw IllegalStateException("선택한 이미지를 읽을 수 없습니다.")
    }

    private suspend fun detectRawHoldsFromBestFrame(bitmap: Bitmap): List<Hold> {
        Log.d(TAG, "detectHoldsFromBestFrame: HoldDetector start")
        val rawHolds = holdDetector.detectFromFrame(bitmap)
        Log.d(TAG, "detectHoldsFromBestFrame: raw hold count=${rawHolds.size}")
        return rawHolds
    }

    private fun classifyAllHoldsFromBestFrame(
        bitmap: Bitmap,
        rawHolds: List<Hold>
    ): HoldColorClassifier.ClassifiedHoldPrecomputeResult {
        Log.d(TAG, "detectHoldsFromBestFrame: classify all colors")
        return holdColorClassifier.classifyAllRich(
            bitmap = bitmap,
            holds = rawHolds,
            relaxedRejection = true
        )
    }

    private suspend fun prepareBestFrame(sourceKey: HoldDetectionPrecomputeSourceKey): PreparedBestFrame {
        if (sourceKey.debugBestFrameImageUri != null) {
            Log.d(
                TAG,
                "prepareBestFrame: use debug image as best frame, uri=${sourceKey.debugBestFrameImageUri}"
            )
            return PreparedBestFrame(
                bitmap = loadBitmapFromUri(Uri.parse(sourceKey.debugBestFrameImageUri)),
                bestFrameTimeUs = null
            )
        }

        val uri = sourceKey.sourceVideoUri
            ?: throw IllegalStateException("videoUri ?놁쓬")

        Log.d(TAG, "prepareBestFrame: PersonDetector start")
        val bestTimeUs = personDetector.findBestFrameTime(uri)
        Log.d(TAG, "prepareBestFrame: best frame at ${bestTimeUs / 1000}ms")

        val retriever = FFmpegMediaMetadataRetriever()
        val parsedUri = Uri.parse(uri)
        val rotationDegrees = readUploadVideoRotationDegrees(
            context = context,
            uri = parsedUri,
            logTag = TAG
        )
        val preparedBitmap = try {
            if (!setUploadRetrieverDataSource(
                    context = context,
                    retriever = retriever,
                    uri = parsedUri,
                    logTag = TAG
                )
            ) {
                throw IllegalStateException("setDataSource ?ㅽ뙣 (scheme=${parsedUri.scheme})")
            }
            retriever.getFrameAtTime(
                bestTimeUs,
                FFmpegMediaMetadataRetriever.OPTION_CLOSEST
            )?.let { rawBitmap ->
                orientBitmapForUploadRotation(
                    bitmap = rawBitmap,
                    rotationDegrees = rotationDegrees
                )
            } ?: throw IllegalStateException(
                "getFrameAtTime returned null (PTS=${bestTimeUs / 1000}ms)"
            )
        } finally {
            retriever.release()
        }

        Log.d(
            TAG,
            "prepareBestFrame: prepared best frame (${preparedBitmap.width}x${preparedBitmap.height})"
        )
        return PreparedBestFrame(
            bitmap = preparedBitmap,
            bestFrameTimeUs = bestTimeUs
        )
    }

    private suspend fun detectHoldsFromBestFrame(
        bitmap: Bitmap,
        detectionTargetColor: String
    ): DetectedHoldFrameResult {
        val rawHolds = detectRawHoldsFromBestFrame(bitmap)
        val filteredHolds = holdColorClassifier.filterClassifiedHolds(
            classifiedHolds = classifyAllHoldsFromBestFrame(
                bitmap = bitmap,
                rawHolds = rawHolds
            ).classifiedHolds,
            targetColorName = detectionTargetColor,
            scoreThreshold = 0.25f
        )
        return DetectedHoldFrameResult(
            allHolds = classifyAllHoldsFromBestFrame(
                bitmap = bitmap,
                rawHolds = rawHolds
            ).allHolds,
            filteredHolds = filteredHolds
        )
    }

    private data class DetectedHoldFrameResult(
        val allHolds: List<Hold>,
        val filteredHolds: List<Hold>
    )

    private data class DetectionInputKey(
        val sourceVideoUri: String?,
        val debugBestFrameImageUri: String?,
        val normalizedDetectionTargetColor: String
    )

    internal enum class HoldDetectionPrecomputeStatus {
        Idle,
        Waiting,
        Running,
        Ready,
        Failed
    }

    internal data class HoldDetectionPrecomputeSourceKey(
        val sourceVideoUri: String?,
        val debugBestFrameImageUri: String?
    )

    internal data class HoldDetectionPrecomputeEntry(
        val selectionGeneration: Long,
        val sourceVideoUri: String?,
        val debugBestFrameImageUri: String?,
        val status: HoldDetectionPrecomputeStatus,
        val bestFrameBitmap: Bitmap? = null,
        val bestFrameTimeUs: Long? = null,
        val rawYoloHolds: List<Hold> = emptyList(),
        val classifiedAllRich: List<HoldColorClassifier.ClassifiedHoldRich> = emptyList(),
        val allRawHolds: List<Hold> = emptyList(),
        val lastAppliedColorKey: String? = null,
        val detectedHolds: List<Hold> = emptyList(),
        val errorMessage: String? = null
    ) {
        fun matches(
            selectionGeneration: Long,
            sourceKey: HoldDetectionPrecomputeSourceKey
        ): Boolean {
            return this.selectionGeneration == selectionGeneration &&
                sourceVideoUri == sourceKey.sourceVideoUri &&
                debugBestFrameImageUri == sourceKey.debugBestFrameImageUri
        }
    }

    private data class PreparedBestFrame(
        val bitmap: Bitmap,
        val bestFrameTimeUs: Long?
    )

    private data class PreparedHoldPrecomputeResult(
        val bitmap: Bitmap,
        val bestFrameTimeUs: Long?,
        val rawYoloHolds: List<Hold>,
        val classifiedAllRich: List<HoldColorClassifier.ClassifiedHoldRich>,
        val allRawHolds: List<Hold>
    )

    companion object {
        private const val TAG = "UploadHoldDetectionDelegate"
    }
}

internal fun setUploadRetrieverDataSource(
    context: Context,
    retriever: FFmpegMediaMetadataRetriever,
    uri: Uri,
    logTag: String
): Boolean {
    return try {
        if (uri.scheme == "file") {
            retriever.setDataSource(uri.path ?: return false)
        } else {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return false
            retriever.setDataSource(pfd.fileDescriptor)
            pfd.close()
        }
        true
    } catch (error: Exception) {
        Log.e(logTag, "setUploadRetrieverDataSource failed (scheme=${uri.scheme}): ${error.message}")
        false
    }
}

internal fun readUploadVideoRotationDegrees(
    context: Context,
    uri: Uri,
    logTag: String
): Int {
    val extractor = MediaExtractor()
    return try {
        if (uri.scheme == "file") {
            extractor.setDataSource(uri.path ?: return 0)
        } else {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return 0
            extractor.setDataSource(pfd.fileDescriptor)
            pfd.close()
        }

        for (trackIndex in 0 until extractor.trackCount) {
            val trackFormat = extractor.getTrackFormat(trackIndex)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("video/") == true) {
                return if (trackFormat.containsKey(MediaFormat.KEY_ROTATION)) {
                    normalizeVideoRotationDegrees(trackFormat.getInteger(MediaFormat.KEY_ROTATION))
                } else {
                    0
                }
            }
        }

        0
    } catch (error: Exception) {
        Log.w(logTag, "Failed to read video rotation metadata: ${error.message}", error)
        0
    } finally {
        extractor.release()
    }
}

internal fun orientBitmapForUploadRotation(
    bitmap: Bitmap,
    rotationDegrees: Int
): Bitmap {
    val normalizedRotationDegrees = normalizeVideoRotationDegrees(rotationDegrees)
    if (normalizedRotationDegrees == 0) {
        return bitmap
    }

    val matrix = Matrix().apply {
        postRotate(normalizedRotationDegrees.toFloat())
    }
    return Bitmap.createBitmap(
        bitmap,
        0,
        0,
        bitmap.width,
        bitmap.height,
        matrix,
        true
    ).also { orientedBitmap ->
        if (orientedBitmap !== bitmap && !bitmap.isRecycled) {
            bitmap.recycle()
        }
    }
}
