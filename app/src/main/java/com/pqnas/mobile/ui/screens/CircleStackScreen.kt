package com.pqnas.mobile.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.pqnas.mobile.api.CircleStackFederatedEventDto
import com.pqnas.mobile.api.CircleStackPostDto
import com.pqnas.mobile.api.FileItemDto
import com.pqnas.mobile.circlestack.CircleStackRepository
import com.pqnas.mobile.circlestack.circleStackFriendlyMessage
import com.pqnas.mobile.files.FilesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.platform.LocalConfiguration

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

private enum class CircleStackFeedMode(
    val apiValue: String,
    val label: String,
    val subtitle: String
) {
    Feed(
        apiValue = "feed",
        label = "Feed",
        subtitle = "All visible Circle Stack posts"
    ),
    Federated(
        apiValue = "federated",
        label = "Federated",
        subtitle = "Posts received from other connected servers"
    ),
    MyCircle(
        apiValue = "my_circle",
        label = "My circle",
        subtitle = "People and posts closest to your own circle"
    ),
    Discover(
        apiValue = "discover",
        label = "Discover",
        subtitle = "Extended circle and wider public discovery"
    );

    fun next(): CircleStackFeedMode = when (this) {
        Feed -> Federated
        Federated -> MyCircle
        MyCircle -> Discover
        Discover -> Feed
    }
}

@Composable
fun CircleStackScreen(
    repository: CircleStackRepository,
    filesRepository: FilesRepository,
    baseUrl: String,
    imageLoader: ImageLoader,
    onBeforeExternalPicker: () -> Unit = {},
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var posts by remember { mutableStateOf<List<CircleStackPostDto>>(emptyList()) }
    var federatedEvents by remember { mutableStateOf<List<CircleStackFederatedEventDto>>(emptyList()) }
    var feedMode by remember { mutableStateOf(CircleStackFeedMode.Feed) }
    var newText by remember { mutableStateOf("") }
    var composerExpanded by remember { mutableStateOf(false) }
    var visibility by remember { mutableStateOf("public") }
    var status by remember { mutableStateOf("Loading Circle Stack...") }
    var loading by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var uploadingImage by remember { mutableStateOf(false) }
    var pendingImageUri by remember { mutableStateOf<Uri?>(null) }
    var pendingImageName by remember { mutableStateOf("") }
    var pendingImageMime by remember { mutableStateOf<String?>(null) }

    var selectedNasImagePath by remember { mutableStateOf("") }
    var showNasImagePicker by remember { mutableStateOf(false) }
    var nasPickerPath by remember { mutableStateOf<String?>(null) }
    var nasPickerItems by remember { mutableStateOf<List<FileItemDto>>(emptyList()) }
    var nasPickerLoading by remember { mutableStateOf(false) }
    var nasPickerStatus by remember { mutableStateOf("") }

    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    var previewImageUsesTrustedLoader by remember { mutableStateOf(true) }

    fun loadNasImagePicker(path: String?) {
        nasPickerPath = path?.trim('/')?.ifBlank { null }
        nasPickerLoading = true
        nasPickerStatus = "Loading..."

        scope.launch {
            runCatching {
                filesRepository.list(nasPickerPath)
            }.onSuccess { response ->
                nasPickerPath = response.path.ifBlank { null }

                nasPickerItems = response.items
                    .filter { item ->
                        !circleStackShouldHidePickerItem(item.name) &&
                                (item.type == "dir" || circleStackIsImageFile(item.name))
                    }
                    .sortedWith(
                        compareBy<FileItemDto> { if (it.type == "dir") 0 else 1 }
                            .thenBy { it.name.lowercase(Locale.getDefault()) }
                    )

                nasPickerStatus = if (nasPickerItems.isEmpty()) {
                    "No image files here."
                } else {
                    ""
                }
            }.onFailure { e ->
                nasPickerItems = emptyList()
                nasPickerStatus = circleStackFriendlyMessage("Load files", e)
            }

            nasPickerLoading = false
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        pendingImageUri = uri
        pendingImageMime = context.contentResolver.getType(uri) ?: "image/jpeg"
        pendingImageName = circleStackDisplayName(context, uri)
            .ifBlank { circleStackFallbackImageName(pendingImageMime) }

        composerExpanded = true
        status = "Image attached: $pendingImageName"
    }

    fun loadFeed(mode: CircleStackFeedMode = feedMode) {
        scope.launch {
            loading = true
            status = "Loading ${mode.label}..."

            runCatching {
                if (mode == CircleStackFeedMode.Feed) {
                    val loaded = repository.feed(mode = "feed")
                    posts = loaded
                    federatedEvents = emptyList()
                    loaded.size
                } else {
                    val loaded = repository.federatedFeed(mode = mode.apiValue)
                    posts = emptyList()
                    federatedEvents = loaded
                    loaded.size
                }
            }.onSuccess { count ->
                status = if (count <= 0) {
                    "No posts in ${mode.label}."
                } else {
                    "${mode.label} ready."
                }
            }.onFailure { e ->
                status = circleStackFriendlyMessage("Load ${mode.label}", e)
            }

            loading = false
        }
    }

    fun createPost() {
        val text = newText.trim()
        val imageUri = pendingImageUri
        val nasMediaPath = selectedNasImagePath.trim('/')

        if (text.isBlank() && imageUri == null && nasMediaPath.isBlank()) {
            status = "Write something or attach an image first."
            return
        }

        scope.launch {
            creating = true
            uploadingImage = imageUri != null
            status = when {
                nasMediaPath.isNotBlank() -> "Publishing Circle Stack post..."
                imageUri != null -> "Importing image to cloud storage..."
                else -> "Publishing Circle Stack post..."
            }

            var stagedFile: File? = null

            runCatching {
                val mediaPath = when {
                    nasMediaPath.isNotBlank() -> {
                        nasMediaPath
                    }

                    imageUri != null -> {
                        val safeName = circleStackSafeUploadName(
                            pendingImageName.ifBlank { circleStackFallbackImageName(pendingImageMime) },
                            pendingImageMime
                        )

                        val targetPath =
                            ".pqnas_circlestack/mobile_uploads/${System.currentTimeMillis()}_$safeName"

                        runCatching { filesRepository.mkdir(".pqnas_circlestack") }
                        runCatching { filesRepository.mkdir(".pqnas_circlestack/mobile_uploads") }

                        stagedFile = stageCircleStackImageUri(
                            context = context,
                            uri = imageUri,
                            fallbackName = safeName
                        )

                        filesRepository.uploadChunkedFromTempFile(
                            path = targetPath,
                            file = stagedFile!!,
                            mimeType = pendingImageMime,
                            overwrite = false
                        )

                        targetPath
                    }

                    else -> ""
                }

                status = "Publishing Circle Stack post..."

                repository.createPost(
                    text = text,
                    visibility = visibility,
                    mediaPath = mediaPath
                )
            }.onSuccess {
                newText = ""
                selectedNasImagePath = ""
                pendingImageUri = null
                pendingImageName = ""
                pendingImageMime = null
                composerExpanded = false
                status = "Posted."
                loadFeed(feedMode)
            }.onFailure { e ->
                status = circleStackFriendlyMessage("Post", e)
            }

            runCatching { stagedFile?.delete() }
            uploadingImage = false
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
                loadFeed(feedMode)
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
                loadFeed(feedMode)
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

                        if (selectedNasImagePath.isBlank() && pendingImageUri == null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        showNasImagePicker = true
                                        loadNasImagePicker(null)
                                    },
                                    enabled = !creating,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CirclePanelSoft,
                                        contentColor = CircleText
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Choose from cloud storage")
                                }

                                TextButton(
                                    onClick = {
                                        onBeforeExternalPicker()
                                        imagePickerLauncher.launch(arrayOf("image/*"))
                                    },
                                    enabled = !creating,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Import from phone", color = CircleAccentSoft)
                                }
                            }
                        } else {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = CirclePanelSoft.copy(alpha = 0.55f)
                                ),
                                border = BorderStroke(1.dp, CircleLine.copy(alpha = 0.45f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = if (selectedNasImagePath.isNotBlank()) {
                                            "Cloud image: /$selectedNasImagePath"
                                        } else {
                                            "Imported image: ${pendingImageName.ifBlank { "attached" }}"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CircleText,
                                        modifier = Modifier.weight(1f)
                                    )

                                    TextButton(
                                        onClick = {
                                            selectedNasImagePath = ""
                                            pendingImageUri = null
                                            pendingImageName = ""
                                            pendingImageMime = null
                                        },
                                        enabled = !creating
                                    ) {
                                        Text("Remove", color = CircleMuted)
                                    }
                                }
                            }
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
                                Text(
                                    when {
                                        uploadingImage -> "Uploading..."
                                        creating -> "Posting..."
                                        else -> "Post"
                                    }
                                )
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
                    onClick = {
                        val nextMode = feedMode.next()
                        feedMode = nextMode
                        loadFeed(nextMode)
                    },
                    enabled = !loading
                ) {
                    Text(feedMode.label, color = CircleAccent)
                }

                TextButton(
                    onClick = { loadFeed(feedMode) },
                    enabled = !loading
                ) {
                    Text("Refresh", color = CircleAccentSoft)
                }
            }

            Text(
                text = feedMode.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = CircleMuted
            )

            if (loading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = CircleAccent,
                    trackColor = CirclePanelSoft
                )
            }

            val emptyForMode = if (feedMode == CircleStackFeedMode.Feed) {
                posts.isEmpty()
            } else {
                federatedEvents.isEmpty()
            }

            if (emptyForMode && !loading) {
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
                            text = if (feedMode == CircleStackFeedMode.Feed) {
                                "No posts yet. Write the first Circle Stack post above."
                            } else {
                                "No ${feedMode.label} events yet."
                            },
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
                    if (feedMode == CircleStackFeedMode.Feed) {
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
                                    previewImageUsesTrustedLoader = true
                                    previewImageUrl = url
                                }
                            )
                        }
                    } else {
                        items(
                            items = federatedEvents,
                            key = { ev -> ev.id }
                        ) { ev ->
                            CircleFederatedEventCard(
                                event = ev,
                                mode = feedMode,
                                onOpenImage = { url ->
                                    previewImageUsesTrustedLoader = false
                                    previewImageUrl = url
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNasImagePicker) {
        Dialog(
            onDismissRequest = { showNasImagePicker = false }
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                colors = CardDefaults.cardColors(containerColor = CirclePanel),
                border = BorderStroke(1.dp, CircleLine)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Choose image from cloud storage",
                        style = MaterialTheme.typography.titleMedium,
                        color = CircleText,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Path: /${nasPickerPath.orEmpty()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = CircleMuted
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { loadNasImagePicker(null) },
                            enabled = !nasPickerLoading
                        ) {
                            Text("Root", color = CircleAccentSoft)
                        }

                        TextButton(
                            onClick = { loadNasImagePicker(circleStackParentPath(nasPickerPath)) },
                            enabled = !nasPickerLoading && !nasPickerPath.isNullOrBlank()
                        ) {
                            Text("Up", color = CircleAccentSoft)
                        }

                        TextButton(
                            onClick = { loadNasImagePicker(nasPickerPath) },
                            enabled = !nasPickerLoading
                        ) {
                            Text("Refresh", color = CircleAccentSoft)
                        }
                    }

                    if (nasPickerLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = CircleAccent,
                            trackColor = CirclePanelSoft
                        )
                    }

                    if (nasPickerStatus.isNotBlank()) {
                        Text(
                            text = nasPickerStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = CircleMuted
                        )
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(
                            items = nasPickerItems,
                            key = { item -> "${item.type}:${nasPickerPath.orEmpty()}/${item.name}" }
                        ) { item ->
                            val itemPath = circleStackJoinPath(nasPickerPath, item.name)

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (item.type == "dir") {
                                            loadNasImagePicker(itemPath)
                                        } else {
                                            selectedNasImagePath = itemPath
                                            pendingImageUri = null
                                            pendingImageName = ""
                                            pendingImageMime = null
                                            showNasImagePicker = false
                                            composerExpanded = true
                                            status = "Image selected from cloud storage."
                                        }
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = CirclePanelSoft.copy(alpha = 0.55f)
                                ),
                                border = BorderStroke(1.dp, CircleLine.copy(alpha = 0.32f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (item.type == "dir") {
                                        Text(
                                            text = "📁",
                                            color = CircleText
                                        )
                                    } else {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(circleStackFileGetUrl(baseUrl, itemPath))
                                                .crossfade(true)
                                                .build(),
                                            imageLoader = imageLoader,
                                            contentDescription = "Image preview",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(46.dp)
                                                .background(CircleBg)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = CircleText
                                        )

                                        Text(
                                            text = if (item.type == "dir") "Folder" else "/$itemPath",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = CircleMuted
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showNasImagePicker = false }) {
                            Text("Close", color = CircleAccentSoft)
                        }
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
                    CircleZoomablePreviewImage(
                        imageUrl = imageUrl,
                        imageLoader = imageLoader,
                        useTrustedImageLoader = previewImageUsesTrustedLoader,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(520.dp)
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
private fun CircleZoomablePreviewImage(
    imageUrl: String,
    imageLoader: ImageLoader,
    useTrustedImageLoader: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var scale by remember(imageUrl) { mutableStateOf(1f) }
    var offsetX by remember(imageUrl) { mutableStateOf(0f) }
    var offsetY by remember(imageUrl) { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .background(CircleBg)
            .pointerInput(imageUrl) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val nextScale = (scale * zoom).coerceIn(1f, 5f)
                    scale = nextScale

                    if (nextScale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            }
    ) {
        val imageModifier = Modifier
            .fillMaxSize()
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offsetX,
                translationY = offsetY
            )

        if (useTrustedImageLoader) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
                imageLoader = imageLoader,
                contentDescription = "Circle Stack image preview",
                contentScale = ContentScale.Fit,
                modifier = imageModifier
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Circle Stack federated image preview",
                contentScale = ContentScale.Fit,
                modifier = imageModifier
            )
        }

        Text(
            text = if (scale <= 1.01f) "Pinch to zoom" else "Drag image • ${"%.1f".format(scale)}x",
            style = MaterialTheme.typography.labelSmall,
            color = CircleText,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .background(CircleBg.copy(alpha = 0.72f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun CircleFederatedEventCard(
    event: CircleStackFederatedEventDto,
    mode: CircleStackFeedMode,
    onOpenImage: (String) -> Unit
) {
    val context = LocalContext.current
    val displayName = event.actor_display_name
        .ifBlank { circleStackPayloadString(event, "owner_display_name") }
        .ifBlank { event.origin_label }
        .ifBlank { event.actor_fp_short }
        .ifBlank { "Remote user" }

    val textPreview = event.text_preview
        .ifBlank { circleStackPayloadString(event, "text_preview") }

    val typeLabel = when (event.event_type) {
        "circle.post.created" -> "Federated post"
        "circle.reply.created" -> "Federated reply"
        "circle.reaction.created" -> "Federated reaction"
        else -> event.event_type.ifBlank { "Federated event" }
    }

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
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = CircleText,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = formatCircleEpoch(
                            if (event.created_epoch > 0L) event.created_epoch else event.received_epoch
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = CircleMuted
                    )
                }

                Text(
                    text = mode.label.uppercase(LocalConfiguration.current.locales[0]),
                    style = MaterialTheme.typography.labelSmall,
                    color = CircleAccent,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = typeLabel,
                style = MaterialTheme.typography.labelMedium,
                color = CircleAccentSoft,
                fontWeight = FontWeight.Bold
            )

            if (textPreview.isNotBlank()) {
                Text(
                    text = textPreview,
                    style = MaterialTheme.typography.bodyLarge,
                    color = CircleText
                )
            } else if (event.reaction.isNotBlank()) {
                Text(
                    text = "${event.actor_fp_short.ifBlank { displayName }} reacted ${event.reaction}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = CircleText
                )
            } else {
                Text(
                    text = "No text preview in this federated event.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CircleMuted
                )
            }

            if (circleStackPayloadBoolean(event, "has_media")) {
                val previewUrl = circleStackFederatedPreviewUrl(event)

                if (previewUrl.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .background(CirclePanelSoft)
                            .clickable { onOpenImage(previewUrl) }
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(previewUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Federated Circle Stack media",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        Text(
                            text = "Tap to zoom",
                            style = MaterialTheme.typography.labelSmall,
                            color = CircleText,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .background(CircleBg.copy(alpha = 0.72f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    Text(
                        text = "Media exists on the origin server, but no preview URL was available.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CircleMuted
                    )
                }
            }

            HorizontalDivider(color = CircleLine.copy(alpha = 0.45f))

            Text(
                text = "Origin: ${event.origin_nas.take(12).ifBlank { "unknown" }}",
                style = MaterialTheme.typography.bodySmall,
                color = CircleMuted
            )
        }
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
                    text = post.visibility.uppercase(LocalConfiguration.current.locales[0]),
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
                        .clickable { onOpenImage(mediaUrl) }
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

                    Text(
                        text = "Tap to zoom",
                        style = MaterialTheme.typography.labelSmall,
                        color = CircleText,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .background(CircleBg.copy(alpha = 0.72f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
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

private fun circleStackMapValue(
    map: Map<*, *>,
    key: String
): Any? {
    return map[key]
}

@Suppress("UNCHECKED_CAST")
private fun circleStackPayloadMap(
    event: CircleStackFederatedEventDto,
    key: String
): Map<*, *> {
    return event.payload[key] as? Map<*, *> ?: emptyMap<Any, Any?>()
}

@Suppress("UNCHECKED_CAST")
private fun circleStackPayloadList(
    event: CircleStackFederatedEventDto,
    key: String
): List<Any?> {
    return event.payload[key] as? List<Any?> ?: emptyList()
}

private fun circleStackStringValue(value: Any?): String {
    return value as? String ?: ""
}

private fun circleStackFirstMediaRefId(event: CircleStackFederatedEventDto): String {
    val directRefs = circleStackPayloadList(event, "media_refs")
    for (ref in directRefs) {
        val refMap = ref as? Map<*, *> ?: continue
        val refId = circleStackStringValue(refMap["ref_id"])
        if (refId.isNotBlank()) return refId
    }

    val mediaPreview = circleStackPayloadMap(event, "media_preview")
    val previewRefs = mediaPreview["refs"] as? List<*> ?: emptyList<Any?>()
    for (ref in previewRefs) {
        val refMap = ref as? Map<*, *> ?: continue
        val refId = circleStackStringValue(refMap["ref_id"])
        if (refId.isNotBlank()) return refId
    }

    return ""
}

private fun circleStackFederatedPreviewUrl(event: CircleStackFederatedEventDto): String {
    val origin = circleStackPayloadMap(event, "origin")

    val baseUrl = circleStackStringValue(origin["preview_base_url"])
        .trim()
        .trimEnd('/')

    val endpoint = circleStackStringValue(origin["preview_endpoint"])
        .trim()
        .ifBlank { "/api/v4/circlestack/federation/media-preview" }
        .let { if (it.startsWith("/")) it else "/$it" }

    val refId = circleStackFirstMediaRefId(event)

    if (baseUrl.isBlank() || refId.isBlank()) {
        return ""
    }

    val eventId = event.event_id.trim()
    if (eventId.isBlank()) {
        return ""
    }

    val encodedEventId = URLEncoder.encode(eventId, "UTF-8")
    val encodedRef = URLEncoder.encode(refId, "UTF-8")
    return "$baseUrl$endpoint?event_id=$encodedEventId&ref_id=$encodedRef"
}

private fun circleStackPayloadString(
    event: CircleStackFederatedEventDto,
    key: String
): String {
    return (event.payload[key] as? String).orEmpty()
}

private fun circleStackPayloadBoolean(
    event: CircleStackFederatedEventDto,
    key: String
): Boolean {
    return when (val v = event.payload[key]) {
        is Boolean -> v
        is Number -> v.toInt() != 0
        is String -> v.equals("true", ignoreCase = true) || v == "1"
        else -> false
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


private fun circleStackDisplayName(context: Context, uri: Uri): String {
    return runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                cursor.getString(idx).orEmpty()
            } else {
                ""
            }
        }.orEmpty()
    }.getOrDefault("")
}

private fun circleStackFallbackImageName(mimeType: String?): String {
    val ext = when (mimeType?.lowercase(Locale.getDefault())) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "image/heic" -> "heic"
        "image/heif" -> "heif"
        else -> "jpg"
    }

    return "circle_image.$ext"
}

private fun circleStackSafeUploadName(name: String, mimeType: String?): String {
    val fallback = circleStackFallbackImageName(mimeType)
    val raw = name.ifBlank { fallback }

    val cleaned = raw
        .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_")
        .trim()
        .trim('.')
        .ifBlank { fallback }
        .take(140)

    return if (cleaned.contains(".")) {
        cleaned
    } else {
        val ext = fallback.substringAfterLast('.', "jpg")
        "$cleaned.$ext"
    }
}

private suspend fun stageCircleStackImageUri(
    context: Context,
    uri: Uri,
    fallbackName: String
): File = withContext(Dispatchers.IO) {
    val dir = File(context.cacheDir, "circlestack_uploads").also {
        it.mkdirs()
    }

    val safeName = circleStackSafeUploadName(fallbackName, context.contentResolver.getType(uri))
    val out = File(dir, "${System.currentTimeMillis()}_$safeName")

    context.contentResolver.openInputStream(uri)?.use { input ->
        out.outputStream().use { output ->
            input.copyTo(output)
        }
    } ?: throw IllegalStateException("Could not open selected image.")

    if (!out.isFile || out.length() <= 0L) {
        throw IllegalStateException("Selected image was empty.")
    }

    out
}


private fun circleStackIsImageFile(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase(Locale.getDefault())
    return ext in setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "heif")
}

private fun circleStackJoinPath(parent: String?, name: String): String {
    val cleanParent = parent.orEmpty().trim('/')
    val cleanName = name.trim('/')
    return if (cleanParent.isBlank()) cleanName else "$cleanParent/$cleanName"
}

private fun circleStackParentPath(path: String?): String? {
    val clean = path.orEmpty().trim('/')
    if (clean.isBlank()) return null
    val parts = clean.split('/').filter { it.isNotBlank() }
    if (parts.size <= 1) return null
    return parts.dropLast(1).joinToString("/")
}


private fun circleStackShouldHidePickerItem(name: String): Boolean {
    val clean = name.trim()
    if (clean.isBlank()) return true

    // Hide service-private implementation folders from normal media picking:
    // .pqnas_activity, .pqnas_circlestack, .trash-like internals, editor temp dirs, etc.
    if (clean.startsWith(".")) return true

    return false
}

private fun circleStackFileGetUrl(baseUrl: String, path: String): String {
    val base = baseUrl.trim().trimEnd('/')
    val cleanPath = path.trim('/')
    val encoded = URLEncoder.encode(cleanPath, "UTF-8")
    return "$base/api/v4/files/get?path=$encoded"
}
