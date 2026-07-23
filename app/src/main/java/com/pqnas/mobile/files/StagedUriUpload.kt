package com.pqnas.mobile.files

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import java.io.InputStream
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File
import java.io.RandomAccessFile

private fun uploadUriMimeType(
    context: Context,
    uri: Uri
): String {
    return runCatching {
        context.contentResolver.getType(uri)
    }.getOrNull().orEmpty()
}

private fun equivalentMediaStoreUri(
    context: Context,
    uri: Uri
): Uri? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null

    if (uri.authority == MediaStore.AUTHORITY) {
        return uri
    }

    return runCatching {
        MediaStore.getMediaUri(context, uri)
    }.getOrNull()
}

private fun isMediaStoreImageUri(
    context: Context,
    uri: Uri
): Boolean {
    val mimeType = uploadUriMimeType(context, uri)
    if (!mimeType.startsWith("image/", ignoreCase = true)) return false

    return equivalentMediaStoreUri(context, uri) != null
}

fun requiresOriginalPhotoAccess(
    context: Context,
    uri: Uri
): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
        isMediaStoreImageUri(context, uri)
}

private fun openUploadInputStream(
    context: Context,
    uri: Uri
): InputStream? {
    val resolver = context.contentResolver

    if (!isMediaStoreImageUri(context, uri)) {
        return resolver.openInputStream(uri)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val options = Bundle().apply {
            // Privacy/integrity: ask the selected document provider for the
            // original media bytes instead of an EXIF-GPS-redacted copy.
            putBoolean(
                MediaStore.EXTRA_ACCEPT_ORIGINAL_MEDIA_FORMAT,
                true
            )
        }

        val mimeType = uploadUriMimeType(context, uri)
            .ifBlank { "image/*" }

        val descriptor = resolver.openTypedAssetFileDescriptor(
            uri,
            mimeType,
            options
        ) ?: return null

        return descriptor.createInputStream()
    }

    if (uri.authority == MediaStore.AUTHORITY) {
        // Android 10–11 direct MediaStore URI path.
        return resolver.openInputStream(
            MediaStore.setRequireOriginal(uri)
        )
    }

    // Android 10–11 document providers do not support the newer original
    // media format option. Preserve the user-granted document URI access.
    return resolver.openInputStream(uri)
}

fun stageUriToTempFile(
    context: Context,
    uri: Uri,
    fileNameHint: String? = null
): File {
    val suffix = fileNameHint
        ?.substringAfterLast('.', "")
        ?.takeIf { it.isNotBlank() }
        ?.let { ".$it" }
        ?: ".bin"

    val tempFile = File.createTempFile("pqnas_upload_", suffix, context.cacheDir)

    try {
        openUploadInputStream(context, uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
                output.flush()
            }
        } ?: throw IllegalStateException("Could not open input stream")
    } catch (e: Exception) {
        // Do not leave partially staged private media in the application cache.
        tempFile.delete()
        throw e
    }

    return tempFile
}

fun tempFileRequestBody(
    file: File,
    mimeType: String? = null,
    onProgress: (sentBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
): RequestBody {
    return object : RequestBody() {
        override fun contentType() = mimeType?.toMediaTypeOrNull()

        override fun contentLength(): Long = file.length()

        override fun writeTo(sink: BufferedSink) {
            file.inputStream().use { input ->
                val total = file.length()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var uploaded = 0L

                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break

                    sink.write(buffer, 0, read)
                    uploaded += read
                    onProgress(uploaded, total)
                }

                sink.flush()
            }
        }
    }
}


fun tempFileSliceRequestBody(
    file: File,
    offset: Long,
    byteCount: Long,
    mimeType: String? = null,
    onProgress: (sentBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
): RequestBody {
    return object : RequestBody() {
        override fun contentType() = mimeType?.toMediaTypeOrNull()

        override fun contentLength(): Long = byteCount

        override fun writeTo(sink: BufferedSink) {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(offset)

                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var remaining = byteCount
                var uploaded = 0L

                while (remaining > 0L) {
                    val want = minOf(buffer.size.toLong(), remaining).toInt()
                    val read = raf.read(buffer, 0, want)
                    if (read == -1) break

                    sink.write(buffer, 0, read)
                    uploaded += read.toLong()
                    remaining -= read.toLong()

                    onProgress(uploaded, byteCount)
                }

                sink.flush()
            }
        }
    }
}

