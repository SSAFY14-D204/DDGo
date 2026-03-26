package com.ddgo.app.data.mapper

import com.ddgo.app.data.mapper.CommunityMapper.toDomain
import com.ddgo.app.data.remote.community.CommunityPostSummaryDto
import org.junit.Assert.assertEquals
import org.junit.Test

class CommunityMapperTest {

    @Test
    fun `community post summary dto maps thumbnail url`() {
        val dto = CommunityPostSummaryDto(
            id = 1L,
            title = "title",
            contentPreview = "preview",
            authorNickname = "tester",
            gymId = 2L,
            gymName = "DDGo Gym",
            createdAt = "2026-03-26T09:00:00",
            viewCount = 11,
            likeCount = 3,
            commentCount = 2,
            videoCount = 1,
            thumbnailUrl = "https://example.com/video.jpg",
            isLiked = true,
            isMine = false
        )

        val domain = dto.toDomain()

        assertEquals("https://example.com/video.jpg", domain.thumbnailUrl)
        assertEquals(1, domain.videoCount)
    }
}
