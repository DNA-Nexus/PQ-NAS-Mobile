package com.pqnas.mobile

// PQNAS_INCOMING_ANDROID_SHARE_V1

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object IncomingShareStager {
    data class Result(
        val manifestPath: String,
        val itemCount: Int
    )

    fun stage(context: Context, intent: Intent?): Result {
        require(intent != null) { "missing share intent" }

        val batchId = "share_" +
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) +
                "_" +
                UUID.randomUUID().toString().take(8)

        val dir = File(context.filesDir, "incoming_shares/$batchId")
        if (!dir.mkdirs() && !dir.isDirectory) {
            error("could not create incoming share directory")
        }

        val items = JSONArray()
        var count = 0

        for ((index, uri) in extractUris(intent).withIndex()) {
            val staged = copyUriToPrivateFile(
                context = context,
                uri = uri,
                dir = dir,
                index = index,
                fallbackMime = intent.type
            )
            items.put(staged)
            count += 1
        }

        val sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        if (!sharedText.isNullOrBlank()) {
            // PQNAS_ANDROID_INCOMING_URL_SHORTCUTS_V1:
            // Browser/app Sharesheet URL shares should become portable .url shortcut files,
            // not generic shared_text.txt files. Destination selection still happens later
            // in FilesScreen, so this works for user storage and writable workspaces.
            val cleanText = sharedText.trim()
            val isUrlShortcut = isSafeHttpUrlForIncomingShortcut(cleanText)
            val preferredName = if (isUrlShortcut) {
                incomingUrlShortcutFileName(cleanText)
            } else {
                "shared_text.txt"
            }
            val textFile = uniqueFile(dir, preferredName)
            val storedText = if (isUrlShortcut) {
                "[InternetShortcut]\nURL=$cleanText\n"
            } else {
                sharedText
            }

            textFile.writeText(storedText, Charsets.UTF_8)

            items.put(
                JSONObject()
                    .put("kind", "file")
                    .put("name", textFile.name)
                    .put("original_name", preferredName)
                    .put("path", textFile.absolutePath)
                    .put("source_uri", "")
                    .put("mime", if (isUrlShortcut) "application/x-mswinurl" else "text/plain")
                    .put("bytes", textFile.length())
            )
            count += 1
        }

        require(count > 0) { "share contained no readable file or text" }

        val manifest = JSONObject()
            .put("format", "pqnas_incoming_share_v1")
            .put("batch_id", batchId)
            .put("created_at_unix_ms", System.currentTimeMillis())
            .put("source_action", intent.action ?: "")
            .put("source_type", intent.type ?: "")
            .put("items", items)

        val manifestFile = File(dir, "manifest.json")
        manifestFile.writeText(manifest.toString(2), Charsets.UTF_8)

        return Result(
            manifestPath = manifestFile.absolutePath,
            itemCount = count
        )
    }

    @Suppress("DEPRECATION")
    private fun extractUris(intent: Intent): List<Uri> {
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val one = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (one == null) emptyList() else listOf(one)
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                    ?.toList()
                    .orEmpty()
            }

            else -> emptyList()
        }
    }

    private fun copyUriToPrivateFile(
        context: Context,
        uri: Uri,
        dir: File,
        index: Int,
        fallbackMime: String?
    ): JSONObject {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: fallbackMime ?: "application/octet-stream"
        val originalName = queryDisplayName(context, uri)
        val preferredName = sanitizeFileName(originalName ?: defaultName(index, mime))
        val outputFile = uniqueFile(dir, preferredName)

        resolver.openInputStream(uri).use { input ->
            require(input != null) { "could not open shared stream" }

            outputFile.outputStream().use { output ->
                input.copyTo(output)
                output.flush()
            }
        }

        return JSONObject()
            .put("kind", "file")
            .put("name", outputFile.name)
            .put("original_name", originalName ?: "")
            .put("path", outputFile.absolutePath)
            .put("source_uri", uri.toString())
            .put("mime", mime)
            .put("bytes", outputFile.length())
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                .use { cursor ->
                    if (cursor != null && cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else {
                        null
                    }
                }
        } catch (_: Exception) {
            null
        }
    }

    private fun sanitizeFileName(name: String): String {
        val cleaned = name
            .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_")
            .trim()
            .trim('.')

        return cleaned.ifBlank { "shared_file.bin" }.take(180)
    }

    private fun defaultName(index: Int, mime: String): String {
        val ext = when {
            mime.equals("image/jpeg", ignoreCase = true) -> ".jpg"
            mime.equals("image/png", ignoreCase = true) -> ".png"
            mime.equals("image/gif", ignoreCase = true) -> ".gif"
            mime.equals("image/webp", ignoreCase = true) -> ".webp"
            mime.equals("video/mp4", ignoreCase = true) -> ".mp4"
            mime.equals("application/pdf", ignoreCase = true) -> ".pdf"
            mime.equals("text/plain", ignoreCase = true) -> ".txt"
            else -> ".bin"
        }

        return "shared_${index + 1}$ext"
    }

    private fun uniqueFile(dir: File, preferredName: String): File {
        var candidate = File(dir, preferredName)
        if (!candidate.exists()) return candidate

        val dot = preferredName.lastIndexOf('.')
        val base = if (dot > 0) preferredName.substring(0, dot) else preferredName
        val ext = if (dot > 0) preferredName.substring(dot) else ""

        for (i in 2..9999) {
            candidate = File(dir, "${base}_${i}${ext}")
            if (!candidate.exists()) return candidate
        }

        error("could not allocate unique file name for $preferredName")
    }
}

// PQNAS_ANDROID_INCOMING_URL_HELPERS_FIX_V2
private fun isSafeHttpUrlForIncomingShortcut(value: String): Boolean {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return false

    val uri = Uri.parse(trimmed)
    val scheme = uri.scheme?.lowercase() ?: return false
    if (scheme != "http" && scheme != "https") return false

    val host = uri.host ?: return false
    return host.isNotBlank()
}

private fun incomingUrlShortcutFileName(value: String): String {
    val uri = Uri.parse(value.trim())

    val host = uri.host
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: "shared_link"

    val pathTail = uri.lastPathSegment
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: "link"

    val rawBase = "$host-$pathTail"

    val safeBase = rawBase
        .replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "_")
        .trim()
        .trim('.')
        .take(80)
        .ifBlank { "shared_link" }

    return "$safeBase.url"
}

