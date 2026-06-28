package com.pqnas.mobile.ui.screens

// PQNAS_ANDROID_WORKSPACE_MESSAGES_LINKS_V1: Android workspace messages + URL shortcut UI.

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pqnas.mobile.api.WorkspaceMessageDto
import com.pqnas.mobile.files.FileScope
import com.pqnas.mobile.files.FilesRepository
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.HttpException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkspaceMessagesSheet(
    filesRepository: FilesRepository,
    workspace: FileScope.Workspace,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var reloadNonce by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<WorkspaceMessageDto>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    fun reload() {
        reloadNonce += 1
    }

    LaunchedEffect(workspace.workspaceId, reloadNonce) {
        loading = true
        status = "Loading messages..."
        runCatching {
            filesRepository.listWorkspaceMessages(
                workspaceId = workspace.workspaceId,
                limit = 100
            )
        }.onSuccess { response ->
            if (response.ok) {
                messages = response.messages
                    .distinctBy { it.id }
                    .sortedBy { it.id }
                status = "OK: ${messages.size} messages loaded for ${workspace.workspaceId}"
            } else {
                status = response.message ?: response.error ?: "Could not load messages."
            }
        }.onFailure { e ->
            status = workspaceMessageFailureText("Load messages", e)
        }
        loading = false
    }

    ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Workspace messages",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = workspace.workspaceName.ifBlank { workspace.workspaceId },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (status.isNotBlank()) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (
                        status == "OK" ||
                        status.startsWith("OK:") ||
                        status.startsWith("No ") ||
                        status.startsWith("Loading") ||
                        status.startsWith("Sending") ||
                        status.startsWith("Message sent") ||
                        status.startsWith("Message deleted")
                    ) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (messages.isEmpty()) {
                        item {
                            Text(
                                text = "Android received zero messages. Status: $status",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    items(messages, key = { it.id }) { message ->
                        WorkspaceMessageRow(
                            message = message,
                            onDelete = {
                                scope.launch {
                                    status = "Deleting message..."
                                    runCatching {
                                        filesRepository.deleteWorkspaceMessage(
                                            workspaceId = workspace.workspaceId,
                                            messageId = message.id
                                        )
                                    }.onSuccess { response ->
                                        status = if (response.ok) "Message deleted." else response.message ?: response.error ?: "Delete failed."
                                        reload()
                                    }.onFailure { e ->
                                        status = workspaceMessageFailureText("Delete message", e)
                                    }
                                }
                            }
                        )
                    }
                }
            }

            if (workspace.canWrite) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(4000) },
                    label = { Text("New message") },
                    placeholder = { Text("Write a note visible to workspace members...") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { reload() }) {
                        Text("Refresh")
                    }
                    Button(
                        enabled = !sending && draft.trim().isNotBlank(),
                        onClick = {
                            val body = draft.trim()
                            scope.launch {
                                sending = true
                                status = "Sending..."
                                runCatching {
                                    filesRepository.postWorkspaceMessage(
                                        workspaceId = workspace.workspaceId,
                                        body = body
                                    )
                                }.onSuccess { response ->
                                    if (response.ok) {
                                        draft = ""
                                        response.message?.let { savedMessage ->
                                            messages = (messages + savedMessage)
                                                .distinctBy { it.id }
                                                .sortedBy { it.id }
                                        }
                                        status = "Message sent. Local list now has ${messages.size} messages."
                                        reload()
                                    } else {
                                        status = response.error ?: "Send failed."
                                    }
                                }.onFailure { e ->
                                    status = workspaceMessageFailureText("Send message", e)
                                }
                                sending = false
                            }
                        }
                    ) {
                        Text(if (sending) "Sending..." else "Send")
                    }
                }
            } else {
                Text(
                    text = "You can read messages here, but this workspace role cannot post new ones.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { reload() }) {
                        Text("Refresh")
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun WorkspaceMessageRow(
    message: WorkspaceMessageDto,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (message.is_own) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = message.author_name.ifBlank { if (message.is_own) "Me" else "Member" },
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = formatWorkspaceMessageTime(message.created_at_epoch, message.created_at),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = message.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (message.attachments.isNotEmpty()) {
                HorizontalDivider()
                message.attachments.forEach { attachment ->
                    Text(
                        text = "Attachment: " + attachment.label.ifBlank { attachment.name.ifBlank { attachment.path } },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (message.can_delete) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDelete) {
                        Text("Delete")
                    }
                }
            }
        }
    }
}

@Composable
internal fun WorkspaceUrlLinkDialog(
    currentPath: String?,
    onDismiss: () -> Unit,
    onSave: (title: String, url: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save URL link") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Create a small .url shortcut file in this workspace" +
                            (currentPath?.let { " at /$it" } ?: " root") + ".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(120) },
                    label = { Text("Title") },
                    placeholder = { Text("Example: Supplier portal") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it.trim().take(2048) },
                    label = { Text("URL") },
                    placeholder = { Text("https://example.com") },
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error.isNotBlank()) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cleanUrl = url.trim()
                    if (!isSafeHttpUrlForWorkspaceShortcut(cleanUrl)) {
                        error = "Only http:// and https:// links are accepted."
                        return@TextButton
                    }
                    onSave(title.trim(), cleanUrl)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

internal fun workspaceUrlShortcutContent(title: String, url: String): String {
    val cleanTitle = title.trim()
    return buildString {
        appendLine("[InternetShortcut]")
        appendLine("URL=$url")
        if (cleanTitle.isNotBlank()) appendLine("Comment=$cleanTitle")
    }
}

internal fun workspaceUrlLinkFileName(title: String, url: String): String {
    val source = title.ifBlank {
        url.removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
            .ifBlank { "Saved link" }
    }

    val cleaned = source
        .replace(Regex("[\\/:*?\"<>|\\u0000-\\u001F]"), "_")
        .trim()
        .trim('.')
        .ifBlank { "Saved link" }
        .take(100)

    return if (cleaned.endsWith(".url", ignoreCase = true)) cleaned else "$cleaned.url"
}

internal fun isSafeHttpUrlForWorkspaceShortcut(url: String): Boolean {
    val low = url.lowercase(Locale.US)
    return low.startsWith("https://") || low.startsWith("http://")
}


// PQNAS_ANDROID_WORKSPACE_MESSAGE_ERROR_VISIBILITY_V1
private fun workspaceMessageFailureText(prefix: String, e: Throwable): String {
    if (e is HttpException) {
        val code = e.code()
        val rawBody = runCatching {
            e.response()?.errorBody()?.string().orEmpty()
        }.getOrDefault("")

        val detail = workspaceMessageErrorDetail(rawBody)
            .ifBlank { e.message.orEmpty() }

        return if (detail.isNotBlank()) {
            "$prefix failed (HTTP $code): $detail"
        } else {
            "$prefix failed (HTTP $code)"
        }
    }

    val msg = e.message
        ?: e::class.java.simpleName.takeIf { it.isNotBlank() }
        ?: "unknown error"

    return "$prefix failed: $msg"
}

private fun workspaceMessageErrorDetail(rawBody: String): String {
    val raw = rawBody.trim()
    if (raw.isBlank()) return ""

    return runCatching {
        val json = JSONObject(raw)
        listOf("message", "detail", "error")
            .firstNotNullOfOrNull { key ->
                json.optString(key)
                    .trim()
                    .takeIf { it.isNotBlank() }
            }
            ?: raw.take(500)
    }.getOrElse {
        raw.take(500)
    }
}

private fun formatWorkspaceMessageTime(epoch: Long, fallback: String): String {
    if (epoch <= 0L) return fallback
    return try {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epoch * 1000L))
    } catch (_: Exception) {
        fallback
    }
}

// PQNAS_ANDROID_WORKSPACE_MESSAGES_REFRESH_DEBUG_V3
