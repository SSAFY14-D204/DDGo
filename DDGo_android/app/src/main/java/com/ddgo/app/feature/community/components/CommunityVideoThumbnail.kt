package com.ddgo.app.feature.community.components

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.media3.common.VideoSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import wseemann.media.FFmpegMediaMetadataRetriever

internal sealed interface CommunityVideoThumbnailState {
    val isPortrait: Boolean?

    data object Loading : CommunityVideoThumbnailState {
        override val isPortrait: Boolean? = null
    }

    data class Success(
        val bitmap: Bitmap,
        override val isPortrait: Boolean
    ) : CommunityVideoThumbnailState

    data class Error(
        override val isPortrait: Boolean? = null
    ) : CommunityVideoThumbnailState
}

@Composable
internal fun rememberCommunityVideoThumbnailState(
    playbackUrl: String
): CommunityVideoThumbnailState {
    return produceState<CommunityVideoThumbnailState>(
        initialValue = CommunityVideoThumbnailState.Loading,
        key1 = playbackUrl
    ) {
        value = loadCommunityVideoThumbnailState(playbackUrl)
    }.value
}

internal fun resolveCommunityVideoIsPortrait(videoSize: VideoSize): Boolean? {
    if (videoSize.width <= 0 || videoSize.height <= 0) {
        return null
    }

    val sourceWidth = videoSize.width * videoSize.pixelWidthHeightRatio
    val sourceHeight = videoSize.height.toFloat()
    val isRotated = videoSize.unappliedRotationDegrees % 180 != 0
    val displayedWidth = if (isRotated) sourceHeight else sourceWidth
    val displayedHeight = if (isRotated) sourceWidth else sourceHeight
    if (displayedWidth <= 0f || displayedHeight <= 0f) {
        return null
    }

    return displayedWidth / displayedHeight < 1f
}

private suspend fun loadCommunityVideoThumbnailState(
    playbackUrl: String
): CommunityVideoThumbnailState = withContext(Dispatchers.IO) {
    val metadata = loadCommunityVideoMetadata(playbackUrl)
    val bitmap = loadCommunityVideoFrame(playbackUrl)

    when {
        bitmap != null -> CommunityVideoThumbnailState.Success(
            bitmap = bitmap,
            isPortrait = metadata?.isPortrait ?: (bitmap.height > bitmap.width)
        )

        else -> CommunityVideoThumbnailState.Error(isPortrait = metadata?.isPortrait)
    }
}

private fun loadCommunityVideoMetadata(playbackUrl: String): CommunityVideoMetadata? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(playbackUrl, emptyMap())
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull()
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull()
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            ?.toIntOrNull()
            ?: 0

        resolveDisplayedAspectRatio(
            width = width,
            height = height,
            rotationDegrees = rotation
        )?.let { aspectRatio ->
            CommunityVideoMetadata(isPortrait = aspectRatio < 1f)
        }
    } catch (_: Throwable) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

private fun loadCommunityVideoFrame(playbackUrl: String): Bitmap? {
    val retriever = FFmpegMediaMetadataRetriever()
    return try {
        retriever.setDataSource(playbackUrl)
        retriever.getFrameAtTime(
            COMMUNITY_THUMBNAIL_TIME_US,
            FFmpegMediaMetadataRetriever.OPTION_CLOSEST
        )
    } catch (_: Throwable) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

private fun resolveDisplayedAspectRatio(
    width: Int?,
    height: Int?,
    rotationDegrees: Int
): Float? {
    val safeWidth = width?.takeIf { it > 0 } ?: return null
    val safeHeight = height?.takeIf { it > 0 } ?: return null

    val displayedWidth = if (rotationDegrees % 180 != 0) {
        safeHeight.toFloat()
    } else {
        safeWidth.toFloat()
    }
    val displayedHeight = if (rotationDegrees % 180 != 0) {
        safeWidth.toFloat()
    } else {
        safeHeight.toFloat()
    }
    if (displayedWidth <= 0f || displayedHeight <= 0f) {
        return null
    }

    return displayedWidth / displayedHeight
}

private data class CommunityVideoMetadata(
    val isPortrait: Boolean
)

private const val COMMUNITY_THUMBNAIL_TIME_US = 1_000_000L
