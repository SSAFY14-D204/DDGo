package com.ddgo.app.data.remote.community

import com.ddgo.app.data.remote.auth.EmptyDto
import com.ddgo.app.data.remote.common.ApiResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface CommunityApi {
    @GET("v1/community/posts")
    suspend fun getPosts(
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("keyword") keyword: String,
        @Query("sort") sort: String,
        @Query("gymId") gymId: Long? = null
    ): ApiResponse<CommunityPostPageResponseDto>

    @GET("v1/community/posts/{postId}")
    suspend fun getPostDetail(
        @Path("postId") postId: Long
    ): ApiResponse<CommunityPostDetailResponseDto>

    @POST("v1/community/posts")
    suspend fun createPost(
        @Body request: CommunityPostUpsertRequestDto
    ): ApiResponse<CommunityPostDetailResponseDto>

    @PATCH("v1/community/posts/{postId}")
    suspend fun updatePost(
        @Path("postId") postId: Long,
        @Body request: CommunityPostUpsertRequestDto
    ): ApiResponse<CommunityPostDetailResponseDto>

    @DELETE("v1/community/posts/{postId}")
    suspend fun deletePost(
        @Path("postId") postId: Long
    ): ApiResponse<EmptyDto>

    @GET("v1/community/posts/{postId}/comments")
    suspend fun getComments(
        @Path("postId") postId: Long
    ): ApiResponse<List<CommunityCommentDto>>

    @POST("v1/community/posts/{postId}/comments")
    suspend fun createComment(
        @Path("postId") postId: Long,
        @Body request: CommunityCommentRequestDto
    ): ApiResponse<CommunityCommentDto>

    @PATCH("v1/community/posts/{postId}/comments/{commentId}")
    suspend fun updateComment(
        @Path("postId") postId: Long,
        @Path("commentId") commentId: Long,
        @Body request: CommunityCommentRequestDto
    ): ApiResponse<CommunityCommentDto>

    @DELETE("v1/community/posts/{postId}/comments/{commentId}")
    suspend fun deleteComment(
        @Path("postId") postId: Long,
        @Path("commentId") commentId: Long
    ): ApiResponse<EmptyDto>

    @POST("v1/community/posts/{postId}/likes")
    suspend fun likePost(@Path("postId") postId: Long): ApiResponse<CommunityLikeResponseDto>

    @DELETE("v1/community/posts/{postId}/likes")
    suspend fun unlikePost(@Path("postId") postId: Long): ApiResponse<CommunityLikeResponseDto>

    @POST("v1/community/comments/{commentId}/likes")
    suspend fun likeComment(@Path("commentId") commentId: Long): ApiResponse<CommunityLikeResponseDto>

    @DELETE("v1/community/comments/{commentId}/likes")
    suspend fun unlikeComment(@Path("commentId") commentId: Long): ApiResponse<CommunityLikeResponseDto>

    @POST("v1/community/media/video-urls")
    suspend fun issueVideoUploadUrls(
        @Body request: CommunityVideoUploadUrlRequestDto
    ): ApiResponse<CommunityVideoUploadUrlResponseDto>
}
