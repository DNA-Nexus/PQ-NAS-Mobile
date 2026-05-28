package com.pqnas.mobile.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

data class CircleStackMentionDto(
    val fp: String = "",
    val fp_short: String = "",
    val display_name: String = "",
    val avatar_url: String = ""
)

data class CircleStackReactionDto(
    val reaction: String = "",
    val count: Long = 0L,
    val reacted_by_me: Boolean = false,
    val people: List<CircleStackMentionDto> = emptyList()
)

data class CircleStackReplyDto(
    val id: Long = 0L,
    val post_id: Long = 0L,
    val actor_fp: String = "",
    val actor_fp_short: String = "",
    val actor_display_name: String = "",
    val actor_avatar_url: String = "",
    val text: String = "",
    val created_epoch: Long = 0L,
    val is_mine: Boolean = false,
    val media_url: String = "",
    val mentions: List<CircleStackMentionDto> = emptyList(),
    val reactions: List<CircleStackReactionDto> = emptyList(),
    val my_reaction: String = ""
)

data class CircleStackPostDto(
    val id: Long = 0L,
    val text: String = "",
    val created_epoch: Long = 0L,
    val owner_fp: String = "",
    val owner_display_name: String = "",
    val owner_fp_short: String = "",
    val owner_avatar_url: String = "",
    val visibility: String = "public",
    val media_url: String = "",
    val mentions: List<CircleStackMentionDto> = emptyList(),
    val reactions: List<CircleStackReactionDto> = emptyList(),
    val replies: List<CircleStackReplyDto> = emptyList(),
    val my_reaction: String = ""
)

data class CircleStackFeedResponse(
    val ok: Boolean = false,
    val posts: List<CircleStackPostDto> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

data class CircleStackFederatedEventDto(
    val id: Long = 0L,
    val event_id: String = "",
    val event_type: String = "",
    val actor_display_name: String = "",
    val actor_fp: String = "",
    val actor_fp_short: String = "",
    val origin_label: String = "",
    val origin_nas: String = "",
    val created_epoch: Long = 0L,
    val received_epoch: Long = 0L,
    val source: String = "",
    val post_id: Long = 0L,
    val reply_id: Long = 0L,
    val target_type: String = "",
    val reaction: String = "",
    val text_preview: String = "",
    val payload: Map<String, Any?> = emptyMap()
)

data class CircleStackFederatedFeedResponse(
    val ok: Boolean = false,
    val count: Long = 0L,
    val events: List<CircleStackFederatedEventDto> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

data class CircleStackFederatedOriginDto(
    val origin_nas: String = "",
    val enabled: Boolean = true,
    val my_muted: Boolean = false,
    val my_hidden: Boolean = false
)

data class CircleStackFederatedOriginsResponse(
    val ok: Boolean = false,
    val items: List<CircleStackFederatedOriginDto> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

data class CircleStackCreatePostRequest(
    val text: String,
    val visibility: String = "public",
    val media_path: String = "",
    val circle_allow: String = "[]",
    val mentions: List<String> = emptyList()
)

data class CircleStackCreatePostResponse(
    val ok: Boolean = false,
    val id: Long = 0L,
    val error: String? = null,
    val message: String? = null
)

data class CircleStackPostReactionRequest(
    val post_id: Long,
    val reaction: String
)

data class CircleStackPostReactionResponse(
    val ok: Boolean = false,
    val post_id: Long = 0L,
    val reaction: String = "",
    val error: String? = null,
    val message: String? = null
)

data class CircleStackPostReplyRequest(
    val post_id: Long,
    val text: String,
    val media_path: String = "",
    val mentions: List<String> = emptyList()
)

data class CircleStackPostReplyResponse(
    val ok: Boolean = false,
    val id: Long = 0L,
    val post_id: Long = 0L,
    val reply: CircleStackReplyDto? = null,
    val error: String? = null,
    val message: String? = null
)

interface CircleStackApi {
    @GET("/api/v4/circlestack/feed")
    suspend fun feed(
        @Query("limit") limit: Int = 100,
        @Query("mode") mode: String? = null
    ): CircleStackFeedResponse

    @GET("/api/v4/circlestack/federated/feed")
    suspend fun federatedFeed(
        @Query("limit") limit: Int = 50
    ): CircleStackFederatedFeedResponse

    @GET("/api/v4/circlestack/federated/origins")
    suspend fun federatedOrigins(): CircleStackFederatedOriginsResponse

    @Headers("Content-Type: application/json")
    @POST("/api/v4/circlestack/posts/create")
    suspend fun createPost(
        @Body request: CircleStackCreatePostRequest
    ): CircleStackCreatePostResponse

    @Headers("Content-Type: application/json")
    @POST("/api/v4/circlestack/posts/react")
    suspend fun reactToPost(
        @Body request: CircleStackPostReactionRequest
    ): CircleStackPostReactionResponse

    @Headers("Content-Type: application/json")
    @POST("/api/v4/circlestack/posts/reply")
    suspend fun replyToPost(
        @Body request: CircleStackPostReplyRequest
    ): CircleStackPostReplyResponse
}
