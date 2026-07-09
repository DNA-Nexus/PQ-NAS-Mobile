package com.pqnas.mobile.ui.screens

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pqnas.mobile.R
import com.pqnas.mobile.files.FileScope
import com.pqnas.mobile.files.FilesRepository
import com.pqnas.mobile.files.ScopedFilesOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPreviewScreen(
    filesRepository: FilesRepository,
    fileScope: FileScope,
    relPath: String,
    displayName: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scopedOps = remember(filesRepository, context) {
        ScopedFilesOps(filesRepository, context)
    }

    var pdfFile by remember(relPath) { mutableStateOf<File?>(null) }
    var pageBitmap by remember(relPath) { mutableStateOf<Bitmap?>(null) }
    var pageCount by remember(relPath) { mutableIntStateOf(0) }
    var currentPage by remember(relPath) { mutableIntStateOf(0) }
    var zoom by remember(relPath) { mutableFloatStateOf(1f) }
    var panX by remember(relPath) { mutableFloatStateOf(0f) }
    var panY by remember(relPath) { mutableFloatStateOf(0f) }
    var loading by remember(relPath) { mutableStateOf(true) }
    var status by remember(relPath) {
        mutableStateOf(context.getString(R.string.image_preview_loading))
    }

    BackHandler { onClose() }

    // Recycle old rendered pages when the page bitmap changes or preview closes.
    DisposableEffect(pageBitmap) {
        val capturedBitmap = pageBitmap
        onDispose {
            capturedBitmap?.recycle()
        }
    }

    // Delete the temporary cached PDF when another file is opened or preview closes.
    DisposableEffect(pdfFile) {
        val capturedFile = pdfFile
        onDispose {
            capturedFile?.delete()
        }
    }

    LaunchedEffect(fileScope, relPath) {
        loading = true
        status = context.getString(R.string.image_preview_loading)
        pageBitmap = null
        pdfFile = null
        pageCount = 0
        currentPage = 0
        zoom = 1f
        panX = 0f
        panY = 0f

        var downloaded: File? = null

        try {
            downloaded = withContext(Dispatchers.IO) {
                val body = scopedOps.download(fileScope, relPath)
                copyPdfToTempFile(body, context.cacheDir)
            }

            val count = withContext(Dispatchers.IO) {
                openPdfRenderer(downloaded) { renderer ->
                    renderer.pageCount
                }
            }

            pdfFile = downloaded
            downloaded = null
            pageCount = count
            status = ""
        } catch (e: Exception) {
            downloaded?.delete()
            status = e.message ?: context.getString(R.string.image_preview_decode_failed)
        } finally {
            loading = false
        }
    }

    LaunchedEffect(pdfFile, currentPage) {
        zoom = 1f
        panX = 0f
        panY = 0f

        val file = pdfFile ?: return@LaunchedEffect
        if (pageCount <= 0) return@LaunchedEffect

        try {
            loading = true
            status = ""

            val rendered = withContext(Dispatchers.IO) {
                renderPdfPage(file, currentPage)
            }

            pageBitmap = rendered
        } catch (e: Exception) {
            status = e.message ?: context.getString(R.string.image_preview_decode_failed)
        } finally {
            loading = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = displayName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.text_editor_back)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(innerPadding)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .pointerInput(pageBitmap, currentPage) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (zoom > 1.01f) {
                                        zoom = 1f
                                        panX = 0f
                                        panY = 0f
                                    } else {
                                        zoom = 2.5f
                                        panX = 0f
                                        panY = 0f
                                    }
                                }
                            )
                        }
                        .pointerInput(pageBitmap, currentPage, pageCount, zoom) {
                            var swipeX = 0f

                            detectTransformGestures { _, pan, zoomChange, _ ->
                                val nextZoom = (zoom * zoomChange).coerceIn(1f, 5f)
                                val isZoomGesture = kotlin.math.abs(zoomChange - 1f) > 0.01f

                                if (nextZoom <= 1.01f) {
                                    zoom = 1f
                                    panX = 0f
                                    panY = 0f

                                    // At normal zoom, use horizontal pan as page swipe.
                                    // When zoomed in, the same gesture is reserved for panning the page.
                                    if (!isZoomGesture && pageCount > 1) {
                                        swipeX += pan.x
                                        val swipeThresholdPx = 140f

                                        if (swipeX <= -swipeThresholdPx && currentPage < pageCount - 1) {
                                            currentPage += 1
                                            swipeX = 0f
                                        } else if (swipeX >= swipeThresholdPx && currentPage > 0) {
                                            currentPage -= 1
                                            swipeX = 0f
                                        }
                                    }
                                } else {
                                    val maxPan = 3000f * nextZoom
                                    zoom = nextZoom
                                    swipeX = 0f
                                    panX = (panX + pan.x).coerceIn(-maxPan, maxPan)
                                    panY = (panY + pan.y).coerceIn(-maxPan, maxPan)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val bitmap = pageBitmap

                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = displayName,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = zoom,
                                    scaleY = zoom,
                                    translationX = panX,
                                    translationY = panY
                                ),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text(
                            text = status.ifBlank {
                                stringResource(R.string.image_preview_loading)
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }

                if (status.isNotBlank() && pageBitmap != null) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            currentPage = (currentPage - 1).coerceAtLeast(0)
                        },
                        enabled = !loading && currentPage > 0
                    ) {
                        Text(stringResource(R.string.image_preview_previous))
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = if (pageCount > 0) {
                            "${currentPage + 1} / $pageCount"
                        } else {
                            ""
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    TextButton(
                        onClick = {
                            currentPage = (currentPage + 1).coerceAtMost(pageCount - 1)
                        },
                        enabled = !loading && pageCount > 0 && currentPage < pageCount - 1
                    ) {
                        Text(stringResource(R.string.image_preview_next))
                    }
                }
            }
        }
    }
}

private fun copyPdfToTempFile(responseBody: ResponseBody, cacheDir: File): File {
    val tempFile = File.createTempFile("preview-", ".pdf", cacheDir)

    responseBody.use { body ->
        tempFile.outputStream().use { out ->
            body.byteStream().use { input ->
                input.copyTo(out)
            }
        }
    }

    return tempFile
}

private inline fun <T> openPdfRenderer(file: File, block: (PdfRenderer) -> T): T {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        PdfRenderer(pfd).use { renderer ->
            return block(renderer)
        }
    }
}

private fun renderPdfPage(file: File, pageIndex: Int): Bitmap =
    openPdfRenderer(file) { renderer ->
        val safePageIndex = pageIndex.coerceIn(0, renderer.pageCount - 1)

        renderer.openPage(safePageIndex).use { page ->
            // Render at a reasonable size so large PDFs do not create huge bitmaps.
            val scale = (1800f / page.width.toFloat())
                .coerceAtMost(2.0f)
                .coerceAtLeast(0.25f)

            val bitmap = Bitmap.createBitmap(
                (page.width * scale).toInt().coerceAtLeast(1),
                (page.height * scale).toInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )

            bitmap.eraseColor(Color.WHITE)

            val matrix = Matrix().apply {
                postScale(scale, scale)
            }

            page.render(
                bitmap,
                null,
                matrix,
                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
            )

            bitmap
        }
    }
