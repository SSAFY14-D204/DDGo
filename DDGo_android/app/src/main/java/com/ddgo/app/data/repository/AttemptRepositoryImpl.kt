package com.ddgo.app.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.ddgo.app.data.mapper.AttemptMapper.toDomain
import com.ddgo.app.data.mapper.AttemptMapper.toUploadedAttemptVideo
import com.ddgo.app.data.remote.attempt.AttemptApi
import com.ddgo.app.data.remote.attempt.GenerateVideoUrlRequestDto
import com.ddgo.app.domain.model.AttemptUploadTicket
import com.ddgo.app.domain.model.UploadedAttemptVideo
import com.ddgo.app.domain.repository.AttemptRepository
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @Named("DirectUploadOkHttpClient") private val directUploadOkHttpClient: OkHttpClient
) : AttemptRepository {

    override suspend fun uploadAttemptVideo(
        challengeId: Long,
        videoUri: String
    ): Result<UploadedAttemptVideo> {
        return try {
            val startResponse = attemptApi.startAttempt(challengeId)
            val startedAttempt = startResponse.data
                ?: return Result.failure(
                    Exception(startResponse.message.ifBlank { "Failed to start attempt." })
                )

            if (!startResponse.success) {
                return Result.failure(
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
                ?: return Result.failure(
                    Exception(presignedUrlResponse.message.ifBlank { "Failed to issue upload URL." })
                )

            if (!presignedUrlResponse.success) {
                return Result.failure(
                    Exception(presignedUrlResponse.message.ifBlank { "Failed to issue upload URL." })
                )
            }

            uploadToPresignedUrl(
                uploadTicket = uploadTicket,
                videoUri = videoUri,
                contentType = metadata.contentType,
                fileSize = metadata.fileSize
            )

            Result.success(
                com.ddgo.app.data.mapper.AttemptMapper.toUploadedAttemptVideo(
                    challengeId = challengeId,
                    videoUri = videoUri,
                    startResponse = startedAttempt,
                    uploadResponse = presignedUrl
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun uploadToPresignedUrl(
        uploadTicket: AttemptUploadTicket,
        videoUri: String,
        contentType: String,
        fileSize: Long
    ) {
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
    private data class VideoFileMetadata(
        val originalFileName: String,
        val contentType: String,
        val fileSize: Long
    )
}
