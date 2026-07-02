package com.pqnas.mobile.ui.screens

// PQNAS_ANDROID_WORKSPACE_MESSAGES_LINKS_V1: Android workspace messages + URL shortcut UI.

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
    val focusManager = LocalFocusManager.current
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

    fun sendDraft() {
        val body = draft.trim()
        if (sending || body.isBlank()) return

        // Hide the software keyboard before sending. This avoids the Android back
        // button dismissing the whole bottom sheet when the keyboard is open.
        focusManager.clearFocus()

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

    ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
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
                        status.startsWith("Contact card") ||
                        status.endsWith("copied.") ||
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
                            onStatus = { status = it },
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
                    onValueChange = { value ->
                        val nextDraft = value.take(4000)
                        draft = nextDraft
                        if (parseWorkspaceContactCardText(nextDraft) != null) {
                            status = "Contact card detected. Send to share it with workspace members."
                        }
                    },
                    label = { Text("New message") },
                    placeholder = { Text("Write a note visible to workspace members...") },
                    minLines = 3,
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { sendDraft() }),
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
                        onClick = { sendDraft() }
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
    onStatus: (String) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val parsedContactCard = remember(message.body) {
        parseWorkspaceContactCardText(message.body)
    }

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

            if (parsedContactCard != null) {
                WorkspaceContactMessageBody(
                    parsed = parsedContactCard,
                    onCopy = { label, value, success ->
                        copyWorkspaceContactValue(
                            context = context,
                            label = label,
                            value = value,
                            success = success,
                            onStatus = onStatus
                        )
                    }
                )
            } else {
                Text(
                    text = message.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

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


private data class ParsedWorkspaceContactCard(
    val before: String,
    val after: String,
    val card: WorkspaceContactCardData
)

private data class WorkspaceContactCardData(
    val name: String = "",
    val company: String = "",
    val title: String = "",
    val email: String = "",
    val phone: String = "",
    val mobile: String = "",
    val website: String = "",
    val address: String = "",
    val tags: String = "",
    val identity: String = ""
)

// PQNAS_ANDROID_CONTACT_CARD_RENDER_V1:
// Parse only the explicit DNA-Nexus contact-card envelope. The message body is
// still rendered as plain Compose Text, not HTML, so workspace messages cannot
// inject markup or execute scripts through contact fields.
private fun parseWorkspaceContactCardText(text: String): ParsedWorkspaceContactCard? {
    val raw = text
    val start = raw.indexOf("[DNA-NEXUS-CONTACT]")
    val end = raw.indexOf("[/DNA-NEXUS-CONTACT]")
    if (start < 0 || end < 0 || end <= start) return null

    val before = raw.substring(0, start).trim()
    val body = raw
        .substring(start + "[DNA-NEXUS-CONTACT]".length, end)
        .trim()
    val after = raw
        .substring(end + "[/DNA-NEXUS-CONTACT]".length)
        .trim()

    var card = WorkspaceContactCardData()

    body.lineSequence().forEach { line ->
        val idx = line.indexOf(":")
        if (idx <= 0) return@forEach

        val key = line.substring(0, idx).trim().lowercase(Locale.US)
        val value = line.substring(idx + 1).trim()
        if (key.isBlank() || value.isBlank()) return@forEach

        card = when (key) {
            "name" -> card.copy(name = value)
            "company" -> card.copy(company = value)
            "title" -> card.copy(title = value)
            "email" -> card.copy(email = value)
            "phone" -> card.copy(phone = value)
            "mobile" -> card.copy(mobile = value)
            "website" -> card.copy(website = value)
            "address" -> card.copy(address = value)
            "tags" -> card.copy(tags = value)
            "identity" -> card.copy(identity = value)
            else -> card
        }
    }

    if (
        card.name.isBlank() &&
        card.company.isBlank() &&
        card.email.isBlank() &&
        card.phone.isBlank() &&
        card.mobile.isBlank()
    ) {
        return null
    }

    return ParsedWorkspaceContactCard(
        before = before,
        after = after,
        card = card
    )
}

@Composable
private fun WorkspaceContactMessageBody(
    parsed: ParsedWorkspaceContactCard,
    onCopy: (label: String, value: String, success: String) -> Unit
) {
    val card = parsed.card

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (parsed.before.isNotBlank()) {
            Text(
                text = parsed.before,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = card.name.ifBlank {
                                card.company.ifBlank {
                                    card.email.ifBlank { "Contact card" }
                                }
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        val meta = listOf(card.company, card.title)
                            .filter { it.isNotBlank() }
                            .joinToString(" • ")

                        if (meta.isNotBlank()) {
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = "Contact",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                WorkspaceContactLine("Email", card.email)
                WorkspaceContactLine("Phone", card.phone)
                WorkspaceContactLine("Mobile", card.mobile)
                WorkspaceContactLine("Website", card.website)
                WorkspaceContactLine("Address", card.address)
                WorkspaceContactLine("Tags", card.tags)

                if (card.identity.isNotBlank()) {
                    Text(
                        text = "Identity: ${card.identity}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            onCopy(
                                "Contact card",
                                formatWorkspaceContactCardForClipboard(card),
                                "Contact copied."
                            )
                        }
                    ) {
                        Text("Copy contact")
                    }

                    if (card.email.isNotBlank()) {
                        TextButton(
                            onClick = {
                                onCopy("Email", card.email, "Email copied.")
                            }
                        ) {
                            Text("Email")
                        }
                    }

                    if (card.phone.isNotBlank() || card.mobile.isNotBlank()) {
                        TextButton(
                            onClick = {
                                onCopy(
                                    "Phone",
                                    card.phone.ifBlank { card.mobile },
                                    "Phone copied."
                                )
                            }
                        ) {
                            Text("Phone")
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (card.address.isNotBlank()) {
                        TextButton(
                            onClick = {
                                onCopy("Address", card.address, "Address copied.")
                            }
                        ) {
                            Text("Address")
                        }
                    }

                    if (card.website.isNotBlank()) {
                        TextButton(
                            onClick = {
                                onCopy("Website", card.website, "Website copied.")
                            }
                        ) {
                            Text("Website")
                        }
                    }
                }
            }
        }

        if (parsed.after.isNotBlank()) {
            Text(
                text = parsed.after,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun WorkspaceContactLine(label: String, value: String) {
    if (value.isBlank()) return

    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun formatWorkspaceContactCardForClipboard(card: WorkspaceContactCardData): String =
    listOf(
        "[DNA-NEXUS-CONTACT]",
        "Name: ${card.name}",
        "Company: ${card.company}",
        "Title: ${card.title}",
        "Email: ${card.email}",
        "Phone: ${card.phone}",
        "Mobile: ${card.mobile}",
        "Website: ${card.website}",
        "Address: ${card.address}",
        "Tags: ${card.tags}",
        "Identity: ${card.identity}",
        "[/DNA-NEXUS-CONTACT]"
    ).filterNot { it.endsWith(": ") }.joinToString("\n")

private fun copyWorkspaceContactValue(
    context: Context,
    label: String,
    value: String,
    success: String,
    onStatus: (String) -> Unit
) {
    val clean = value.trim()
    if (clean.isBlank()) {
        onStatus("Nothing to copy.")
        return
    }

    runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, clean))
    }.onSuccess {
        onStatus(success)
    }.onFailure {
        onStatus("Copy failed.")
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
