package com.ddgo.app.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.ddgo.app.BuildConfig
import com.ddgo.app.core.network.toUserFacingNetworkMessageOrNull
import com.ddgo.app.data.mapper.CommunityMapper.toDomain
import com.ddgo.app.data.mapper.CommunityMapper.toDto
import com.ddgo.app.data.mapper.CommunityMapper.toPayload
import com.ddgo.app.data.mapper.CommunityMapper.toReference
import com.ddgo.app.data.mapper.GymMapper.toDomain
import com.ddgo.app.data.mapper.GymMapper.toDomainOrNull
import com.ddgo.app.data.remote.attempt.AttemptApi
import com.ddgo.app.data.remote.challenge.ChallengeApi
import com.ddgo.app.data.remote.community.CommunityApi
import com.ddgo.app.data.remote.community.CommunityCommentRequestDto
import com.ddgo.app.data.remote.community.CommunityPostUpsertRequestDto
import com.ddgo.app.data.remote.community.CommunityVideoUploadUrlRequestDto
import com.ddgo.app.data.remote.gym.GymApi
import com.ddgo.app.data.remote.gym.ResolveGymRequestDto
import com.ddgo.app.data.remote.kakao.KakaoLocalApi
import com.ddgo.app.data.remote.kakao.KakaoPlaceDocumentDto
import com.ddgo.app.domain.model.CommunityChallengeReference
import com.ddgo.app.domain.model.CommunityComment
import com.ddgo.app.domain.model.CommunityDraftVideoStatus
import com.ddgo.app.domain.model.CommunityFeedPage
import com.ddgo.app.domain.model.CommunityLikeResult
import com.ddgo.app.domain.model.CommunityPostDetail
import com.ddgo.app.domain.model.CommunityPostUpsertRequest
import com.ddgo.app.domain.model.CommunitySort
import com.ddgo.app.domain.model.CommunityVideoDraft
import com.ddgo.app.domain.model.CommunityVideoUploadFailureException
import com.ddgo.app.domain.model.CommunityVideoUploadRequest
import com.ddgo.app.domain.model.CommunityVideoUploadTicket
import com.ddgo.app.domain.model.NearbyPlace
import com.ddgo.app.domain.repository.CommunityRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.URLConnection
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source

class CommunityRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val communityApi: CommunityApi,
    private val challengeApi: ChallengeApi,
    private val attemptApi: AttemptApi,
    private val kakaoLocalApi: KakaoLocalApi,
    private val gymApi: GymApi,
    @Named("DirectUploadOkHttpClient") private val directUploadOkHttpClient: OkHttpClient
) : CommunityRepository {

    override suspend fun getPosts(
        page: Int,
        size: Int,
        keyword: String,
        sort: CommunitySort,
        gymId: Long?
    ): Result<CommunityFeedPage> = runCatching {
        val response = communityApi.getPosts(page, size, keyword, sort.name, gymId)
        if (!response.success || response.data == null) {
            throw IllegalStateException(response.message.ifBlank { "커뮤니티 게시글을 불러오지 못했어요." })
        }
        response.data.toDomain()
    }

    override suspend fun getPostDetail(postId: Long): Result<CommunityPostDetail> = runCatching {
        val response = communityApi.getPostDetail(postId)
        if (!response.success || response.data == null) {
            throw IllegalStateException(response.message.ifBlank { "게시글 상세를 불러오지 못했어요." })
        }
        response.data.toDomain().toEmulatorAccessibleDetail()
    }

    override suspend fun createPost(request: CommunityPostUpsertRequest): Result<CommunityPostDetail> = runCatching {
        val uploadedVideos = uploadPendingVideos(request.videos)
        val response = communityApi.createPost(
            CommunityPostUpsertRequestDto(
                title = request.title,
                content = request.content,
                gymId = request.gymId,
                videos = uploadedVideos.mapIndexed { index, item -> item.toPayload(index) }
            )
        )
        if (!response.success || response.data == null) {
            throw IllegalStateException(response.message.ifBlank { "게시글을 작성하지 못했어요." })
        }
        response.data.toDomain().toEmulatorAccessibleDetail()
    }

    override suspend fun updatePost(
        postId: Long,
        request: CommunityPostUpsertRequest
    ): Result<CommunityPostDetail> = runCatching {
        val uploadedVideos = uploadPendingVideos(request.videos)
        val response = communityApi.updatePost(
            postId = postId,
            request = CommunityPostUpsertRequestDto(
                title = request.title,
                content = request.content,
                gymId = request.gymId,
                videos = uploadedVideos.mapIndexed { index, item -> item.toPayload(index) }
            )
        )
        if (!response.success || response.data == null) {
            throw IllegalStateException(response.message.ifBlank { "게시글을 수정하지 못했어요." })
        }
        response.data.toDomain().toEmulatorAccessibleDetail()
    }

    override suspend fun deletePost(postId: Long): Result<Unit> = runCatching {
        val response = communityApi.deletePost(postId)
        if (!response.success) {
            throw IllegalStateException(response.message.ifBlank { "게시글을 삭제하지 못했어요." })
        }
    }

    override suspend fun getComments(postId: Long): Result<List<CommunityComment>> = runCatching {
        val response = communityApi.getComments(postId)
        if (!response.success || response.data == null) {
            throw IllegalStateException(response.message.ifBlank { "댓글을 불러오지 못했어요." })
        }
        response.data.sortedBy { it.createdAt }.map { it.toDomain(postId) }
    }

    override suspend fun createComment(
        postId: Long,
        content: String,
        parentCommentId: Long?
    ): Result<List<CommunityComment>> = runCatching {
        val response = communityApi.createComment(
            postId = postId,
            request = CommunityCommentRequestDto(content = content, parentCommentId = parentCommentId)
        )
        if (!response.success || response.data == null) {
            throw IllegalStateException(response.message.ifBlank { "댓글을 작성하지 못했어요." })
        }
        getComments(postId).getOrThrow()
    }

    override suspend fun updateComment(
        postId: Long,
        commentId: Long,
        content: String
    ): Result<List<CommunityComment>> = runCatching {
        val response = communityApi.updateComment(
            postId = postId,
            commentId = commentId,
            request = CommunityCommentRequestDto(content = content)
        )
        if (!response.success || response.data == null) {
            throw IllegalStateException(response.message.ifBlank { "댓글을 수정하지 못했어요." })
        }
        getComments(postId).getOrThrow()
    }

    override suspend fun deleteComment(
        postId: Long,
        commentId: Long
    ): Result<List<CommunityComment>> = runCatching {
        val response = communityApi.deleteComment(postId, commentId)
        if (!response.success) {
            throw IllegalStateException(response.message.ifBlank { "댓글을 삭제하지 못했어요." })
        }
        getComments(postId).getOrThrow()
    }

    override suspend fun likePost(postId: Long): Result<CommunityLikeResult> = runCatching {
        val response = communityApi.likePost(postId)
        if (!response.success || response.data == null) {
            throw IllegalStateException(response.message.ifBlank { "게시글 좋아요를 반영하지 못했어요." })
        }
        response.data.toDomain()
    }

    override suspend fun unlikePost(postId: Long): Result<CommunityLikeResult> = runCatching {
        val response = communityApi.unlikePost(postId)
        if (!response.success || response.data == null) {
            throw IllegalStateException(response.message.ifBlank { "게시글 좋아요를 반영하지 못했어요." })
        }
        response.data.toDomain()
    }

    override suspend fun likeComment(commentId: Long): Result<CommunityLikeResult> = runCatching {
        val response = communityApi.likeComment(commentId)
        if (!response.success || response.data == null) {
            throw IllegalStateException(response.message.ifBlank { "댓글 좋아요를 반영하지 못했어요." })
        }
        response.data.toDomain()
    }

    override suspend fun unlikeComment(commentId: Long): Result<CommunityLikeResult> = runCatching {
        val response = communityApi.unlikeComment(commentId)
        if (!response.success || response.data == null) {
            throw IllegalStateException(response.message.ifBlank { "댓글 좋아요를 반영하지 못했어요." })
        }
        response.data.toDomain()
    }

    override suspend fun issueVideoUploadTickets(
        videos: List<CommunityVideoUploadRequest>
    ): Result<List<CommunityVideoUploadTicket>> = runCatching {
        val response = communityApi.issueVideoUploadUrls(
            CommunityVideoUploadUrlRequestDto(videos.map { it.toDto() })
        )
        if (!response.success || response.data == null) {
            throw IllegalStateException(response.message.ifBlank { "영상 업로드 주소를 발급하지 못했어요." })
        }
        response.data.tickets.map { it.toDomain().copy(uploadUrl = toEmulatorAccessibleUrl(it.uploadUrl)) }
    }

    override suspend fun uploadVideo(
        ticket: CommunityVideoUploadTicket,
        draft: CommunityVideoDraft
    ): Result<CommunityVideoDraft> = withContext(Dispatchers.IO) {
        runCatching {
            val uploadUri = Uri.parse(ticket.uploadUrl)
            if (uploadUri.host.equals("minio", ignoreCase = true)) {
                throw IllegalStateException("서버가 기기에서 접근할 수 없는 내부 업로드 주소를 반환했어요.")
            }

            val localUri = requireNotNull(draft.localUri) { "로컬 영상 URI가 없어요." }
            val videoUri = Uri.parse(localUri)
            val requestBody = object : RequestBody() {
                override fun contentType() = draft.contentType.toMediaTypeOrNull()

                override fun contentLength(): Long = draft.fileSize

                override fun writeTo(sink: BufferedSink) {
                    openInputStream(videoUri).use { inputStream ->
                        requireNotNull(inputStream) { "업로드할 영상을 열지 못했어요." }
                        sink.writeAll(inputStream.source())
                    }
                }
            }

            val request = Request.Builder()
                .url(ticket.uploadUrl)
                .put(requestBody)
                .addHeader("Content-Type", draft.contentType)
                .build()

            directUploadOkHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("영상 업로드에 실패했어요. (HTTP ${response.code})")
                }
            }

            draft.copy(
                objectKey = ticket.objectKey,
                status = CommunityDraftVideoStatus.UPLOADED,
                errorMessage = null
            )
        }
    }

    override suspend fun getChallengeReferences(): Result<List<CommunityChallengeReference>> = runCatching {
        val challengesResponse = challengeApi.getChallenges()
        if (!challengesResponse.success || challengesResponse.data == null) {
            throw IllegalStateException(challengesResponse.message.ifBlank { "챌린지 참고 목록을 불러오지 못했어요." })
        }

        challengesResponse.data.map { challenge ->
            val attempts = runCatching { attemptApi.getAttempts(challenge.id) }.getOrNull()
                ?.takeIf { it.success && it.data != null }
                ?.data
                ?.attempts
                ?.map { it.toReference(challenge.id) }
                .orEmpty()

            challenge.toReference(attempts).enrichGymReference()
        }
    }

    private suspend fun uploadPendingVideos(
        drafts: List<CommunityVideoDraft>
    ): List<CommunityVideoDraft> {
        if (drafts.size > 3) {
            throw IllegalArgumentException("영상은 최대 3개까지 첨부할 수 있어요.")
        }

        val pending = drafts.filter { it.objectKey == null }
        if (pending.isEmpty()) {
            return drafts
        }

        val tickets = issueVideoUploadTickets(
            pending.map {
                CommunityVideoUploadRequest(
                    originalFileName = it.originalFileName,
                    contentType = it.contentType,
                    fileSize = it.fileSize
                )
            }
        ).getOrThrow()

        if (tickets.size != pending.size) {
            throw IllegalStateException("영상 업로드 준비에 필요한 정보가 일부 누락되었어요.")
        }

        val uploaded = pending.zip(tickets).map { (draft, ticket) ->
            uploadVideo(ticket, draft).getOrElse { throwable ->
                throw CommunityVideoUploadFailureException(
                    draftId = draft.id,
                    message = throwable.toUserFacingNetworkMessageOrNull()
                        ?: throwable.message
                        ?: "영상 업로드에 실패했어요."
                )
            }
        }

        val uploadedById = uploaded.associateBy { it.id }
        return drafts.map { draft -> uploadedById[draft.id] ?: draft }
    }

    override suspend fun createDraftVideo(
        uriString: String,
        sortOrder: Int
    ): Result<CommunityVideoDraft> = withContext(Dispatchers.IO) {
        runCatching {
            val uri = Uri.parse(uriString)
            val fileName = resolveFileName(uri)
            val contentType = resolveContentType(uri, fileName)
            val fileSize = resolveFileSize(uri)
            val durationMs = requireNotNull(resolveDuration(uri)?.takeIf { it > 0L }) {
                "영상 길이를 확인할 수 없어 준비하지 못했어요."
            }

            CommunityVideoDraft(
                id = uriString,
                localUri = uriString,
                originalFileName = fileName,
                contentType = contentType,
                fileSize = fileSize,
                durationMs = durationMs,
                sortOrder = sortOrder
            )
        }
    }

    private fun resolveFileName(uri: Uri): String {
        if (uri.scheme == "file") {
            return uri.path?.let { File(it).name } ?: "community_video.mp4"
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
                it.getString(0)?.takeIf(String::isNotBlank)?.let { name ->
                    return name
                }
            }
        }

        return "community_video.mp4"
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
            requireNotNull(inputStream) { "선택한 영상 크기를 읽지 못했어요." }
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

    private fun resolveDuration(uri: Uri): Long? {
        return runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong()
            }
        }.getOrNull()
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

    private fun toEmulatorAccessibleUrl(url: String): String {
        if (!BuildConfig.DEBUG) return url

        return runCatching {
            val uri = Uri.parse(url)
            val host = uri.host ?: return url
            if (host != "localhost" && host != "127.0.0.1") {
                return url
            }

            val authority = buildString {
                append("10.0.2.2")
                if (uri.port != -1) {
                    append(":").append(uri.port)
                }
            }

            uri.buildUpon()
                .encodedAuthority(authority)
                .build()
                .toString()
        }.getOrDefault(url)
    }

    private suspend fun CommunityChallengeReference.enrichGymReference(): CommunityChallengeReference {
        if (gymId != null) return this

        val resolvedGym = resolveGymByName(gymName) ?: return this
        return copy(
            gymId = resolvedGym.first,
            gymName = resolvedGym.second
        )
    }

    private suspend fun resolveGymByName(gymName: String): Pair<Long, String>? {
        val nearbyPlace = searchGymPlaceByName(gymName) ?: return null
        val response = runCatching {
            gymApi.resolveGym(
                ResolveGymRequestDto(
                    mapProvider = "KAKAO",
                    externalPlaceId = nearbyPlace.externalPlaceId,
                    placeName = nearbyPlace.placeName,
                    addressName = nearbyPlace.addressName,
                    roadAddressName = nearbyPlace.roadAddressName,
                    latitude = nearbyPlace.latitude,
                    longitude = nearbyPlace.longitude
                )
            )
        }.getOrNull() ?: return null

        val data = response.data ?: return null
        if (!response.success) return null

        val resolvedGym = data.toDomain()
        return resolvedGym.gymId.toLong() to resolvedGym.gym.displayName
    }

    private suspend fun searchGymPlaceByName(gymName: String): NearbyPlace? {
        val documents = runCatching {
            kakaoLocalApi.searchPlacesByKeyword(
                query = gymName,
                sort = null,
                size = 10
            ).documents
        }.getOrDefault(emptyList())

        val matchingDocument = documents
            .filter(::isClimbingRelevant)
            .sortedByDescending { scoreGymNameMatch(it, gymName) }
            .firstOrNull()
            ?: return null

        return matchingDocument.toDomainOrNull()
    }

    private fun isClimbingRelevant(document: KakaoPlaceDocumentDto): Boolean {
        val searchableText = buildString {
            append(document.placeName)
            append(' ')
            append(document.categoryName.orEmpty())
            append(' ')
            append(document.categoryGroupName.orEmpty())
        }.lowercase()

        val climbingKeywords = listOf(
            "클라이밍",
            "암벽",
            "암장",
            "볼더링",
            "climbing",
            "bouldering"
        )

        return climbingKeywords.any(searchableText::contains)
    }

    private fun scoreGymNameMatch(document: KakaoPlaceDocumentDto, gymName: String): Int {
        val normalizedPlaceName = document.placeName.normalizeGymText()
        val normalizedGymName = gymName.normalizeGymText()

        return when {
            normalizedPlaceName == normalizedGymName -> 3
            normalizedPlaceName.contains(normalizedGymName) -> 2
            normalizedGymName.contains(normalizedPlaceName) -> 1
            else -> 0
        }
    }

    private fun String.normalizeGymText(): String {
        return lowercase()
            .replace(" ", "")
            .replace("클라이밍", "")
            .replace("암벽", "")
            .replace("센터", "")
            .replace("gym", "")
            .replace("climbing", "")
    }

    private fun CommunityPostDetail.toEmulatorAccessibleDetail(): CommunityPostDetail =
        copy(videos = videos.map { it.copy(playbackUrl = toEmulatorAccessibleUrl(it.playbackUrl)) })
}
