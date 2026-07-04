package com.pqnas.mobile.ui.screens

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh

import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.pqnas.mobile.R
import com.pqnas.mobile.files.FilesRepository
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow
import com.pqnas.mobile.files.FileScope
import com.pqnas.mobile.files.ScopedFilesOps
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import android.graphics.Typeface
import android.graphics.Rect
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.KeyListener
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.height
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.OverScroller
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen(
    filesRepository: FilesRepository,
    fileScope: FileScope = FileScope.User,
    relPath: String,
    displayName: String,
    onClose: () -> Unit,
    onSaved: () -> Unit
) {
    val uiScope = rememberCoroutineScope()
    val context = LocalContext.current
    val scopedOps = remember(filesRepository, context) {
        ScopedFilesOps(filesRepository, context.applicationContext)
    }

    var editorValue by remember(relPath) { mutableStateOf(TextFieldValue("")) }
    var originalText by remember(relPath) { mutableStateOf("") }
    var editorDirty by remember(relPath) { mutableStateOf(false) }
    var encoding by remember(relPath) { mutableStateOf("utf-8") }
    var mtimeEpoch by remember(relPath) { mutableStateOf<Long?>(null) }
    var sha256 by remember(relPath) { mutableStateOf<String?>(null) }

    var loading by remember(relPath) { mutableStateOf(true) }
    var saving by remember(relPath) { mutableStateOf(false) }
    var status by remember(relPath) { mutableStateOf(context.getString(R.string.text_editor_status_loading)) }
    var statusIsError by remember(relPath) { mutableStateOf(false) }
    var statusIsOk by remember(relPath) { mutableStateOf(false) }

    var showFindBar by remember(relPath) { mutableStateOf(false) }
    var findQuery by remember(relPath) { mutableStateOf("") }
    var matchCase by remember(relPath) { mutableStateOf(false) }

    var showDiscardDialog by remember(relPath) { mutableStateOf(false) }
    var showReloadDialog by remember(relPath) { mutableStateOf(false) }
    var readOnly by remember(relPath) { mutableStateOf(false) }
    var editMode by remember(relPath) { mutableStateOf(false) }
    var leaseHeartbeatJob by remember(relPath) { mutableStateOf<Job?>(null) }

    val editorBridge = remember(relPath) {
        object {
            var suppressCallbacks = false
            var latestText = ""
        }
    }
    val editorPaddingPx = with(LocalDensity.current) { 12.dp.roundToPx() }
    val surfaceColor = MaterialTheme.colorScheme.surface.toArgb()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    var editorScrollY by remember(relPath) { mutableIntStateOf(0) }
    var editorScrollRange by remember(relPath) { mutableIntStateOf(0) }
    var editorViewportHeightPx by remember(relPath) { mutableIntStateOf(0) }
    var editorHasFocus by remember(relPath) { mutableStateOf(false) }
    var editorViewRef by remember(relPath) { mutableStateOf<ScrollAwareEditText?>(null) }
    var editorSelectionStart by remember(relPath) { mutableIntStateOf(0) }

    val dirty = editorDirty
    val matches = remember(editorValue.text, findQuery, matchCase) {
        findMatches(
            fullText = editorValue.text,
            query = findQuery,
            matchCase = matchCase
        )
    }
    val findStatus = remember(matches, findQuery, editorSelectionStart) {
        computeFindStatus(
            context = context,
            matches = matches,
            query = findQuery,
            selectedStart = editorSelectionStart
        )
    }

    val editorByteCount = remember(editorValue.text) {
        editorValue.text.toByteArray(Charsets.UTF_8).size.toLong()
    }
    fun currentEditorText(): String {
        return editorBridge.latestText
    }

    fun setEditorStatus(value: String, isError: Boolean = false, isOk: Boolean = false) {
        status = value
        statusIsError = isError
        statusIsOk = isOk
    }


    fun enterEditMode(selectionOffset: Int? = null) {
        if (loading || saving || readOnly) return

        editMode = true

        editorViewRef?.post {
            val view = editorViewRef ?: return@post

            val editableKeyListener = view.getTag() as? KeyListener
            if (editableKeyListener != null && view.keyListener !== editableKeyListener) {
                view.keyListener = editableKeyListener
            }

            view.showSoftInputOnFocus = true
            view.isFocusable = true
            view.isFocusableInTouchMode = true
            view.isCursorVisible = true
            view.requestFocus()

            val textLen = view.text?.length ?: 0
            val fallbackSelection = when {
                view.selectionStart >= 0 -> view.selectionStart
                else -> editorSelectionStart
            }

            val targetSelection = (selectionOffset ?: fallbackSelection).coerceIn(0, textLen)
            view.setSelection(targetSelection)
            editorSelectionStart = targetSelection

            val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    fun exitEditMode() {
        editMode = false

        val view = editorViewRef
        view?.apply {
            isCursorVisible = false
            showSoftInputOnFocus = false
            clearFocus()
        }

        val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view?.windowToken, 0)
    }

    fun stopLeaseHeartbeat() {
        leaseHeartbeatJob?.cancel()
        leaseHeartbeatJob = null
    }

    fun leaseLockedMessage(raw: String): String {
        val lower = raw.lowercase(Locale.getDefault())
        return when {
            "edit_locked" in lower ->
                context.getString(R.string.text_editor_lock_edited_elsewhere)
            "edit_lock_missing" in lower ->
                context.getString(R.string.text_editor_lock_missing)
            else ->
                context.getString(R.string.text_editor_read_only)
        }
    }

    fun startLeaseHeartbeat() {
        stopLeaseHeartbeat()

        if (fileScope !is FileScope.Workspace || !fileScope.canWrite) return

        leaseHeartbeatJob = uiScope.launch {
            while (isActive) {
                delay(20_000L)
                try {
                    scopedOps.refreshEditLease(fileScope, relPath)
                } catch (e: Exception) {
                    stopLeaseHeartbeat()
                    readOnly = true
                    setEditorStatus(leaseLockedMessage(e.message.orEmpty()), isError = true)
                }
            }
        }
    }
    suspend fun loadFile() {
        loading = true
        setEditorStatus(context.getString(R.string.text_editor_status_loading))
        stopLeaseHeartbeat()

        try {
            val resp = scopedOps.readText(fileScope, relPath)
            if (!resp.ok) {
                throw IllegalStateException(composeApiMessage(resp.error, resp.message, context.getString(R.string.text_editor_read_text_failed)))
            }

            val text = resp.text ?: ""
            editorValue = TextFieldValue(text = text)
            originalText = text
            editorBridge.latestText = text
            editorDirty = false
            editorSelectionStart = 0
            encoding = resp.encoding ?: "utf-8"
            mtimeEpoch = resp.mtime_epoch
            sha256 = resp.sha256

            readOnly = false

            if (fileScope is FileScope.Workspace) {
                if (!fileScope.canWrite) {
                    readOnly = true
                    setEditorStatus(context.getString(R.string.text_editor_read_only_role), isError = true)
                } else {
                    try {
                        scopedOps.acquireEditLease(fileScope, relPath)
                        readOnly = false
                        startLeaseHeartbeat()
                        setEditorStatus(context.getString(R.string.text_editor_status_ok), isOk = true)
                    } catch (e: Exception) {
                        readOnly = true
                        setEditorStatus(leaseLockedMessage(e.message.orEmpty()), isError = true)
                    }
                }
            } else {
                setEditorStatus(context.getString(R.string.text_editor_status_ok), isOk = true)
            }

            loading = false
        } catch (e: Exception) {
            loading = false
            readOnly = true
            setEditorStatus(friendlyTextEditorMessage(context, context.getString(R.string.text_editor_action_read_text), e), isError = true)
        }
    }

    fun saveFile() {
        if (loading || saving || !dirty || readOnly) return

        uiScope.launch {
            saving = true
            setEditorStatus(context.getString(R.string.text_editor_status_saving))

            try {
                if (fileScope is FileScope.Workspace && fileScope.canWrite) {
                    scopedOps.refreshEditLease(fileScope, relPath)
                }

                val textToSave = currentEditorText()

                val resp = scopedOps.writeText(
                    scope = fileScope,
                    path = relPath,
                    text = textToSave,
                    expectedMtimeEpoch = mtimeEpoch,
                    expectedSha256 = sha256
                )

                if (!resp.ok) {
                    throw IllegalStateException(composeApiMessage(resp.error, resp.message, context.getString(R.string.text_editor_write_text_failed)))
                }

                originalText = textToSave
                editorBridge.latestText = textToSave
                editorValue = TextFieldValue(text = textToSave)
                editorDirty = false
                mtimeEpoch = resp.mtime_epoch ?: mtimeEpoch
                sha256 = resp.sha256 ?: sha256
                saving = false
                exitEditMode()
                setEditorStatus(context.getString(R.string.text_editor_status_saved), isOk = true)
                onSaved()
            } catch (e: Exception) {
                saving = false

                val raw = e.message.orEmpty().lowercase(Locale.getDefault())
                setEditorStatus(
                    when {
                        "changed_on_server" in raw ->
                            context.getString(R.string.text_editor_changed_on_server)
                        "edit_locked" in raw || "edit_lock_missing" in raw -> {
                            readOnly = true
                            leaseLockedMessage(e.message.orEmpty())
                        }
                        else ->
                            friendlyTextEditorMessage(context, context.getString(R.string.text_editor_action_write_text), e)
                    },
                    isError = true
                )
            }
        }
    }

    fun jumpToMatch(start: Int) {
        if (findQuery.isBlank()) return
        val end = (start + findQuery.length).coerceAtMost(currentEditorText().length)
        editorValue = editorValue.copy(
            selection = TextRange(start, end)
        )
    }

    fun findNext() {
        if (findQuery.isBlank()) return
        if (matches.isEmpty()) return

        val startPos = editorValue.selection.end.coerceAtLeast(0)
        val next = matches.firstOrNull { it >= startPos } ?: matches.first()
        jumpToMatch(next)
    }

    fun findPrev() {
        if (findQuery.isBlank()) return
        if (matches.isEmpty()) return

        val startPos = (editorValue.selection.start - 1).coerceAtLeast(0)
        val prev = matches.lastOrNull { it <= startPos } ?: matches.last()
        jumpToMatch(prev)
    }

    fun requestClose() {
        if (saving) return
        if (dirty) {
            showDiscardDialog = true
        } else {
            uiScope.launch {
                stopLeaseHeartbeat()
                runCatching { scopedOps.releaseEditLease(fileScope, relPath) }
                onClose()
            }
        }
    }

    fun requestReload() {
        if (saving) return
        if (dirty) {
            showReloadDialog = true
        } else {
            uiScope.launch { loadFile() }
        }
    }

    BackHandler {
        if (editMode || editorHasFocus) {
            exitEditMode()
        } else {
            requestClose()
        }
    }

    LaunchedEffect(relPath) {
        loadFile()
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
                            text = stringResource(R.string.text_editor_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { requestClose() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.text_editor_back)
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showFindBar = !showFindBar },
                            enabled = !loading && !saving
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = stringResource(R.string.text_editor_find)
                            )
                        }

                        IconButton(
                            onClick = { requestReload() },
                            enabled = !loading && !saving
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.text_editor_reload)
                            )
                        }

                        TextButton(
                            onClick = {
                                if (editMode) exitEditMode() else enterEditMode()
                            },
                            enabled = !loading && !saving && !readOnly
                        ) {
                            Text(if (editMode) stringResource(R.string.text_editor_done) else stringResource(R.string.text_editor_edit))
                        }

                        TextButton(
                            onClick = { saveFile() },
                            enabled = !loading && !saving && dirty
                        ) {
                            Text(if (saving) stringResource(R.string.text_editor_status_saving) else stringResource(R.string.text_editor_save))
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
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = "/$relPath",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.text_editor_encoding, encoding, formatBytes(editorByteCount)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = if (dirty) stringResource(R.string.text_editor_unsaved_changes) else stringResource(R.string.text_editor_no_local_changes),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (dirty) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Text(
                            text = if (readOnly) stringResource(R.string.text_editor_mode_read_only) else stringResource(R.string.text_editor_mode_editable),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (readOnly) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                statusIsOk -> MaterialTheme.colorScheme.tertiary
                                statusIsError -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                if (showFindBar) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = findQuery,
                                onValueChange = { findQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text(stringResource(R.string.text_editor_search)) },
                                enabled = !loading && !saving
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = matchCase,
                                    onClick = { matchCase = !matchCase },
                                    label = { Text(stringResource(R.string.text_editor_match_case)) },
                                    enabled = !loading && !saving
                                )

                                TextButton(
                                    onClick = { findPrev() },
                                    enabled = findQuery.isNotBlank() && matches.isNotEmpty()
                                ) {
                                    Text(stringResource(R.string.text_editor_prev))
                                }

                                TextButton(
                                    onClick = { findNext() },
                                    enabled = findQuery.isNotBlank() && matches.isNotEmpty()
                                ) {
                                    Text(stringResource(R.string.text_editor_next))
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                Text(
                                    text = findStatus,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (findQuery.isNotBlank() && matches.isEmpty()) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
                val surfaceColor = MaterialTheme.colorScheme.surface.toArgb()
                val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
                val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .onSizeChanged { editorViewportHeightPx = it.height }
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            ScrollAwareEditText(context).apply {
                                editorViewRef = this
                                canEdit = !loading && !saving && !readOnly
                                editingEnabled = editMode && !loading && !saving && !readOnly
                                onTapToEditAtOffset = { offset ->
                                    enterEditMode(offset)
                                }
                                onImeBackPressed = {
                                    exitEditMode()
                                }
                                onFocusChangedCallback = { focused ->
                                    editorHasFocus = focused
                                }

                                onSelectionChangedCallback = { selStart, _ ->
                                    if (!editorBridge.suppressCallbacks) {
                                        val textLen = text?.length ?: 0
                                        editorSelectionStart = selStart.coerceIn(0, textLen)
                                    }
                                }

                                onScrollMetricsChanged = { newScrollY, newScrollRange ->
                                    editorScrollY = newScrollY
                                    editorScrollRange = newScrollRange
                                }

                                typeface = Typeface.MONOSPACE
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)

                                gravity = Gravity.TOP or Gravity.START
                                inputType = InputType.TYPE_CLASS_TEXT or
                                        InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                                        InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS

                                isSingleLine = false
                                maxLines = Int.MAX_VALUE
                                setHorizontallyScrolling(false)
                                showSoftInputOnFocus = true

                                isVerticalScrollBarEnabled = false
                                isHorizontalScrollBarEnabled = false
                                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS

                                setPadding(
                                    editorPaddingPx,
                                    editorPaddingPx,
                                    editorPaddingPx,
                                    editorPaddingPx
                                )

                                setBackgroundColor(surfaceColor)
                                setTextColor(onSurfaceColor)
                                setHintTextColor(onSurfaceVariantColor)

                                val editableKeyListener = keyListener
                                setTag(editableKeyListener)

                                setText(editorValue.text)
                                val start = editorValue.selection.start.coerceIn(0, editorValue.text.length)
                                val end = editorValue.selection.end.coerceIn(0, editorValue.text.length)
                                setSelection(start, end)

                                post { publishScrollMetrics() }

                                addTextChangedListener(object : TextWatcher {
                                    override fun beforeTextChanged(
                                        s: CharSequence?,
                                        start: Int,
                                        count: Int,
                                        after: Int
                                    ) = Unit

                                    override fun onTextChanged(
                                        s: CharSequence?,
                                        start: Int,
                                        before: Int,
                                        count: Int
                                    ) = Unit

                                    override fun afterTextChanged(s: Editable?) {
                                        if (editorBridge.suppressCallbacks) return

                                        val newText = s?.toString().orEmpty()
                                        editorBridge.latestText = newText
                                        val selStart = selectionStart.coerceIn(0, newText.length)

                                        editorSelectionStart = selStart

                                        val nowDirty = newText != originalText
                                        if (editorDirty != nowDirty) {
                                            editorDirty = nowDirty
                                        }

                                        post { publishScrollMetrics() }
                                    }
                                })
                            }
                        },
                        update = { view ->
                            editorViewRef = view as? ScrollAwareEditText

                            val targetText = editorValue.text
                            val start = editorValue.selection.start.coerceIn(0, targetText.length)
                            val end = editorValue.selection.end.coerceIn(0, targetText.length)

                            view.setBackgroundColor(surfaceColor)
                            view.setTextColor(onSurfaceColor)
                            view.setHintTextColor(onSurfaceVariantColor)

                            val editableKeyListener = view.getTag() as? KeyListener
                            val editableNow = editMode && !loading && !saving && !readOnly
                            val desiredKeyListener = if (editableNow) editableKeyListener else null

                            if (view.keyListener !== desiredKeyListener) {
                                view.keyListener = desiredKeyListener
                            }

                            view.canEdit = !loading && !saving && !readOnly
                            view.editingEnabled = editableNow
                            view.onTapToEditAtOffset = { offset ->
                                enterEditMode(offset)
                            }
                            view.onImeBackPressed = {
                                exitEditMode()
                            }

                            view.showSoftInputOnFocus = editableNow

                            // Important:
                            // Keep the view focusable/touchable even outside edit mode.
                            // We make it read-only with keyListener = null instead.
                            view.isFocusable = true
                            view.isFocusableInTouchMode = true
                            view.isCursorVisible = editableNow

                            // Do not push stale Compose text back into the native EditText
                            // while there are unsaved native edits. Tapping Save/toolbar can
                            // clear focus before saveFile() reads the current native text.
                            if (!editorDirty && !view.hasFocus() && view.text?.toString() != targetText) {
                                editorBridge.suppressCallbacks = true
                                view.setText(targetText)
                                view.setSelection(start, end)
                                editorSelectionStart = start
                                editorBridge.suppressCallbacks = false
                            } else if (!editorDirty && !view.hasFocus() && (view.selectionStart != start || view.selectionEnd != end)) {
                                editorBridge.suppressCallbacks = true
                                view.setSelection(start, end)
                                editorSelectionStart = start
                                editorBridge.suppressCallbacks = false
                            }

                            (view as? ScrollAwareEditText)?.post {
                                view.publishScrollMetrics()
                            }
                        }
                    )

                    EditorPositionThumb(
                        scrollY = editorScrollY,
                        scrollRange = editorScrollRange,
                        viewportHeightPx = editorViewportHeightPx,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = { requestReload() },
                        enabled = !loading && !saving
                    ) {
                        Text(stringResource(R.string.text_editor_reload))
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    TextButton(
                        onClick = {
                            if (editMode) exitEditMode() else enterEditMode()
                        },
                        enabled = !loading && !saving && !readOnly
                    ) {
                        Text(if (editMode) stringResource(R.string.text_editor_done) else stringResource(R.string.text_editor_edit))
                    }

                    TextButton(
                        onClick = { requestClose() },
                        enabled = !saving,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(stringResource(R.string.text_editor_close))
                    }

                    TextButton(
                        onClick = { saveFile() },
                        enabled = !loading && !saving && dirty
                    ) {
                        Text(if (saving) stringResource(R.string.text_editor_status_saving) else stringResource(R.string.text_editor_save))
                    }
                }
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.text_editor_discard_changes_title)) },
            text = { Text(stringResource(R.string.text_editor_discard_changes_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        uiScope.launch {
                            stopLeaseHeartbeat()
                            runCatching { scopedOps.releaseEditLease(fileScope, relPath) }
                            onClose()
                        }
                    }
                ) {
                    Text(stringResource(R.string.text_editor_discard))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDiscardDialog = false }
                ) {
                    Text(stringResource(R.string.text_editor_cancel))
                }
            }
        )
    }

    if (showReloadDialog) {
        AlertDialog(
            onDismissRequest = { showReloadDialog = false },
            title = { Text(stringResource(R.string.text_editor_reload_from_server_title)) },
            text = { Text(stringResource(R.string.text_editor_reload_from_server_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showReloadDialog = false
                        uiScope.launch { loadFile() }
                    }
                ) {
                    Text(stringResource(R.string.text_editor_reload))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showReloadDialog = false }
                ) {
                    Text(stringResource(R.string.text_editor_cancel))
                }
            }
        )
    }
}
private class ScrollAwareEditText(
    context: android.content.Context
) : android.widget.EditText(context) {

    var onSelectionChangedCallback: ((Int, Int) -> Unit)? = null
    var onScrollMetricsChanged: ((scrollY: Int, scrollRange: Int) -> Unit)? = null
    var onFocusChangedCallback: ((Boolean) -> Unit)? = null

    var canEdit: Boolean = false
    var editingEnabled: Boolean = false
    var onTapToEditAtOffset: ((Int) -> Unit)? = null
    var onImeBackPressed: (() -> Unit)? = null

    private val flingScroller = OverScroller(context)
    private val viewConfig = ViewConfiguration.get(context)
    private val touchSlop = viewConfig.scaledTouchSlop
    private val minFlingVelocity = viewConfig.scaledMinimumFlingVelocity
    private val maxFlingVelocity = viewConfig.scaledMaximumFlingVelocity

    private var velocityTracker: VelocityTracker? = null
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var dragging = false

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) {
                onImeBackPressed?.invoke()
            }

            // Consume keyboard back so it does not bubble up and close the editor.
            return true
        }

        return super.onKeyPreIme(keyCode, event)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Accessibility: browse-mode taps are routed through performClick(),
        // but this custom editor also needs low-level touch handling for
        // inertial scrolling and tap-to-edit offset selection.
        if (!editingEnabled) {
            return handleBrowseTouch(event)
        }

        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            flingScroller.forceFinished(true)
        }

        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        // Accessibility: custom touch handling must still expose a click action
        // so TalkBack/keyboard users can activate tap-to-edit behavior.
        super.performClick()

        if (canEdit && !editingEnabled) {
            val textLen = text?.length ?: 0
            val offset = selectionStart.coerceIn(0, textLen)
            onTapToEditAtOffset?.invoke(offset)
        }

        return true
    }

    private fun handleBrowseTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                flingScroller.forceFinished(true)

                parent?.requestDisallowInterceptTouchEvent(true)

                downX = event.x
                downY = event.y
                lastY = event.y
                dragging = false

                recycleVelocityTracker()
                velocityTracker = VelocityTracker.obtain().also {
                    it.addMovement(event)
                }

                return true
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)

                val dx = abs(event.x - downX)
                val dy = abs(event.y - downY)

                if (!dragging && (dx > touchSlop || dy > touchSlop)) {
                    dragging = true
                }

                if (dragging) {
                    val deltaY = (lastY - event.y).toInt()
                    if (deltaY != 0) {
                        scrollTo(scrollX, clampVerticalScroll(scrollY + deltaY))
                        publishScrollMetrics()
                    }
                    lastY = event.y
                }

                return true
            }

            MotionEvent.ACTION_UP -> {
                velocityTracker?.addMovement(event)
                parent?.requestDisallowInterceptTouchEvent(false)

                if (dragging) {
                    velocityTracker?.computeCurrentVelocity(
                        1000,
                        maxFlingVelocity.toFloat()
                    )

                    val velocityY = velocityTracker?.yVelocity ?: 0f
                    val maxScroll = maxVerticalScroll()

                    if (abs(velocityY) >= minFlingVelocity && maxScroll > 0) {
                        flingScroller.fling(
                            scrollX,
                            scrollY,
                            0,
                            (-velocityY).toInt(),
                            0,
                            0,
                            0,
                            maxScroll
                        )
                        postInvalidateOnAnimation()
                    }
                } else if (canEdit) {
                    val textLen = text?.length ?: 0
                    val offset = getOffsetForPosition(event.x, event.y)
                        .coerceIn(0, textLen)

                    // Accessibility: route real tap activation through performClick()
                    // while preserving the exact tapped text offset for edit mode.
                    setSelection(offset)
                    performClick()
                }

                dragging = false
                recycleVelocityTracker()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                dragging = false
                recycleVelocityTracker()
                return true
            }
        }

        return true
    }

    override fun computeScroll() {
        super.computeScroll()

        if (flingScroller.computeScrollOffset()) {
            scrollTo(scrollX, clampVerticalScroll(flingScroller.currY))
            publishScrollMetrics()
            postInvalidateOnAnimation()
        }
    }

    override fun onFocusChanged(
        focused: Boolean,
        direction: Int,
        previouslyFocusedRect: Rect?
    ) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        onFocusChangedCallback?.invoke(focused)
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onSelectionChangedCallback?.invoke(selStart, selEnd)
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        publishScrollMetrics()
    }

    override fun onLayout(
        changed: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        super.onLayout(changed, left, top, right, bottom)
        publishScrollMetrics()
    }

    private fun clampVerticalScroll(value: Int): Int {
        return value.coerceIn(0, maxVerticalScroll())
    }

    private fun maxVerticalScroll(): Int {
        return (computeVerticalScrollRange() - height).coerceAtLeast(0)
    }

    private fun recycleVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    fun publishScrollMetrics() {
        onScrollMetricsChanged?.invoke(scrollY, maxVerticalScroll())
    }
}


private fun findMatches(
    fullText: String,
    query: String,
    matchCase: Boolean
): List<Int> {
    if (query.isBlank()) return emptyList()

    val haystack = if (matchCase) fullText else fullText.lowercase(Locale.getDefault())
    val needle = if (matchCase) query else query.lowercase(Locale.getDefault())
    val out = mutableListOf<Int>()

    var pos = 0
    while (true) {
        val idx = haystack.indexOf(needle, pos)
        if (idx < 0) break
        out += idx
        pos = idx + maxOf(1, needle.length)
    }

    return out
}

private fun computeFindStatus(
    context: android.content.Context,
    matches: List<Int>,
    query: String,
    selectedStart: Int
): String {
    if (query.isBlank()) return ""
    if (matches.isEmpty()) return context.getString(R.string.text_editor_not_found)

    val exact = matches.indexOf(selectedStart)
    val current = when {
        exact >= 0 -> exact
        else -> matches.indexOfFirst { it >= selectedStart }.takeIf { it >= 0 } ?: 0
    }

    return "${current + 1} / ${matches.size}"
}

private fun composeApiMessage(
    error: String?,
    message: String?,
    fallback: String
): String {
    val left = error?.trim().orEmpty()
    val right = message?.trim().orEmpty()

    return when {
        left.isNotBlank() && right.isNotBlank() -> "$left: $right"
        left.isNotBlank() -> left
        right.isNotBlank() -> right
        else -> fallback
    }
}

private fun friendlyTextEditorMessage(
    context: android.content.Context,
    action: String,
    error: Throwable
): String {
    val msg = error.message?.trim().orEmpty()
    val lower = msg.lowercase(Locale.getDefault())

    return when {
        "changed_on_server" in lower || "changed on server" in lower ->
            context.getString(R.string.text_editor_changed_on_server)

        "edit_locked" in lower ->
            context.getString(R.string.text_editor_lock_edited_elsewhere)

        "edit_lock_missing" in lower ->
            context.getString(R.string.text_editor_lock_missing)

        msg.contains("HTTP 400", ignoreCase = true) ->
            context.getString(R.string.text_editor_action_failed_invalid_request, action)

        msg.contains("HTTP 401", ignoreCase = true) ->
            context.getString(R.string.text_editor_session_expired)

        msg.contains("HTTP 403", ignoreCase = true) ->
            context.getString(R.string.text_editor_access_denied)

        msg.contains("HTTP 404", ignoreCase = true) ->
            context.getString(R.string.text_editor_item_not_found)

        msg.contains("HTTP 409", ignoreCase = true) ->
            context.getString(R.string.text_editor_action_failed_conflict, action)

        msg.contains("HTTP 413", ignoreCase = true) ->
            context.getString(R.string.text_editor_action_failed_too_large, action)

        msg.contains("HTTP 500", ignoreCase = true) ->
            context.getString(R.string.text_editor_action_failed_server_error, action)

        msg.isNotBlank() ->
            context.getString(R.string.text_editor_action_failed_with_message, action, msg)

        else ->
            context.getString(R.string.text_editor_action_failed_unknown, action)
    }
}
@Composable
private fun EditorPositionThumb(
    scrollY: Int,
    scrollRange: Int,
    viewportHeightPx: Int,
    modifier: Modifier = Modifier
) {
    if (viewportHeightPx <= 0 || scrollRange <= 0) return

    val density = LocalDensity.current
    val viewport = viewportHeightPx.toFloat()
    val contentHeight = viewport + scrollRange.toFloat()

    val minThumbHeightPx = with(density) { 36.dp.toPx() }
    val thumbHeightPx = ((viewport * viewport) / contentHeight)
        .coerceAtLeast(minThumbHeightPx)
        .coerceAtMost(viewport)

    val travelPx = (viewport - thumbHeightPx).coerceAtLeast(0f)
    val thumbOffsetPx =
        if (scrollRange <= 0) 0f
        else (scrollY.toFloat() / scrollRange.toFloat()) * travelPx

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(10.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxHeight()
                .width(3.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
                )
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = with(density) { thumbOffsetPx.toDp() })
                .width(6.dp)
                .height(with(density) { thumbHeightPx.toDp() })
                .clip(RoundedCornerShape(999.dp))
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                )
        )
    }
}
private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB", "PB")
    val digitGroups = (ln(bytes.toDouble()) / ln(1024.0)).toInt()
    val value = bytes / 1024.0.pow(digitGroups.toDouble())
    return String.format(Locale.US, "%.1f %s", value, units[digitGroups - 1])
}
