package com.ddgo.app.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.ddgo.app.core.network.toUserFacingNetworkMessageOrNull
import com.ddgo.app.data.mapper.AttemptMapper.toDomain
import com.ddgo.app.data.mapper.AttemptMapper.toUploadedAttemptVideo
import com.ddgo.app.data.remote.common.ApiErrorResponse
import com.ddgo.app.data.remote.attempt.AttemptApi
import com.ddgo.app.data.remote.attempt.AttemptEndBaseDataDto
import com.ddgo.app.data.remote.attempt.AttemptEndFeedbacksDataDto
import com.ddgo.app.data.remote.attempt.AttemptEndMetricsDataDto
import com.ddgo.app.data.remote.attempt.AttemptEndRequestDto
import com.ddgo.app.data.remote.attempt.GenerateVideoUrlRequestDto
import com.ddgo.app.data.remote.attempt.VideoUploadCompleteRequestDto
import com.ddgo.app.domain.model.AttemptCompletionPayload
import com.ddgo.app.domain.model.AttemptUploadTicket
import com.ddgo.app.domain.model.ChallengeAlreadyClosedException
import com.ddgo.app.domain.model.UploadedAttemptVideo
import com.ddgo.app.domain.repository.AttemptRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import javax.inject.Inject
import javax.inject.Named
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.URLConnection
import retrofit2.HttpException

private const val TAG = "AttemptRepository"

/**
 * AttemptRepository 구현체입니다.
 *
 * 역할:
 * - 시도를 시작합니다.
 * - presigned 업로드 URL을 발급받습니다.
 * - 발급된 URL로 영상 바이너리를 직접 업로드합니다.
 */
class AttemptRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val attemptApi: AttemptApi,
    @Named("DirectUploadOkHttpClient") private val directUploadOkHttpClient: OkHttpClient,
    private val json: Json
) : AttemptRepository {

    override suspend fun uploadAttemptVideo(
        challengeId: Long,
        videoUri: String
    ): Result<UploadedAttemptVideo> {
        return withContext(Dispatchers.IO) {
            try {
                val startResponse = attemptApi.startAttempt(challengeId)
                val startedAttempt = startResponse.data
                    ?: return@withContext Result.failure(
                        Exception(startResponse.message.ifBlank { "Failed to start attempt." })
                    )

                if (!startResponse.success) {
                    return@withContext Result.failure(
                        Exception(startResponse.message.ifBlank { "Failed to start attempt." })
                    )
                }

                val metadata = extractVideoMetadata(videoUri)
                val presignedUrlResponse = attemptApi.generateVideoUploadUrl(
                    attemptId = startedAttempt.attemptId,
                    request = GenerateVideoUrlRequestDto(
                        originalFileName = metadata.originalFileName,
                        contentType = metadata.contentType,
                        fileSize = metadata.fileSize
                    )
                )

                val presignedUrl = presignedUrlResponse.data
                val uploadTicket = presignedUrl?.toDomain(startedAttempt.attemptId)
                    ?: return@withContext Result.failure(
                        Exception(presignedUrlResponse.message.ifBlank { "Failed to issue upload URL." })
                    )

                if (!presignedUrlResponse.success) {
                    return@withContext Result.failure(
                        Exception(presignedUrlResponse.message.ifBlank { "Failed to issue upload URL." })
                    )
                }

                uploadToPresignedUrl(
                    uploadTicket = uploadTicket,
                    videoUri = videoUri,
                    contentType = metadata.contentType,
                    fileSize = metadata.fileSize
                ).let { etag ->
                    val uploadCompleteResponse = attemptApi.completeVideoUpload(
                        attemptId = startedAttempt.attemptId,
                        request = VideoUploadCompleteRequestDto(etag = etag)
                    )

                    if (!uploadCompleteResponse.success) {
                        return@withContext Result.failure(
                            Exception(
                                uploadCompleteResponse.message.ifBlank {
                                    "Failed to confirm uploaded video."
                                }
                            )
                        )
                    }

                    val uploadCompletion = uploadCompleteResponse.data
                        ?: return@withContext Result.failure(
                            Exception("Missing upload confirmation response.")
                        )

                    if (
                        !uploadCompletion.isUploadConfirmed() ||
                        !uploadCompletion.attemptStatus.equals("PROCESSING", ignoreCase = true) &&
                        !uploadCompletion.attemptStatus.equals("DONE", ignoreCase = true)
                    ) {
                        return@withContext Result.failure(
                            Exception(
                                "Video upload was confirmed, but attempt status is ${uploadCompletion.attemptStatus}."
                            )
                        )
                    }
                }

                Result.success(
                    com.ddgo.app.data.mapper.AttemptMapper.toUploadedAttemptVideo(
                        challengeId = challengeId,
                        videoUri = videoUri,
                        startResponse = startedAttempt,
                        uploadResponse = presignedUrl
                    )
                )
            } catch (e: HttpException) {
                val errorResponse = resolveHttpErrorResponse(e)
                val detail = errorResponse?.message?.takeIf { it.isNotBlank() }
                    ?: resolveHttpErrorMessage(e)
                Log.e(
                    TAG,
                    "uploadAttemptVideo: request failed with HTTP ${e.code()}" +
                        detail?.let { ", detail=$it" }.orEmpty(),
                    e
                )
                if (errorResponse?.code == CHALLENGE_ALREADY_CLOSED_ERROR_CODE) {
                    return@withContext Result.failure(
                        ChallengeAlreadyClosedException(
                            detail ?: "이미 종료된 챌린지에는 시도를 추가할 수 없습니다."
                        )
                    )
                }
                Result.failure(
                    IllegalStateException(
                        detail ?: "Failed to upload attempt video with HTTP ${e.code()}.",
                        e
                    )
                )
            } catch (e: Exception) {
                Result.failure(
                    IllegalStateException(
                        e.toUserFacingNetworkMessageOrNull() ?: e.message ?: "영상 업로드에 실패했어요.",
                        e
                    )
                )
            }
        }
    }

    override suspend fun endAttempt(
        challengeId: Long,
        attemptId: Long,
        payload: AttemptCompletionPayload
    ): Result<Unit> {
        return try {
            val response = attemptApi.endAttempt(
                challengeId = challengeId,
                attemptId = attemptId,
                request = AttemptEndRequestDto(
                    baseData = AttemptEndBaseDataDto(
                        attemptResult = payload.attemptResult,
                        durationMs = payload.durationMs,
                        maxHoldNo = payload.maxHoldNo
                    ),
                    metricsData = AttemptEndMetricsDataDto(
                        centerStabilityRatio = payload.centerStabilityRatio,
                        stabilityRecoveryScore = payload.stabilityRecoveryScore,
                        stableContactRatio = payload.stableContactRatio,
                        lowerBodyDriveScore = payload.lowerBodyDriveScore,
                        overallMovementScore = payload.overallMovementScore,
                        cruxHoldNo = payload.cruxHoldNo,
                        cruxDurationMs = payload.cruxDurationMs,
                        dangerEventCount = payload.dangerEventCount,
                        loadFocusLabel = payload.loadFocusLabel
                    ),
                    feedbacksData = AttemptEndFeedbacksDataDto(
                        failureReason = payload.failureReason,
                        riskAlert = payload.riskAlert,
                        nextMission = payload.nextMission
                    )
                )
            )

            if (response.success) {
                Result.success(Unit)
            } else {
                Result.failure(
                    Exception(response.message.ifBlank { "Failed to end attempt." })
                )
            }
        } catch (e: HttpException) {
            if (e.code() in setOf(404, 405, 501)) {
                Log.w(
                    TAG,
                    "endAttempt: endpoint is not ready yet (HTTP ${e.code()}). Skip finalization for now."
                )
                Result.success(Unit)
            } else {
                val detail = resolveHttpErrorMessage(e)
                Log.e(
                    TAG,
                    "endAttempt: request failed with HTTP ${e.code()}" +
                        detail?.let { ", detail=$it" }.orEmpty(),
                    e
                )
                Result.failure(
                    IllegalStateException(
                        detail ?: "Failed to end attempt with HTTP ${e.code()}.",
                        e
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException(
                    e.toUserFacingNetworkMessageOrNull() ?: e.message ?: "시도 결과 저장에 실패했어요.",
                    e
                )
            )
        }
    }

    private fun uploadToPresignedUrl(
        uploadTicket: AttemptUploadTicket,
        videoUri: String,
        contentType: String,
        fileSize: Long
    ): String? {
        val uploadUri = Uri.parse(uploadTicket.uploadUrl)
        if (uploadUri.host.equals("minio", ignoreCase = true)) {
            throw IllegalStateException(
                "Presigned upload URL host 'minio' is not reachable from the app. " +
                    "Backend must return a public upload URL."
            )
        }

        val uri = Uri.parse(videoUri)
        val requestBody = object : RequestBody() {
            override fun contentType() = contentType.toMediaTypeOrNull()

            override fun contentLength(): Long = fileSize

            override fun writeTo(sink: BufferedSink) {
                openInputStream(uri).use { inputStream ->
                    requireNotNull(inputStream) { "Could not open video stream for upload." }
                    sink.writeAll(inputStream.source())
                }
            }
        }

        val request = Request.Builder()
            .url(uploadTicket.uploadUrl)
            .put(requestBody)
            .addHeader("Content-Type", contentType)
            .build()

        directUploadOkHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Video upload failed with HTTP ${response.code}.")
            }

            return response.header("ETag")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }
    }

    private fun extractVideoMetadata(videoUri: String): VideoFileMetadata {
        val uri = Uri.parse(videoUri)
        val fileName = resolveFileName(uri)
        val contentType = resolveContentType(uri, fileName)
        val fileSize = resolveFileSize(uri)

        return VideoFileMetadata(
            originalFileName = fileName,
            contentType = contentType,
            fileSize = fileSize
        )
    }

    private fun resolveFileName(uri: Uri): String {
        if (uri.scheme == "file") {
            return uri.path?.let { File(it).name } ?: "attempt_video.mp4"
        }

        val cursor = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val name = it.getString(0)
                if (!name.isNullOrBlank()) {
                    return name
                }
            }
        }

        return "attempt_video.mp4"
    }

    private fun resolveContentType(uri: Uri, fileName: String): String {
        return context.contentResolver.getType(uri)
            ?: URLConnection.guessContentTypeFromName(fileName)
            ?: "video/mp4"
    }

    private fun resolveFileSize(uri: Uri): Long {
        if (uri.scheme == "file") {
            return uri.path?.let { File(it).length() } ?: 0L
        }

        val cursor = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val size = it.getLong(0)
                if (size > 0L) {
                    return size
                }
            }
        }

        openInputStream(uri).use { inputStream ->
            requireNotNull(inputStream) { "Could not read video size." }
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var totalBytes = 0L
            while (true) {
                val readBytes = inputStream.read(buffer)
                if (readBytes == -1) break
                totalBytes += readBytes
            }
            return totalBytes
        }
    }

    private fun openInputStream(uri: Uri): InputStream? {
        return when (uri.scheme) {
            "file" -> {
                val path = uri.path ?: return null
                FileInputStream(File(path))
            }

            else -> context.contentResolver.openInputStream(uri)
        }
    }

    /** 업로드 대상 영상의 메타데이터입니다. */
    private fun resolveHttpErrorResponse(exception: HttpException): ApiErrorResponse? {
        val body = runCatching {
            exception.response()
                ?.errorBody()
                ?.string()
                ?.trim()
        }.getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        return runCatching {
            json.decodeFromString(ApiErrorResponse.serializer(), body)
        }.getOrNull()
    }

    private fun resolveHttpErrorMessage(exception: HttpException): String? {
        return resolveHttpErrorResponse(exception)?.message?.takeIf { it.isNotBlank() }
    }

    private data class VideoFileMetadata(
        val originalFileName: String,
        val contentType: String,
        val fileSize: Long
    )

    private companion object {
        const val CHALLENGE_ALREADY_CLOSED_ERROR_CODE = "CH002"
    }
}
