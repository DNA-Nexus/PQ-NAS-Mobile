package com.pqnas.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.pqnas.mobile.api.CircleStackPostDto
import com.pqnas.mobile.circlestack.CircleStackRepository
import com.pqnas.mobile.circlestack.circleStackFriendlyMessage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CircleBg = Color(0xFF071018)
private val CirclePanel = Color(0xFF121C28)
private val CirclePanelSoft = Color(0xFF1D2B3A)
private val CircleLine = Color(0xFF31506A)
private val CircleAccent = Color(0xFF72E0FF)
private val CircleAccentSoft = Color(0xFF9BEAFF)
private val CircleText = Color(0xFFF4F8FB)
private val CircleMuted = Color(0xFFAFC0CE)
private val CircleGood = Color(0xFF87E69B)
private val CircleBad = Color(0xFFFF7A7A)
private val CircleReactionOptions = listOf("👍", "❤️", "😂", "😮", "👏", "🔥")

@Composable
fun CircleStackScreen(
    repository: CircleStackRepository,
    baseUrl: String,
    imageLoader: ImageLoader,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var posts by remember { mutableStateOf<List<CircleStackPostDto>>(emptyList()) }
    var newText by remember { mutableStateOf("") }
    var composerExpanded by remember { mutableStateOf(false) }
    var visibility by remember { mutableStateOf("public") }
    var status by remember { mutableStateOf("Loading Circle Stack...") }
    var loading by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var previewImageUrl by remember { mutableStateOf<String?>(null) }

    fun loadFeed() {
        scope.launch {
            loading = true
            status = "Loading Circle Stack..."

            runCatching {
                repository.feed()
            }.onSuccess { loaded ->
                posts = loaded
                status = if (loaded.isEmpty()) {
                    "No Circle Stack posts yet."
                } else {
                    "Ready."
                }
            }.onFailure { e ->
                status = circleStackFriendlyMessage("Load", e)
            }

            loading = false
        }
    }

    fun createPost() {
        val text = newText.trim()
        if (text.isBlank()) {
            status = "Write something first."
            return
        }

        scope.launch {
            creating = true
            status = "Publishing Circle Stack post..."

            runCatching {
                repository.createPost(
                    text = text,
                    visibility = visibility
                )
            }.onSuccess {
                newText = ""
                composerExpanded = false
                status = "Posted."
                loadFeed()
            }.onFailure { e ->
                status = circleStackFriendlyMessage("Post", e)
            }

            creating = false
        }
    }

    fun reactToPost(post: CircleStackPostDto, reaction: String) {
        val nextReaction = if (post.my_reaction == reaction) "" else reaction

        scope.launch {
            status = "Updating reaction..."

            runCatching {
                repository.reactToPost(post.id, nextReaction)
            }.onSuccess {
                status = "Reaction updated."
                loadFeed()
            }.onFailure { e ->
                status = circleStackFriendlyMessage("Reaction", e)
            }
        }
    }

    fun replyToPost(post: CircleStackPostDto, replyText: String) {
        val cleanReply = replyText.trim()
        if (cleanReply.isBlank()) {
            status = "Write a reply first."
            return
        }

        scope.launch {
            status = "Sending reply..."

            runCatching {
                repository.replyToPost(post.id, cleanReply)
            }.onSuccess {
                status = "Reply sent."
                loadFeed()
            }.onFailure { e ->
                status = circleStackFriendlyMessage("Reply", e)
            }
        }
    }

    LaunchedEffect(Unit) {
        loadFeed()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = CircleBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CircleBg)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DNA-NEXUS",
                        style = MaterialTheme.typography.labelSmall,
                        color = CircleAccent,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Circle Stack",
                        style = MaterialTheme.typography.headlineLarge,
                        color = CircleText,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Private social feed from your own NAS.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CircleMuted
                    )
                }

                TextButton(onClick = onClose) {
                    Text("Back", color = CircleAccentSoft)
                }
            }

            if (!composerExpanded && newText.isBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { composerExpanded = true },
                    colors = CardDefaults.cardColors(containerColor = CirclePanel),
                    border = BorderStroke(1.dp, CircleLine)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Write a Circle Stack post...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CircleMuted,
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            text = "+",
                            style = MaterialTheme.typography.titleMedium,
                            color = CircleAccent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CirclePanel),
                    border = BorderStroke(1.dp, CircleLine)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = newText,
                            onValueChange = { newText = it },
                            label = { Text("New post") },
                            placeholder = { Text("What do you want to share?") },
                            minLines = 3,
                            maxLines = 8,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircleVisibilityButton(
                                label = "Public",
                                selected = visibility == "public",
                                onClick = { visibility = "public" }
                            )

                            CircleVisibilityButton(
                                label = "Circle",
                                selected = visibility == "circle",
                                onClick = { visibility = "circle" }
                            )

                            CircleVisibilityButton(
                                label = "Private",
                                selected = visibility == "private",
                                onClick = { visibility = "private" }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    if (newText.isBlank()) {
                                        composerExpanded = false
                                    } else {
                                        newText = ""
                                        composerExpanded = false
                                    }
                                },
                                enabled = !creating
                            ) {
                                Text(
                                    text = if (newText.isBlank()) "Collapse" else "Clear",
                                    color = CircleMuted
                                )
                            }

                            Button(
                                onClick = { createPost() },
                                enabled = !creating,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CirclePanelSoft,
                                    contentColor = CircleText,
                                    disabledContainerColor = CirclePanelSoft.copy(alpha = 0.55f),
                                    disabledContentColor = CircleMuted
                                )
                            ) {
                                Text(if (creating) "Posting..." else "Post")
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        status == "Ready." || status == "Posted." -> CircleGood
                        status.contains("failed", ignoreCase = true) ||
                                status.contains("denied", ignoreCase = true) ||
                                status.contains("expired", ignoreCase = true) ||
                                status.contains("not found", ignoreCase = true) -> CircleBad
                        else -> CircleMuted
                    },
                    modifier = Modifier.weight(1f)
                )

                TextButton(
                    onClick = { loadFeed() },
                    enabled = !loading
                ) {
                    Text("Refresh", color = CircleAccentSoft)
                }
            }

            if (loading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = CircleAccent,
                    trackColor = CirclePanelSoft
                )
            }

            if (posts.isEmpty() && !loading) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = CirclePanel.copy(alpha = 0.72f)
                    ),
                    border = BorderStroke(1.dp, CircleLine.copy(alpha = 0.75f))
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No posts yet. Write the first Circle Stack post above.",
                            color = CircleMuted,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = posts,
                        key = { it.id }
                    ) { post ->
                        CirclePostCard(
                            post = post,
                            baseUrl = baseUrl,
                            imageLoader = imageLoader,
                            onReact = { targetPost, reaction ->
                                reactToPost(targetPost, reaction)
                            },
                            onReply = { targetPost, replyText ->
                                replyToPost(targetPost, replyText)
                            },
                            onOpenImage = { url ->
                                previewImageUrl = url
                            }
                        )
                    }
                }
            }
        }
    }

    previewImageUrl?.let { imageUrl ->
        Dialog(
            onDismissRequest = { previewImageUrl = null }
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                colors = CardDefaults.cardColors(containerColor = CirclePanel),
                border = BorderStroke(1.dp, CircleLine)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageUrl)
                            .crossfade(true)
                            .build(),
                        imageLoader = imageLoader,
                        contentDescription = "Circle Stack image preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(520.dp)
                            .background(CircleBg)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { previewImageUrl = null }) {
                            Text("Close", color = CircleAccentSoft)
                        }
                    }
                }
            }
        }
    }
    }
@Composable
private fun CircleVisibilityButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (selected) CircleAccent else CircleMuted
        )
    ) {
        Text(
            text = if (selected) "● $label" else label,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun CirclePostCard(
    post: CircleStackPostDto,
    baseUrl: String,
    imageLoader: ImageLoader,
    onReact: (CircleStackPostDto, String) -> Unit,
    onReply: (CircleStackPostDto, String) -> Unit,
    onOpenImage: (String) -> Unit
) {
    val context = LocalContext.current
    var reactionPickerExpanded by remember(post.id) { mutableStateOf(false) }
    var replyExpanded by remember(post.id) { mutableStateOf(false) }
    var replyText by remember(post.id) { mutableStateOf("") }
    val owner = post.owner_display_name
        .ifBlank { post.owner_fp_short }
        .ifBlank { "Unknown user" }

    val reactionSummary = post.reactions
        .filter { it.count > 0L && it.reaction.isNotBlank() }
        .joinToString(" ") { "${it.reaction} ${it.count}" }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CirclePanel),
        border = BorderStroke(1.dp, CircleLine.copy(alpha = 0.8f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = owner,
                        style = MaterialTheme.typography.titleMedium,
                        color = CircleText,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = formatCircleEpoch(post.created_epoch),
                        style = MaterialTheme.typography.bodySmall,
                        color = CircleMuted
                    )
                }

                Text(
                    text = post.visibility.uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = CircleAccent,
                    fontWeight = FontWeight.Bold
                )
            }

            if (post.text.isNotBlank()) {
                Text(
                    text = post.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = CircleText
                )
            } else if (post.media_url.isNotBlank()) {
                Text(
                    text = "Media post",
                    style = MaterialTheme.typography.bodyLarge,
                    color = CircleText
                )
            }

            if (post.media_url.isNotBlank()) {
                val mediaUrl = circleStackFullUrl(baseUrl, post.media_url)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(CirclePanelSoft)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(mediaUrl)
                            .crossfade(true)
                            .build(),
                        imageLoader = imageLoader,
                        contentDescription = "Circle Stack media",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { onOpenImage(mediaUrl) }
                    ) {
                        Text("Open image", color = CircleAccentSoft)
                    }
                }
            }

            HorizontalDivider(color = CircleLine.copy(alpha = 0.45f))

            if (reactionSummary.isNotBlank()) {
                Text(
                    text = reactionSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = CircleMuted
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { reactionPickerExpanded = !reactionPickerExpanded },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (post.my_reaction.isNotBlank()) CircleAccent else CircleMuted
                    )
                ) {
                    Text(
                        text = if (post.my_reaction.isNotBlank()) {
                            "${post.my_reaction} React"
                        } else {
                            "👍 React"
                        },
                        fontWeight = if (post.my_reaction.isNotBlank()) FontWeight.Bold else FontWeight.Normal
                    )
                }

                if (post.replies.isNotEmpty()) {
                    Text(
                        text = "${post.replies.size} replies",
                        style = MaterialTheme.typography.bodySmall,
                        color = CircleMuted
                    )
                }
            }

            if (reactionPickerExpanded) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(CircleReactionOptions) { reaction ->
                        val selected = post.my_reaction == reaction

                        TextButton(
                            onClick = {
                                reactionPickerExpanded = false
                                onReact(post, reaction)
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (selected) CircleAccent else CircleMuted
                            )
                        ) {
                            Text(
                                text = reaction,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            if (post.replies.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    post.replies.take(3).forEach { reply ->
                        val replyOwner = reply.actor_display_name
                            .ifBlank { reply.actor_fp_short }
                            .ifBlank { "Unknown user" }

                        Text(
                            text = "$replyOwner: ${reply.text}",
                            style = MaterialTheme.typography.bodySmall,
                            color = CircleMuted
                        )
                    }

                    if (post.replies.size > 3) {
                        Text(
                            text = "+${post.replies.size - 3} more replies",
                            style = MaterialTheme.typography.bodySmall,
                            color = CircleMuted
                        )
                    }
                }
            }

            if (replyExpanded) {
                OutlinedTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    label = { Text("Reply") },
                    placeholder = { Text("Write a reply...") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            replyText = ""
                            replyExpanded = false
                        }
                    ) {
                        Text("Cancel", color = CircleMuted)
                    }

                    Button(
                        onClick = {
                            val outgoing = replyText
                            replyText = ""
                            replyExpanded = false
                            onReply(post, outgoing)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CirclePanelSoft,
                            contentColor = CircleText
                        )
                    ) {
                        Text("Send")
                    }
                }
            } else {
                TextButton(
                    onClick = { replyExpanded = true }
                ) {
                    Text(
                        text = if (post.replies.isEmpty()) "Reply" else "Reply • ${post.replies.size}",
                        color = CircleAccentSoft
                    )
                }
            }
        }
    }
}

private fun circleStackFullUrl(baseUrl: String, url: String): String {
    if (url.isBlank()) return ""
    if (url.startsWith("http://", ignoreCase = true) ||
        url.startsWith("https://", ignoreCase = true)
    ) {
        return url
    }

    val base = baseUrl.trim().trimEnd('/')
    val rel = if (url.startsWith("/")) url else "/$url"
    return "$base$rel"
}

private fun formatCircleEpoch(epoch: Long): String {
    if (epoch <= 0L) return ""
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        .format(Date(epoch * 1000L))
}
