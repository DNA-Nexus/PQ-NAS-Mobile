package com.pqnas.mobile.ui.screens

import com.pqnas.mobile.R

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pqnas.mobile.api.FileVersionItemDto
import com.pqnas.mobile.files.FileScope
import com.pqnas.mobile.files.FilesRepository
import com.pqnas.mobile.files.ScopedFilesOps
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileVersionsSheet(
    filesRepository: FilesRepository,
    fileScope: FileScope,
    relPath: String,
    displayName: String,
    onDismiss: () -> Unit,
    onRestored: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scopedOps = remember(filesRepository, context) {
        ScopedFilesOps(filesRepository, context.applicationContext)
    }

    var versions by remember(relPath, fileScope) { mutableStateOf<List<FileVersionItemDto>>(emptyList()) }
    var status by remember(relPath, fileScope) { mutableStateOf(context.getString(R.string.versions_loading)) }
    var closeAfterRestore by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<FileVersionItemDto?>(null) }
    var restoringVersionId by remember { mutableStateOf<String?>(null) }
    var flaggingVersionId by remember { mutableStateOf<String?>(null) }
    var comparingVersion by remember { mutableStateOf<FileVersionItemDto?>(null) }

    val canRestore = scopedOps.canWrite(fileScope)
    val scopeLabel = when (fileScope) {
        FileScope.User -> stringResource(R.string.versions_scope_personal)
        is FileScope.Workspace -> stringResource(
            R.string.versions_scope_workspace,
            fileScope.workspaceName.ifBlank { fileScope.workspaceId }
        )
    }

    fun loadVersions() {
        scope.launch {
            status = context.getString(R.string.versions_loading)
            try {
                val resp = scopedOps.listVersions(fileScope, relPath)
                versions = resp.entries.sortedByDescending { it.created_epoch ?: 0L }
                status = if (versions.isEmpty()) context.getString(R.string.versions_empty_preserved) else ""
            } catch (e: Exception) {
                status = friendlyVersionsMessage(context, "Load versions", e)
            }
        }
    }

    LaunchedEffect(relPath, fileScope) {
        loadVersions()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.versions_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = displayName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = scopeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = relPath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { closeAfterRestore = !closeAfterRestore },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = closeAfterRestore,
                    onCheckedChange = { closeAfterRestore = it }
                )
                Text(
                    text = stringResource(R.string.versions_close_after_restore),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (status.isNotBlank()) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (status.contains("failed", ignoreCase = true) ||
                        status.contains("denied", ignoreCase = true) ||
                        status.contains("not found", ignoreCase = true)
                    ) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            if (versions.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = versions,
                        key = { item ->
                            item.version_id.ifBlank {
                                "${item.created_epoch ?: 0L}:${item.sha256_hex.orEmpty()}"
                            }
                        }
                    ) { item ->
                        VersionRow(
                            item = item,
                            canRestore = canRestore,
                            isRestoring = restoringVersionId == item.version_id,
                            isFlagging = flaggingVersionId == item.version_id,
                            canCompare = isTextLikeVersionPath(relPath),
                            onCompare = {
                                comparingVersion = item
                            },
                            onToggleFlag = {
                                val versionId = item.version_id
                                if (versionId.isBlank()) return@VersionRow

                                scope.launch {
                                    flaggingVersionId = versionId
                                    val wasFlagged = item.flagged_by_me
                                    try {
                                        scopedOps.setVersionFlag(
                                            scope = fileScope,
                                            path = relPath,
                                            versionId = versionId,
                                            shouldFlag = !wasFlagged
                                        )
                                        status = if (wasFlagged) context.getString(R.string.versions_flag_removed) else context.getString(R.string.versions_flagged)
                                        loadVersions()
                                    } catch (e: Exception) {
                                        status = friendlyVersionsMessage(
                                            context,
                                            if (wasFlagged) "Remove flag" else "Flag version",
                                            e
                                        )
                                    } finally {
                                        flaggingVersionId = null
                                    }
                                }
                            },
                            onCopySha = { sha ->
                                if (copyToClipboard(context, "sha256", sha)) {
                                    status = context.getString(R.string.versions_sha_copied)
                                } else {
                                    status = context.getString(R.string.versions_copy_failed)
                                }
                            },
                            onRestore = {
                                pendingRestore = item
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
        }
    }

    pendingRestore?.let { item ->
        AlertDialog(
            onDismissRequest = {
                if (restoringVersionId == null) pendingRestore = null
            },
            title = { Text(stringResource(R.string.versions_restore_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.versions_restore_confirm))
                    Text(
                        text = versionKindLabel(context, item),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    item.created_epoch?.let {
                        Text(
                            text = stringResource(R.string.versions_created_value, formatVersionTime(item)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = restoringVersionId == null && canRestore,
                    onClick = {
                        scope.launch {
                            restoringVersionId = item.version_id
                            try {
                                scopedOps.restoreVersion(
                                    scope = fileScope,
                                    path = relPath,
                                    versionId = item.version_id
                                )

                                val msg = context.getString(R.string.versions_restored_success)
                                status = msg
                                onRestored(msg)

                                pendingRestore = null
                                restoringVersionId = null

                                if (closeAfterRestore) {
                                    onDismiss()
                                } else {
                                    loadVersions()
                                }
                            } catch (e: Exception) {
                                restoringVersionId = null
                                status = friendlyVersionsMessage(context, "Restore version", e)
                            }
                        }
                    }
                ) {
                    Text(if (restoringVersionId == item.version_id) stringResource(R.string.versions_restoring) else stringResource(R.string.versions_restore))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = restoringVersionId == null,
                    onClick = { pendingRestore = null }
                ) {
                    Text(stringResource(R.string.versions_cancel))
                }
            }
        )
    }
    comparingVersion?.let { item ->
        FileVersionCompareDialog(
            filesRepository = filesRepository,
            fileScope = fileScope,
            relPath = relPath,
            displayName = displayName,
            version = item,
            onDismiss = { comparingVersion = null }
        )
    }
}

@Composable
private fun VersionRow(
    item: FileVersionItemDto,
    canRestore: Boolean,
    isRestoring: Boolean,
    isFlagging: Boolean,
    canCompare: Boolean,
    onCompare: () -> Unit,
    onToggleFlag: () -> Unit,
    onCopySha: (String) -> Unit,
    onRestore: () -> Unit
) {
    val context = LocalContext.current

    val actor = item.actor_display
        ?.takeIf { it.isNotBlank() }
        ?: item.actor_name_snapshot?.takeIf { it.isNotBlank() }
        ?: item.actor_fp?.takeIf { it.isNotBlank() }
        ?: "-"

    val sha = item.sha256_hex.orEmpty()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = versionKindLabel(context, item),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = stringResource(R.string.versions_date_value, formatVersionTime(item)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = stringResource(R.string.versions_actor_value, actor),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = stringResource(R.string.versions_size_value, formatVersionBytes(item.bytes)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val flagText = versionFlagSummary(context, item)
            if (flagText.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ) {
                    Text(
                        text = flagText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (sha.isNotBlank()) {
                Text(
                    text = "SHA-256: ${shortSha(sha)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!item.version_id.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.versions_id_value, item.version_id),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        enabled = canCompare && item.version_id.isNotBlank(),
                        onClick = onCompare
                    ) {
                        Text(stringResource(R.string.versions_compare))
                    }

                    TextButton(
                        enabled = !isFlagging && item.version_id.isNotBlank(),
                        onClick = onToggleFlag
                    ) {
                        Text(
                            when {
                                isFlagging -> stringResource(R.string.versions_working)
                                item.flagged_by_me -> stringResource(R.string.versions_unflag)
                                else -> stringResource(R.string.versions_flag)
                            }
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (sha.isNotBlank()) {
                        TextButton(
                            onClick = { onCopySha(sha) }
                        ) {
                            Text(stringResource(R.string.versions_copy_sha))
                        }
                    }

                    TextButton(
                        enabled = canRestore && !isRestoring,
                        onClick = onRestore
                    ) {
                        Text(
                            when {
                                isRestoring -> stringResource(R.string.versions_restoring)
                                !canRestore -> stringResource(R.string.versions_read_only)
                                else -> stringResource(R.string.versions_restore)
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun isTextLikeVersionPath(path: String): Boolean {
    val ext = path.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.getDefault())
    return ext in setOf(
        "txt", "md", "log", "json", "html", "htm", "css", "js", "ts",
        "c", "cc", "cpp", "h", "hpp", "py", "sh", "yml", "yaml",
        "ini", "conf", "csv", "xml", "sql", "toml"
    )
}

private fun versionFlagSummary(context: Context, item: FileVersionItemDto): String {
    val flags = item.flags
    val count = item.flag_count.takeIf { it > 0L } ?: flags.size.toLong()
    if (count <= 0L) return ""

    val names = flags
        .mapNotNull { flag ->
            flag.actor_display
                ?.takeIf { it.isNotBlank() }
                ?: flag.actor_name_snapshot?.takeIf { it.isNotBlank() }
                ?: flag.actor_fp?.takeIf { it.isNotBlank() }
        }

    return when {
        names.size == 1 -> context.getString(R.string.versions_flagged_by_one, names[0])
        names.size == 2 -> context.getString(R.string.versions_flagged_by_two, names[0], names[1])
        names.size > 2 -> context.getString(
            R.string.versions_flagged_by_many,
            names[0],
            names[1],
            names.size - 2
        )
        else -> context.getString(R.string.versions_flagged_by_count, count)
    }
}

private fun versionKindLabel(context: Context, item: FileVersionItemDto): String {
    if (item.is_deleted_event == true) {
        return context.getString(R.string.versions_kind_deleted_snapshot)
    }

    val raw = item.event_kind.orEmpty().lowercase(Locale.getDefault())
    return when {
        raw.contains("overwrite_preserve") -> context.getString(R.string.versions_kind_before_overwrite)
        raw.contains("delete_preserve") -> context.getString(R.string.versions_kind_deleted_snapshot)
        raw.isBlank() -> context.getString(R.string.versions_kind_preserved)
        else -> raw
            .replace('_', ' ')
            .replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
            }
    }
}

private fun formatVersionTime(item: FileVersionItemDto): String {
    item.created_at?.takeIf { it.isNotBlank() }?.let { return it }
    val epoch = item.created_epoch ?: return "-"
    val date = Date(epoch * 1000L)
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return fmt.format(date)
}

private fun formatVersionBytes(bytes: Long?): String {
    val v = bytes ?: return "-"
    if (v < 1024) return "$v B"
    val units = arrayOf("KB", "MB", "GB", "TB", "PB")
    val digitGroups = (ln(v.toDouble()) / ln(1024.0)).toInt()
    val value = v / 1024.0.pow(digitGroups.toDouble())
    return String.format(Locale.US, "%.1f %s", value, units[digitGroups - 1])
}

private fun shortSha(sha: String): String {
    if (sha.length <= 20) return sha
    return sha.take(12) + "…" + sha.takeLast(8)
}

private fun copyToClipboard(context: Context, label: String, text: String): Boolean {
    return try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        true
    } catch (_: Exception) {
        false
    }
}

private fun friendlyVersionsMessage(
    context: Context,
    action: String,
    error: Throwable
): String {
    val http = (error as? HttpException)?.code()
        ?: Regex("""\bHTTP\s+(\d{3})\b""")
            .find(error.message.orEmpty())
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()

    return when (http) {
        400 -> context.getString(R.string.versions_error_invalid_request, action)
        401 -> context.getString(R.string.versions_error_session_expired)
        403 -> context.getString(R.string.versions_error_access_denied)
        404 -> context.getString(R.string.versions_error_not_found)
        409 -> context.getString(R.string.versions_error_conflict, action)
        500 -> context.getString(R.string.versions_error_server, action)
        else -> {
            val msg = error.message?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.versions_unknown_error)
            context.getString(R.string.versions_error_unknown, action, msg)
        }
    }
}
