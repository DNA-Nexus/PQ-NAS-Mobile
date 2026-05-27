package com.pqnas.mobile.circlestack

import com.pqnas.mobile.api.CircleStackApi
import com.pqnas.mobile.api.CircleStackCreatePostRequest
import com.pqnas.mobile.api.CircleStackPostDto
import com.pqnas.mobile.api.CircleStackPostReactionRequest
import com.pqnas.mobile.api.CircleStackPostReplyRequest
import com.pqnas.mobile.api.CircleStackReplyDto
import retrofit2.HttpException

class CircleStackRepository(
    private val api: CircleStackApi
) {
    suspend fun feed(): List<CircleStackPostDto> {
        val response = api.feed(limit = 100)

        if (!response.ok) {
            throw IllegalStateException(response.message ?: response.error ?: "Could not load Circle Stack")
        }

        return response.posts
    }

    suspend fun createPost(
        text: String,
        visibility: String = "public"
    ): Long {
        val cleanText = text.trim()
        if (cleanText.isBlank()) {
            throw IllegalArgumentException("Write something first.")
        }

        val cleanVisibility = when (visibility.trim().lowercase()) {
            "public", "circle", "private" -> visibility.trim().lowercase()
            else -> "public"
        }

        val response = api.createPost(
            CircleStackCreatePostRequest(
                text = cleanText,
                visibility = cleanVisibility
            )
        )

        if (!response.ok || response.id <= 0L) {
            throw IllegalStateException(response.message ?: response.error ?: "Could not create Circle Stack post")
        }

        return response.id
    }

    suspend fun reactToPost(
        postId: Long,
        reaction: String
    ) {
        if (postId <= 0L) {
            throw IllegalArgumentException("Invalid post id.")
        }

        val cleanReaction = reaction.trim()
        if (cleanReaction.isNotBlank() && cleanReaction !in supportedReactions) {
            throw IllegalArgumentException("Unsupported reaction.")
        }

        val response = api.reactToPost(
            CircleStackPostReactionRequest(
                post_id = postId,
                reaction = cleanReaction
            )
        )

        if (!response.ok) {
            throw IllegalStateException(response.message ?: response.error ?: "Could not update reaction")
        }
    }

    suspend fun replyToPost(
        postId: Long,
        text: String
    ): CircleStackReplyDto {
        if (postId <= 0L) {
            throw IllegalArgumentException("Invalid post id.")
        }

        val cleanText = text.trim()
        if (cleanText.isBlank()) {
            throw IllegalArgumentException("Write a reply first.")
        }

        val response = api.replyToPost(
            CircleStackPostReplyRequest(
                post_id = postId,
                text = cleanText
            )
        )

        if (!response.ok || response.reply == null) {
            throw IllegalStateException(response.message ?: response.error ?: "Could not send reply")
        }

        return response.reply
    }

    companion object {
        val supportedReactions = setOf("👍", "❤️", "😂", "😮", "👏", "🔥")
    }
}

fun circleStackFriendlyMessage(action: String, error: Throwable): String {
    val http = (error as? HttpException)?.code()
    return when (http) {
        400 -> "$action failed: invalid request."
        401 -> "Session expired. Please pair again."
        403 -> "Access denied."
        404 -> "Circle Stack endpoint not found. Check that the app is installed and mobile-enabled on the server."
        409 -> "$action failed: conflict."
        413 -> "$action failed: post is too large."
        507 -> "$action failed: storage quota exceeded."
        else -> {
            val msg = error.message?.takeIf { it.isNotBlank() } ?: "unknown error"
            "$action failed: $msg"
        }
    }
}
