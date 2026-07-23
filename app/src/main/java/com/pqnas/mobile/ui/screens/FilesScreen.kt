package com.pqnas.mobile.ui.screens
import com.pqnas.mobile.api.DropZoneUploadDto

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper

import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pqnas.mobile.api.FileItemDto
import com.pqnas.mobile.R
import com.pqnas.mobile.BuildConfig
import com.pqnas.mobile.api.MeStorageResponse
import com.pqnas.mobile.api.DropZoneBrandingDto
import com.pqnas.mobile.api.DropZoneInfo
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.pqnas.mobile.files.FileTypeIcons
import com.pqnas.mobile.files.FilesRepository
import com.pqnas.mobile.files.SvgIconLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow
import androidx.compose.material3.RadioButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import com.pqnas.mobile.api.WorkspaceListItemDto
import com.pqnas.mobile.files.FileScope
import com.pqnas.mobile.ui.theme.PqnasAppTheme
import com.pqnas.mobile.ui.settings.PqnasAppLanguage
import com.pqnas.mobile.files.FileListCache
import com.pqnas.mobile.files.ScopedFilesOps
import com.pqnas.mobile.files.listWorkspaces
import okhttp3.RequestBody.Companion.toRequestBody
import com.pqnas.mobile.files.stageUriToTempFile
import com.pqnas.mobile.files.requiresOriginalPhotoAccess
import java.io.File
import org.json.JSONObject
import com.pqnas.mobile.echostack.EchoStackRepository
import com.pqnas.mobile.circlestack.CircleStackRepository
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.platform.LocalConfiguration


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    filesRepository: FilesRepository,
    serverDisplayName: String = "",
    onLogout: (() -> Unit)? = null,
    onOpenContacts: (() -> Unit)? = null,
    onOpenAdmin: (() -> Unit)? = null,
    appTheme: PqnasAppTheme = PqnasAppTheme.Dark,
    onAppThemeChange: (PqnasAppTheme) -> Unit = {},
    appLanguage: PqnasAppLanguage,
    onAppLanguageChange: (PqnasAppLanguage) -> Unit,
    onBeforeExternalPicker: () -> Unit = {},
    incomingShareManifestPath: String? = null,
    incomingShareNonce: Int = 0,
    onIncomingShareConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val fallbackAppTitle = stringResource(R.string.dna_nexus_files)
    val appTitle = serverDisplayName.trim().ifBlank { fallbackAppTitle }
    val aboutAppTitle = stringResource(R.string.about_connected_server, appTitle)
    val appsForServerText = stringResource(R.string.apps_available_mobile_tools_for_server, appTitle)
    val serverHost = filesRepository.baseUrlForDisplay()
        .removePrefix("https://")
        .removePrefix("http://")
        .trimEnd('/')

    val initialFileListCache = remember(context) {
        FileListCache(context.applicationContext)
    }
    val initialCachedUserRoot = remember(filesRepository, initialFileListCache) {
        initialFileListCache.load(
            namespace = filesRepository.baseUrlForDisplay(),
            scope = FileScope.User,
            path = null
        )
    }

    var currentPath by remember { mutableStateOf<String?>(initialCachedUserRoot?.path) }
    var items by remember {
        mutableStateOf<List<FileItemDto>>(initialCachedUserRoot?.items ?: emptyList())
    }
    var status by remember {
        mutableStateOf(
            if (initialCachedUserRoot != null) {
                "Cached files — refreshing..."
            } else {
                "Loading..."
            }
        )
    }
    var listLoading by remember { mutableStateOf(initialCachedUserRoot == null) }
    var startupEmptyStateGrace by remember { mutableStateOf(initialCachedUserRoot == null) }
    var myStorage by remember { mutableStateOf<MeStorageResponse?>(null) }
    var storageStatus by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var commentedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }

    var shareDialogItem by remember { mutableStateOf<FileItemDto?>(null) }
    var shareDialogUrl by remember { mutableStateOf("") }
    var shareDialogStatus by remember { mutableStateOf("") }
    var shareDialogExistingToken by remember { mutableStateOf<String?>(null) }
    var shareDialogExpiry by remember { mutableStateOf(defaultShareExpiryOption()) }

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showAppSettingsDialog by remember { mutableStateOf(false) }
    var showAppsSheet by remember { mutableStateOf(false) }
    var showSharesManager by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var dropZoneAvailable by remember { mutableStateOf(false) }
    var echoStackAvailable by remember { mutableStateOf(false) }
    var circleStackAvailable by remember { mutableStateOf(false) }
    var contactsAvailable by remember { mutableStateOf(false) }
    var appsChecked by remember { mutableStateOf(false) }
    var showDropZoneSheet by remember { mutableStateOf(false) }
    var dropZones by remember { mutableStateOf<List<DropZoneInfo>>(emptyList()) }
    var dropZoneLoading by remember { mutableStateOf(false) }
    var dropZoneCreating by remember { mutableStateOf(false) }
    var dropZoneStatus by remember { mutableStateOf("") }
    var dropZoneHistoryOpen by remember { mutableStateOf(false) }
    var dropZoneHistoryTitle by remember { mutableStateOf("") }
    var dropZoneHistoryLoading by remember { mutableStateOf(false) }
    var dropZoneHistoryStatus by remember { mutableStateOf("") }
    var dropZoneHistoryUploads by remember { mutableStateOf<List<DropZoneUploadDto>>(emptyList()) }
    var dropZoneLatestUrl by remember { mutableStateOf("") }
    var showEchoStackScreen by remember { mutableStateOf(false) }
    var showCircleStackScreen by remember { mutableStateOf(false) }
    // PQNAS_ANDROID_WORKSPACE_MESSAGES_LINKS_V1
    var showWorkspaceMessagesSheet by remember { mutableStateOf(false) }
    var showWorkspaceUrlLinkDialog by remember { mutableStateOf(false) }

    var dropZoneName by remember { mutableStateOf(context.getString(R.string.drop_zone_name_placeholder)) }
    var dropZoneDestination by remember { mutableStateOf("") }
    var dropZonePassword by remember { mutableStateOf("") }
    var dropZoneExpiresInSeconds by remember { mutableStateOf(7L * 24L * 60L * 60L) }
    var dropZoneMaxFileBytes by remember { mutableStateOf("") }
    var dropZoneMaxTotalBytes by remember { mutableStateOf("") }
    var dropZoneDuplicatePolicy by remember { mutableStateOf("version") }
    var dropZoneBrandingCompanyName by remember { mutableStateOf("") }
    var dropZoneBrandingKicker by remember { mutableStateOf(context.getString(R.string.drop_zone_kicker_placeholder)) }
    var dropZoneBrandingTitle by remember { mutableStateOf(context.getString(R.string.drop_zone_public_title_placeholder)) }
    var dropZoneBrandingDescription by remember { mutableStateOf(context.getString(R.string.drop_zone_public_desc_default)) }
    var dropZoneBrandingButtonText by remember { mutableStateOf(context.getString(R.string.drop_zone_button_text_placeholder)) }
    var dropZoneBrandingFooterText by remember { mutableStateOf(context.getString(R.string.drop_zone_footer_text_placeholder)) }
    var dropZoneBrandingLogoUrl by remember { mutableStateOf("") }
    var dropZoneBrandingPrimaryColor by remember { mutableStateOf("#ff9f1a") }
    var dropZoneBrandingBackgroundColor by remember { mutableStateOf("#070a10") }
    var dropZoneBrandingPanelColor by remember { mutableStateOf("#15161d") }
    var dropZoneBrandingTextColor by remember { mutableStateOf("#f4f4f6") }
    var dropZoneBrandingButtonTextColor by remember { mutableStateOf("#000000") }

    var infoItem by remember { mutableStateOf<FileItemDto?>(null) }
    var infoNoteText by remember { mutableStateOf("") }
    var infoNoteOriginalText by remember { mutableStateOf("") }
    var infoNoteLoading by remember { mutableStateOf(false) }
    var infoNoteSaving by remember { mutableStateOf(false) }
    var infoNoteStatus by remember { mutableStateOf("") }
    var versionsItem by remember { mutableStateOf<FileItemDto?>(null) }
    var pendingDownloadItem by remember { mutableStateOf<FileItemDto?>(null) }
    var renameItem by remember { mutableStateOf<FileItemDto?>(null) }
    var pendingUploadUri by remember { mutableStateOf<Uri?>(null) }
    var pendingUploadName by remember { mutableStateOf<String?>(null) }

    // Hold the picker result while Android asks whether original photo
    // location metadata may be accessed.
    var pendingMediaLocationUploadUri by remember {
        mutableStateOf<Uri?>(null)
    }
    var pendingMediaLocationUploadUris by remember {
        mutableStateOf<List<Uri>>(emptyList())
    }
    var renameText by remember { mutableStateOf("") }
    var moveCopyItem by remember { mutableStateOf<FileItemDto?>(null) }
    var moveCopyMode by remember { mutableStateOf("Move") }
    var moveCopyDestination by remember { mutableStateOf("") }
    var moveCopyPickerPath by remember { mutableStateOf<String?>(null) }
    var moveCopyPickerFolders by remember { mutableStateOf<List<FileItemDto>>(emptyList()) }
    var moveCopyPickerLoading by remember { mutableStateOf(false) }
    var moveCopyPickerStatus by remember { mutableStateOf("") }
    var deleteItem by remember { mutableStateOf<FileItemDto?>(null) }
    var imagePreviewItems by remember { mutableStateOf<List<FileItemDto>>(emptyList()) }
    var imagePreviewStartIndex by remember { mutableStateOf<Int?>(null) }
    var audioPlayerItems by remember { mutableStateOf<List<FileItemDto>>(emptyList()) }
    var audioPlayerStartIndex by remember { mutableStateOf<Int?>(null) }
    var videoPlayerItems by remember { mutableStateOf<List<FileItemDto>>(emptyList()) }
    var videoPlayerStartIndex by remember { mutableStateOf<Int?>(null) }
    var textEditorName by remember { mutableStateOf<String?>(null) }
    var textEditorPath by remember { mutableStateOf<String?>(null) }
    var pdfPreviewName by remember { mutableStateOf<String?>(null) }
    var pdfPreviewPath by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val mainThreadHandler = remember { Handler(Looper.getMainLooper()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scopedOps = remember(filesRepository, context) {
        ScopedFilesOps(filesRepository, context.applicationContext)
    }
    val fileListCache = initialFileListCache
    val thumbnailImageLoader = rememberFileThumbnailImageLoader(filesRepository)

    var currentScope by remember { mutableStateOf<FileScope>(FileScope.User) }
    var workspaces by remember { mutableStateOf<List<WorkspaceListItemDto>>(emptyList()) }
    var loadGeneration by remember { mutableStateOf(0) }

    var overwriteUploadTargetPath by remember { mutableStateOf<String?>(null) }
    var overwriteUploadUri by remember { mutableStateOf<Uri?>(null) }

    var showCreateMenu by remember { mutableStateOf(false) }
    var uploadInProgress by remember { mutableStateOf(false) }
    var uploadFileName by remember { mutableStateOf<String?>(null) }
    var uploadBytesSent by remember { mutableStateOf(0L) }
    var uploadBytesTotal by remember { mutableStateOf(0L) }

    var uploadJob by remember { mutableStateOf<Job?>(null) }
    var uploadCancelRequested by remember { mutableStateOf(false) }

    // PQNAS_INCOMING_DESTINATION_PICKER_V1: pending Android Sharesheet upload destination picker state.
    var showIncomingShareDestinationDialog by remember { mutableStateOf(false) }
    var pendingIncomingShareManifestPath by remember { mutableStateOf<String?>(null) }
    var incomingShareDestinationMode by remember { mutableStateOf("phone_uploads") }
    var incomingSharePhoneUploadsPath by remember { mutableStateOf("") }

    var newFolderDialogOpen by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    var newTextFileDialogOpen by remember { mutableStateOf(false) }
    var newTextFileName by remember { mutableStateOf("") }

    fun normalizeRelPath(rel: String?): String {
        return rel.orEmpty()
            .replace("\\", "/")
            .trim('/')
            .split("/")
            .filter { it.isNotBlank() }
            .joinToString("/")
    }

    // PQNAS_INCOMING_ANDROID_SHARE_V1: incoming Android Sharesheet names are external input.
    fun sanitizeIncomingUploadName(name: String): String {
        val cleaned = name
            .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_")
            .trim()
            .trim('.')

        return cleaned.ifBlank { "shared_file.bin" }.take(180)
    }

    fun allocateIncomingUploadName(
        preferredName: String,
        reservedNames: MutableSet<String>
    ): String {
        val safeName = sanitizeIncomingUploadName(preferredName)
        if (reservedNames.add(safeName)) return safeName

        val dot = safeName.lastIndexOf('.')
        val base = if (dot > 0) safeName.substring(0, dot) else safeName
        val ext = if (dot > 0) safeName.substring(dot) else ""

        for (i in 2..9999) {
            val candidate = "${base}_${i}${ext}"
            if (reservedNames.add(candidate)) return candidate
        }

        return "shared_${System.currentTimeMillis()}.bin".also {
            reservedNames.add(it)
        }
    }

    fun isInternalPqnasFolder(item: FileItemDto): Boolean {
        if (item.type != "dir") return false

        val n = item.name.trim().lowercase(Locale.getDefault())

        return n == ".pqnas_activity" ||
                n == ".pqnas_echostack" ||
                n == ".pqnas-echostack" ||
                n == ".pqnas" ||
                n.startsWith(".pqnas_") ||
                n.startsWith(".pqnas-")
    }

    fun visibleFileItems(source: List<FileItemDto>): List<FileItemDto> =
        source.filterNot { isInternalPqnasFolder(it) }

    fun itemFullPath(item: FileItemDto): String {
        return normalizeRelPath(buildItemPath(currentPath, item.name))
    }

    fun favoriteKey(type: String, path: String): String {
        val t = if (type == "dir") "dir" else "file"
        return "$t:${normalizeRelPath(path)}"
    }

    fun shareKey(type: String, path: String): String {
        val t = if (type == "dir") "dir" else "file"
        return "$t:${normalizeRelPath(path)}"
    }

    fun fullShareUrl(url: String?): String {
        if (url.isNullOrBlank()) return ""

        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url
        }

        val base = filesRepository.baseUrlForDisplay().trim().trimEnd('/')
        val rel = if (url.startsWith("/")) url else "/$url"
        return "$base$rel"
    }

    fun load(path: String?) {
        loadGeneration += 1
        val requestGeneration = loadGeneration
        val scopeSnapshot = currentScope

        val cacheNamespace = filesRepository.baseUrlForDisplay()
        val cached = fileListCache.load(
            namespace = cacheNamespace,
            scope = scopeSnapshot,
            path = path
        )

        if (cached != null) {
            currentPath = cached.path
            val cachedVisibleItems = visibleFileItems(cached.items)

            items = if (favoritesOnly) {
                cachedVisibleItems.filter { it.isFavorite }
            } else {
                cachedVisibleItems
            }
            listLoading = false
            startupEmptyStateGrace = false
            status = context.getString(R.string.files_cached_refreshing)
        } else {
            listLoading = true
            status = context.getString(R.string.files_loading)
        }

        commentedPaths = emptySet()

        scope.launch {
            try {
                val resp = scopedOps.list(scopeSnapshot, path)

                if (requestGeneration != loadGeneration) return@launch

                val baseItems = visibleFileItems(resp.items)
                    .sortedWith(
                        compareBy<FileItemDto> { it.type != "dir" }
                            .thenBy { it.name.lowercase(Locale.getDefault()) }
                    )

                currentPath = if (resp.path.isBlank()) null else resp.path

                fileListCache.save(
                    namespace = cacheNamespace,
                    scope = scopeSnapshot,
                    path = currentPath,
                    items = baseItems
                )

                if (!favoritesOnly) {
                    items = baseItems
                    status = "OK"
                } else {
                    items = emptyList()
                    status = context.getString(R.string.files_loading_favorites)
                }

                listLoading = false

                val favsDeferred = async {
                    runCatching { filesRepository.getFavorites() }.getOrNull()
                }

                val sharesDeferred = async {
                    runCatching { scopedOps.getShares(scopeSnapshot) }.getOrNull()
                }

                val locksDeferred = async {
                    val lockPaths = baseItems.map { item ->
                        normalizeRelPath(buildItemPath(resp.path.ifBlank { null }, item.name))
                    }

                    runCatching {
                        filesRepository.getFileLockStatusBatch(scopeSnapshot, lockPaths)
                    }.getOrNull()
                }

                val notesDeferred = async {
                    val notePaths = baseItems.map { item ->
                        normalizeRelPath(buildItemPath(resp.path.ifBlank { null }, item.name))
                    }

                    runCatching {
                        scopedOps.resolveFileNotes(scopeSnapshot, notePaths)
                    }.getOrNull()
                }

                val storageDeferred = async {
                    runCatching { filesRepository.getMyStorage() }
                }

                val favs = favsDeferred.await()
                val shares = sharesDeferred.await()
                val lockResp = locksDeferred.await()
                val notesResp = notesDeferred.await()

                if (requestGeneration != loadGeneration) return@launch

                commentedPaths = notesResp
                    ?.notes
                    ?.filterValues { note ->
                        note.has_description || note.description.isNotBlank()
                    }
                    ?.keys
                    ?.map { normalizeRelPath(it) }
                    ?.toSet()
                    ?: emptySet()

                val favoriteKeys = favs?.items?.map {
                    favoriteKey(it.type, it.path)
                }?.toSet() ?: emptySet()

                val shareKeys = shares?.shares?.map {
                    shareKey(it.type, it.path)
                }?.toSet() ?: emptySet()
                val lockMap = lockResp?.locks ?: emptyMap()
                val mergedItems = baseItems.map { item ->
                    val fullItemPath = normalizeRelPath(
                        buildItemPath(resp.path.ifBlank { null }, item.name)
                    )
                    val lock = lockMap[fullItemPath]

                    item.copy(
                        isFavorite = favoriteKeys.contains(
                            favoriteKey(item.type, fullItemPath)
                        ),
                        isShared = shareKeys.contains(
                            shareKey(item.type, fullItemPath)
                        ),
                        is_locked = lock != null,
                        locked = lock != null,
                        lock_note = lock?.note,
                        locked_by_fp = lock?.locked_by_fp_short,
                        locked_by_display = lock?.locked_by_label ?: lock?.locked_by_fp_short,
                        lock_expires_at_epoch = lock?.expires_at_epoch
                    )
                }

                items = if (favoritesOnly) {
                    mergedItems.filter { it.isFavorite }
                } else {
                    mergedItems
                }

                fileListCache.save(
                    namespace = cacheNamespace,
                    scope = scopeSnapshot,
                    path = currentPath,
                    items = mergedItems
                )

                status = "OK"

                val storageResult = storageDeferred.await()

                if (requestGeneration != loadGeneration) return@launch

                storageResult.fold(
                    onSuccess = {
                        myStorage = it
                        storageStatus = ""
                    },
                    onFailure = { e ->
                        myStorage = null
                        storageStatus = friendlyHttpMessage("Storage", e)
                    }
                )
            } catch (e: Exception) {
                if (requestGeneration != loadGeneration) return@launch
                listLoading = false
                status = friendlyHttpMessage("Load", e)
            }
        }
    }
    fun refreshCurrent() {
        load(currentPath)
    }
    fun switchToUserScope() {
        currentScope = FileScope.User
        currentPath = null
        load(null)
    }

    fun switchToWorkspaceScope(ws: WorkspaceListItemDto) {
        currentScope = FileScope.Workspace(
            workspaceId = ws.workspace_id,
            workspaceName = ws.name,
            workspaceRole = ws.role
        )
        currentPath = null
        load(null)
    }
    fun refreshWorkspaces() {
        scope.launch {
            try {
                val resp = filesRepository.listWorkspaces()
                workspaces = if (resp.ok) resp.workspaces else emptyList()

                val activeWorkspaceId = (currentScope as? FileScope.Workspace)?.workspaceId
                if (activeWorkspaceId != null) {
                    val stillExists = workspaces.any { it.workspace_id == activeWorkspaceId }
                    if (!stillExists) {
                        currentScope = FileScope.User
                        currentPath = null
                    }
                }
            } catch (_: Exception) {
                workspaces = emptyList()
            }
        }
    }

    fun clearUploadProgressState() {
        uploadInProgress = false
        uploadFileName = null
        uploadBytesSent = 0L
        uploadBytesTotal = 0L
        uploadCancelRequested = false
        uploadJob = null
    }

    fun openShareDialog(item: FileItemDto) {
        shareDialogItem = item
        shareDialogUrl = ""
        shareDialogStatus = ""
        shareDialogExistingToken = null
        shareDialogExpiry = defaultShareExpiryOption()

        scope.launch {
            try {
                val fullPath = itemFullPath(item)
                val shares = scopedOps.getShares(currentScope)
                val existing = shares.shares.firstOrNull {
                    shareKey(it.type, it.path) == shareKey(item.type, fullPath)
                }

                if (existing != null) {
                    shareDialogUrl = fullShareUrl(existing.url)
                    shareDialogExistingToken = existing.token
                    shareDialogStatus = "Already shared"
                }
            } catch (_: Exception) {
            }
        }
    }

    fun createShareFor(item: FileItemDto, expiresSec: Long?) {
        scope.launch {
            try {
                val fullPath = itemFullPath(item)
                shareDialogStatus = "Creating share..."
                val resp = scopedOps.createShare(currentScope,
                    path = fullPath,
                    type = item.type,
                    expiresSec = expiresSec
                )
                shareDialogUrl = fullShareUrl(resp.url)
                shareDialogExistingToken = resp.token
                shareDialogStatus = "Share link created (${shareExpiryLabel(expiresSec)})"
                load(currentPath)
            } catch (e: Exception) {
                val msg = friendlyHttpMessage("Share", e)
                shareDialogStatus = msg
                status = msg
            }
        }
    }

    fun revokeShareForCurrentDialog() {
        val token = shareDialogExistingToken ?: return
        scope.launch {
            try {
                shareDialogStatus = "Revoking share..."
                filesRepository.revokeShare(token)
                shareDialogUrl = ""
                shareDialogExistingToken = null
                shareDialogStatus = "Share revoked"
                load(currentPath)
            } catch (e: Exception) {
                val msg = friendlyHttpMessage("Revoke share", e)
                shareDialogStatus = msg
                status = msg
            }
        }
    }

    fun toggleFavorite(item: FileItemDto) {
        scope.launch {
            try {
                val fullPath = itemFullPath(item)
                if (item.isFavorite) {
                    filesRepository.removeFavorite(fullPath, item.type)
                    status = context.getString(R.string.files_removed_from_favorites, item.name)
                    snackbarHostState.showSnackbar(context.getString(R.string.files_removed_from_favorites, item.name))
                } else {
                    filesRepository.addFavorite(fullPath, item.type)
                    status = context.getString(R.string.files_added_to_favorites, item.name)
                    snackbarHostState.showSnackbar(context.getString(R.string.files_added_to_favorites, item.name))
                }
                load(currentPath)
            } catch (e: Exception) {
                val msg = friendlyHttpMessage("Favorites", e)
                status = msg
                snackbarHostState.showSnackbar(msg)
            }
        }
    }

    fun openImagePreview(item: FileItemDto) {
        if (item.type != "file") return
        if (!isProbablyImageFile(item.name)) return

        val visibleImages = items.filter { it.type == "file" && isProbablyImageFile(it.name) }
        val idx = visibleImages.indexOfFirst { it.name == item.name }
        if (idx < 0) return

        imagePreviewItems = visibleImages
        imagePreviewStartIndex = idx
    }
    fun openAudioPlayer(item: FileItemDto) {
        if (item.type != "file") return
        if (!isProbablyAudioFile(item.name)) return

        val visibleAudioFiles = items.filter { it.type == "file" && isProbablyAudioFile(it.name) }
        val idx = visibleAudioFiles.indexOfFirst { it.name == item.name }
        if (idx < 0) return

        audioPlayerItems = visibleAudioFiles
        audioPlayerStartIndex = idx
    }

    fun openVideoPlayer(item: FileItemDto) {
        if (item.type != "file") return
        if (!isProbablyVideoFile(item.name)) return

        val visibleVideoFiles = items.filter { it.type == "file" && isProbablyVideoFile(it.name) }
        val idx = visibleVideoFiles.indexOfFirst { it.name == item.name }
        if (idx < 0) return

        videoPlayerItems = visibleVideoFiles
        videoPlayerStartIndex = idx
    }

    fun openTextEditor(item: FileItemDto) {
        if (item.type != "file") return
        if (!isProbablyTextFile(item.name)) return

        textEditorPath = buildItemPath(currentPath, item.name)
        textEditorName = item.name
    }

    fun openPdfPreview(item: FileItemDto) {
        if (item.type != "file") return
        if (!isProbablyPdfFile(item.name)) return

        pdfPreviewPath = buildItemPath(currentPath, item.name)
        pdfPreviewName = item.name
    }


    fun parentPath(path: String?): String? {
        if (path.isNullOrBlank()) return null
        val parts = path.split("/").filter { it.isNotBlank() }
        if (parts.isEmpty()) return null
        return parts.dropLast(1).joinToString("/").ifBlank { null }
    }

    val shouldHandleFileBack =
        showCreateMenu ||
        shareDialogItem != null ||
        showSettingsSheet ||
        showAppsSheet ||
        showSharesManager ||
        showAboutDialog ||
        showDropZoneSheet ||
        dropZoneHistoryOpen ||
        showEchoStackScreen ||
        showCircleStackScreen ||
        showWorkspaceMessagesSheet ||
        showWorkspaceUrlLinkDialog ||
        showIncomingShareDestinationDialog ||
        pendingIncomingShareManifestPath != null ||
        newFolderDialogOpen ||
        newTextFileDialogOpen ||
        infoItem != null ||
        versionsItem != null ||
        pendingDownloadItem != null ||
        renameItem != null ||
        pendingUploadUri != null ||
        overwriteUploadTargetPath != null ||
        overwriteUploadUri != null ||
        moveCopyItem != null ||
        deleteItem != null ||
        imagePreviewStartIndex != null ||
        audioPlayerStartIndex != null ||
        videoPlayerStartIndex != null ||
        textEditorName != null ||
        textEditorPath != null ||
        pdfPreviewName != null ||
        pdfPreviewPath != null ||
        favoritesOnly ||
        currentPath != null ||
        currentScope != FileScope.User

    // PQNAS_ANDROID_FILE_BACK_V1:
    // Consume Android Back only while there is an in-app step to reverse.
    // At the File Manager user-root with no dialogs/sheets/previews open this handler is
    // disabled, so Android can close the app normally.
    BackHandler(enabled = shouldHandleFileBack) {
        when {
            showCreateMenu -> showCreateMenu = false

            showWorkspaceMessagesSheet -> showWorkspaceMessagesSheet = false
            showWorkspaceUrlLinkDialog -> showWorkspaceUrlLinkDialog = false

            showSettingsSheet -> showSettingsSheet = false
            showAppsSheet -> showAppsSheet = false
            showSharesManager -> showSharesManager = false
            showAboutDialog -> showAboutDialog = false

            showDropZoneSheet -> showDropZoneSheet = false
            dropZoneHistoryOpen -> dropZoneHistoryOpen = false

            showEchoStackScreen -> showEchoStackScreen = false
            showCircleStackScreen -> showCircleStackScreen = false

            showIncomingShareDestinationDialog || pendingIncomingShareManifestPath != null -> {
                showIncomingShareDestinationDialog = false
                pendingIncomingShareManifestPath = null
            }

            newFolderDialogOpen -> {
                newFolderDialogOpen = false
                newFolderName = ""
            }

            newTextFileDialogOpen -> {
                newTextFileDialogOpen = false
                newTextFileName = ""
            }

            shareDialogItem != null -> {
                shareDialogItem = null
                shareDialogUrl = ""
                shareDialogStatus = ""
                shareDialogExistingToken = null
            }

            infoItem != null -> {
                infoItem = null
                infoNoteText = ""
                infoNoteOriginalText = ""
                infoNoteStatus = ""
                infoNoteLoading = false
                infoNoteSaving = false
            }

            versionsItem != null -> versionsItem = null
            pendingDownloadItem != null -> pendingDownloadItem = null

            renameItem != null -> {
                renameItem = null
                renameText = ""
            }

            pendingUploadUri != null -> {
                pendingUploadUri = null
                pendingUploadName = null
            }

            overwriteUploadTargetPath != null || overwriteUploadUri != null -> {
                overwriteUploadTargetPath = null
                overwriteUploadUri = null
            }

            moveCopyItem != null -> {
                moveCopyItem = null
                moveCopyDestination = ""
                moveCopyPickerPath = null
                moveCopyPickerFolders = emptyList()
                moveCopyPickerStatus = ""
            }

            deleteItem != null -> deleteItem = null

            imagePreviewStartIndex != null -> {
                imagePreviewStartIndex = null
                imagePreviewItems = emptyList()
            }

            audioPlayerStartIndex != null -> {
                audioPlayerStartIndex = null
                audioPlayerItems = emptyList()
            }

            videoPlayerStartIndex != null -> {
                videoPlayerStartIndex = null
                videoPlayerItems = emptyList()
            }

            textEditorName != null || textEditorPath != null -> {
                textEditorName = null
                textEditorPath = null
            }

            favoritesOnly -> {
                favoritesOnly = false
                load(currentPath)
            }

            currentPath != null -> {
                load(parentPath(currentPath))
            }

            currentScope != FileScope.User -> {
                switchToUserScope()
            }
        }
    }

    fun openInfoDialog(item: FileItemDto) {
        infoItem = item
        infoNoteText = ""
        infoNoteOriginalText = ""
        infoNoteStatus = ""
        infoNoteLoading = true

        val path = itemFullPath(item)
        val scopeSnapshot = currentScope

        scope.launch {
            try {
                val resp = scopedOps.getFileNote(scopeSnapshot, path)
                val desc = resp.note?.description.orEmpty()
                infoNoteText = desc
                infoNoteOriginalText = desc
                infoNoteStatus = if (desc.isBlank()) context.getString(R.string.info_no_comment_yet) else ""
            } catch (e: Exception) {
                infoNoteStatus = friendlyHttpMessage("Load comment", e)
            } finally {
                infoNoteLoading = false
            }
        }
    }

    fun closeInfoDialog() {
        infoItem = null
        infoNoteText = ""
        infoNoteOriginalText = ""
        infoNoteStatus = ""
        infoNoteLoading = false
        infoNoteSaving = false
    }

    fun saveInfoComment(item: FileItemDto, description: String) {
        if (!scopedOps.canWrite(currentScope)) {
            val msg = context.getString(R.string.move_copy_no_write_access)
            infoNoteStatus = msg
            status = msg
            scope.launch { snackbarHostState.showSnackbar(msg) }
            return
        }

        val path = itemFullPath(item)
        val scopeSnapshot = currentScope
        val cleanDescription = description.trim()

        scope.launch {
            try {
                infoNoteSaving = true
                infoNoteStatus = context.getString(R.string.info_saving_comment)

                scopedOps.saveFileNote(
                    scope = scopeSnapshot,
                    path = path,
                    itemKind = item.type,
                    description = cleanDescription
                )

                infoNoteText = cleanDescription
                infoNoteOriginalText = cleanDescription
                infoNoteStatus = if (cleanDescription.isBlank()) {
                    context.getString(R.string.info_comment_cleared)
                } else {
                    context.getString(R.string.info_comment_saved)
                }

                status = "OK"
                snackbarHostState.showSnackbar(infoNoteStatus)
                load(currentPath)
            } catch (e: Exception) {
                val msg = friendlyHttpMessage("Save comment", e)
                infoNoteStatus = msg
                status = msg
                snackbarHostState.showSnackbar(msg)
            } finally {
                infoNoteSaving = false
            }
        }
    }

    fun createFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            status = context.getString(R.string.create_folder_name_empty)
            return
        }
        if (trimmed.contains("/")) {
            status = context.getString(R.string.create_folder_name_slash)
            return
        }

        scope.launch {
            try {
                val path = buildItemPath(currentPath, trimmed)
                scopedOps.mkdir(currentScope, path)
                newFolderDialogOpen = false
                newFolderName = ""
                status = "OK"
                snackbarHostState.showSnackbar(context.getString(R.string.create_folder_success, trimmed))
                load(currentPath)
            } catch (e: Exception) {
                val msg = friendlyHttpMessage("Create folder", e)
                status = msg
                snackbarHostState.showSnackbar(msg)
            }
        }
    }

    fun createTextFile(name: String) {
        var trimmed = name.trim()
        if (trimmed.isBlank()) {
            status = context.getString(R.string.create_file_name_empty)
            return
        }
        if (trimmed.contains("/")) {
            status = context.getString(R.string.create_file_name_slash)
            return
        }
        if (!trimmed.contains(".")) {
            trimmed += ".txt"
        }

        scope.launch {
            try {
                val path = buildItemPath(currentPath, trimmed)
                val emptyBody = ByteArray(0).toRequestBody(null)

                scopedOps.upload(
                    scope = currentScope,
                    path = path,
                    body = emptyBody,
                    overwrite = false
                )

                newTextFileDialogOpen = false
                newTextFileName = ""
                status = "OK"
                snackbarHostState.showSnackbar(context.getString(R.string.create_file_success, trimmed))
                load(currentPath)
            } catch (e: Exception) {
                val msg = friendlyHttpMessage("Create text file", e)
                status = msg
                snackbarHostState.showSnackbar(msg)
            }
        }
    }

    fun loadMoveCopyPicker(path: String?) {
        val cleanPath = normalizeRelPath(path)
        val pathArg = cleanPath.ifBlank { null }

        moveCopyPickerPath = pathArg
        moveCopyDestination = cleanPath
        moveCopyPickerLoading = true
        moveCopyPickerStatus = ""

        scope.launch {
            try {
                val resp = scopedOps.list(currentScope, pathArg)

                moveCopyPickerFolders = visibleFileItems(resp.items)
                    .filter { it.type == "dir" }
                    .sortedBy { it.name.lowercase(Locale.getDefault()) }

                moveCopyPickerPath = if (resp.path.isBlank()) null else normalizeRelPath(resp.path)
                moveCopyDestination = normalizeRelPath(moveCopyPickerPath)
            } catch (e: Exception) {
                moveCopyPickerFolders = emptyList()
                moveCopyPickerStatus = friendlyHttpMessage("Folders", e)
            } finally {
                moveCopyPickerLoading = false
            }
        }
    }

    fun openMoveCopyDialog(mode: String, item: FileItemDto) {
        if (!scopedOps.canWrite(currentScope)) {
            val msg = "You do not have write access here."
            status = msg
            scope.launch { snackbarHostState.showSnackbar(msg) }
            return
        }

        if (mode == "Move" && item.isLocked) {
            val msg = context.getString(R.string.move_locked_message, item.name)
            status = msg
            scope.launch { snackbarHostState.showSnackbar(msg) }
            return
        }

        moveCopyMode = mode
        moveCopyItem = item
        moveCopyPickerFolders = emptyList()
        moveCopyPickerStatus = ""
        loadMoveCopyPicker(currentPath)
    }

    fun runMoveCopy(item: FileItemDto, mode: String, destinationDirRaw: String) {
        val destinationDir = normalizeRelPath(destinationDirRaw)

        val fromPath = itemFullPath(item)
        val toPath = normalizeRelPath(
            buildItemPath(
                if (destinationDir.isBlank()) null else destinationDir,
                item.name
            )
        )

        if (toPath.isBlank()) {
            val msg = context.getString(R.string.move_copy_destination_empty)
            status = msg
            scope.launch { snackbarHostState.showSnackbar(msg) }
            return
        }

        if (mode == "Move" && fromPath == toPath) {
            moveCopyItem = null
            moveCopyDestination = ""
            status = context.getString(R.string.move_cancelled_same_destination)
            return
        }

        scope.launch {
            try {
                status = if (mode == "Copy") context.getString(R.string.copying_item, item.name) else context.getString(R.string.moving_item, item.name)

                if (mode == "Copy") {
                    scopedOps.copy(currentScope, fromPath, toPath)
                } else {
                    scopedOps.move(currentScope, fromPath, toPath)
                }

                moveCopyItem = null
                moveCopyDestination = ""
                status = "OK"
                snackbarHostState.showSnackbar(
                    if (mode == "Copy") context.getString(R.string.copied_item, item.name) else context.getString(R.string.moved_item, item.name)
                )
                load(currentPath)
            } catch (e: Exception) {
                val msg = friendlyHttpMessage(mode, e)
                status = msg
                snackbarHostState.showSnackbar(msg)
            }
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        val item = pendingDownloadItem
        if (uri == null || item == null) {
            pendingDownloadItem = null
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            try {
                status = context.getString(R.string.files_downloading_item, item.name)
                val fullPath = buildItemPath(currentPath, item.name)
                val body = scopedOps.download(currentScope, fullPath)

                withContext(Dispatchers.IO) {
                    body.use { responseBody ->
                        val output = context.contentResolver.openOutputStream(uri)
                            ?: throw IllegalStateException("Could not open destination file.")

                        output.use { out ->
                            responseBody.byteStream().use { input ->
                                input.copyTo(out)
                            }
                            out.flush()
                        }
                    }
                }

                status = "OK"
                snackbarHostState.showSnackbar(context.getString(R.string.files_saved_to_download, item.name))
            } catch (e: Exception) {
                val msg = friendlyHttpMessage("Download", e)
                status = msg
                snackbarHostState.showSnackbar(msg)
            } finally {
                pendingDownloadItem = null
            }
        }
    }

    fun uploadUri(uri: Uri, overwrite: Boolean) {
        var fileName: String? = null
        var lastProgressUiUpdateAtMs = 0L
        var lastProgressUiBytes = -1L
        var stagedFile: File? = null

        uploadJob = scope.launch {
            try {
                fileName = queryDisplayName(context, uri)?.trim()
                if (fileName.isNullOrBlank()) {
                    val msg = "Upload failed: could not determine file name."
                    status = msg
                    snackbarHostState.showSnackbar(msg)
                    return@launch
                }

                val safeFileName = fileName!!
                val targetPath = buildItemPath(currentPath, safeFileName)

                val existingItem = items.firstOrNull { it.name == safeFileName }
                if (!overwrite && existingItem != null) {
                    overwriteUploadTargetPath = targetPath
                    overwriteUploadUri = uri
                    pendingUploadUri = uri
                    pendingUploadName = safeFileName
                    status = context.getString(R.string.files_file_already_exists, safeFileName)
                    return@launch
                }

                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

                // Large Android document/content URIs may take a while to stage into
                // our cache before chunked upload can begin. Show visible feedback
                // immediately so the app does not feel frozen.
                uploadInProgress = true
                uploadCancelRequested = false
                uploadFileName = safeFileName
                uploadBytesSent = 0L
                uploadBytesTotal = 0L
                status = context.getString(R.string.upload_preparing_named, safeFileName)

                stagedFile = withContext(Dispatchers.IO) {
                    stageUriToTempFile(
                        context = context,
                        uri = uri,
                        fileNameHint = safeFileName
                    )
                }

                val size = stagedFile!!.length()
                uploadBytesTotal = size

                val onUploadProgress: (Long, Long) -> Unit = { sent, total ->
                    val nowMs = System.currentTimeMillis()
                    val bytesDelta = sent - lastProgressUiBytes
                    val shouldUpdate =
                        sent == total ||
                                lastProgressUiBytes < 0L ||
                                bytesDelta >= 256 * 1024L ||
                                (nowMs - lastProgressUiUpdateAtMs) >= 100L

                    if (shouldUpdate) {
                        lastProgressUiBytes = sent
                        lastProgressUiUpdateAtMs = nowMs
                        mainThreadHandler.post {
                            uploadBytesSent = sent
                            uploadBytesTotal = total
                        }
                    }
                }

                status = context.getString(R.string.upload_uploading_named, safeFileName)

                scopedOps.uploadTempFile(
                    scope = currentScope,
                    path = targetPath,
                    file = stagedFile!!,
                    mimeType = mimeType,
                    overwrite = overwrite,
                    onProgress = onUploadProgress,
                    isCancelled = { uploadCancelRequested }
                )

                overwriteUploadTargetPath = null
                overwriteUploadUri = null
                pendingUploadUri = null
                pendingUploadName = null

                status = "OK"
                snackbarHostState.showSnackbar(
                    if (overwrite) {
                        context.getString(R.string.upload_replaced_item, safeFileName)
                    } else {
                        context.getString(R.string.upload_uploaded_item, safeFileName)
                    }
                )
                load(currentPath)
            } catch (e: CancellationException) {
                overwriteUploadTargetPath = null
                overwriteUploadUri = null
                pendingUploadUri = null
                pendingUploadName = null

                status = context.getString(R.string.upload_cancelled_status)
                snackbarHostState.showSnackbar(context.getString(R.string.upload_cancelled_snackbar))
            } catch (e: Exception) {
                val http = (e as? HttpException)?.code()
                val msgText = e.message.orEmpty().lowercase(Locale.getDefault())
                val looksLikeEarlyConflictTransportError =
                    msgText.contains("unexpected end of stream") ||
                            msgText.contains("end of stream") ||
                            msgText.contains("unexpected eof") ||
                            msgText.contains("stream was reset") ||
                            msgText.contains("socket closed")

                val existingItemConflict =
                    !overwrite &&
                            !fileName.isNullOrBlank() &&
                            items.any { it.name == fileName }

                if (!overwrite &&
                    !fileName.isNullOrBlank() &&
                    (http == 409 || (looksLikeEarlyConflictTransportError && existingItemConflict))
                ) {
                    overwriteUploadTargetPath = buildItemPath(currentPath, fileName!!)
                    overwriteUploadUri = uri
                    pendingUploadUri = uri
                    pendingUploadName = fileName
                    status = context.getString(R.string.files_file_already_exists, fileName)
                } else {
                    val msg = friendlyHttpMessage("Upload", e)
                    status = msg
                    snackbarHostState.showSnackbar(msg)
                }
            } finally {
                stagedFile?.delete()
                stagedFile = null
                clearUploadProgressState()
            }
        }
    }

    fun uploadUrisSequentially(uris: List<Uri>) {
        val selectedUris = uris
            .filter { it.toString().isNotBlank() }
            .distinctBy { it.toString() }

        if (selectedUris.isEmpty()) {
            status = context.getString(R.string.files_no_files_selected)
            return
        }

        if (uploadInProgress) {
            status = context.getString(R.string.upload_another_running)
            return
        }

        val pathSnapshot = currentPath
        val scopeSnapshot = currentScope
        val existingNames = items.map { it.name }.toMutableSet()

        uploadJob = scope.launch {
            var uploadedCount = 0
            var skippedCount = 0
            var failedCount = 0

            try {
                uploadInProgress = true
                uploadCancelRequested = false
                uploadBytesSent = 0L
                uploadBytesTotal = 0L

                selectedUris.forEachIndexed { index, uri ->
                    if (uploadCancelRequested) {
                        throw CancellationException("User cancelled upload")
                    }

                    var stagedFile: File? = null
                    var safeFileName = ""

                    try {
                        val displayName = queryDisplayName(context, uri)?.trim()
                        if (displayName.isNullOrBlank()) {
                            failedCount += 1
                            return@forEachIndexed
                        }

                        safeFileName = displayName
                        val targetPath = buildItemPath(pathSnapshot, safeFileName)

                        if (existingNames.contains(safeFileName)) {
                            skippedCount += 1
                            return@forEachIndexed
                        }

                        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

                        uploadFileName = "$safeFileName (${index + 1}/${selectedUris.size})"
                        uploadBytesSent = 0L
                        uploadBytesTotal = 0L
                        status = context.getString(R.string.upload_multi_preparing, index + 1, selectedUris.size, safeFileName)

                        stagedFile = withContext(Dispatchers.IO) {
                            stageUriToTempFile(
                                context = context,
                                uri = uri,
                                fileNameHint = safeFileName
                            )
                        }

                        val size = stagedFile!!.length()
                        uploadBytesTotal = size

                        var lastProgressUiUpdateAtMs = 0L
                        var lastProgressUiBytes = -1L

                        val onUploadProgress: (Long, Long) -> Unit = { sent, total ->
                            val nowMs = System.currentTimeMillis()
                            val bytesDelta = sent - lastProgressUiBytes
                            val shouldUpdate =
                                sent == total ||
                                        lastProgressUiBytes < 0L ||
                                        bytesDelta >= 256 * 1024L ||
                                        (nowMs - lastProgressUiUpdateAtMs) >= 100L

                            if (shouldUpdate) {
                                lastProgressUiBytes = sent
                                lastProgressUiUpdateAtMs = nowMs
                                mainThreadHandler.post {
                                    uploadBytesSent = sent
                                    uploadBytesTotal = total
                                }
                            }
                        }

                        status = context.getString(R.string.upload_multi_uploading, index + 1, selectedUris.size, safeFileName)

                        scopedOps.uploadTempFile(
                            scope = scopeSnapshot,
                            path = targetPath,
                            file = stagedFile!!,
                            mimeType = mimeType,
                            overwrite = false,
                            onProgress = onUploadProgress,
                            isCancelled = { uploadCancelRequested }
                        )

                        uploadedCount += 1
                        existingNames.add(safeFileName)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failedCount += 1
                    } finally {
                        stagedFile?.delete()
                    }
                }

                status = "OK"

                val parts = mutableListOf<String>()
                if (uploadedCount > 0) parts.add(context.getString(R.string.upload_multi_part_uploaded, uploadedCount))
                if (skippedCount > 0) parts.add(context.getString(R.string.upload_multi_part_skipped, skippedCount))
                if (failedCount > 0) parts.add(context.getString(R.string.upload_multi_part_failed, failedCount))

                val summary = if (parts.isEmpty()) {
                    context.getString(R.string.upload_multi_none)
                } else {
                    context.getString(R.string.upload_multi_complete, parts.joinToString(", "))
                }

                snackbarHostState.showSnackbar(summary)

                if (uploadedCount > 0) {
                    load(pathSnapshot)
                }
            } catch (e: CancellationException) {
                status = context.getString(R.string.upload_cancelled_status)
                snackbarHostState.showSnackbar(context.getString(R.string.upload_cancelled_snackbar))
            } finally {
                clearUploadProgressState()
            }
        }
    }

    // PQNAS_INCOMING_DESTINATION_PICKER_V1: destination helpers for Android Sharesheet uploads.
    fun defaultIncomingSharePhoneUploadsPath(): String {
        val month = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return "Phone Uploads/$month/$stamp"
    }

    fun incomingShareDestinationLabel(scope: FileScope, path: String?): String {
        val cleanPath = normalizeRelPath(path)
        val suffix = if (cleanPath.isBlank()) "/" else "/$cleanPath"

        return when (scope) {
            FileScope.User -> "${context.getString(R.string.file_scope_my_files)} $suffix"
            is FileScope.Workspace -> "${scope.workspaceName.ifBlank { context.getString(R.string.file_scope_workspace_fallback) }} $suffix"
        }
    }

    suspend fun ensureIncomingDestinationFolders(scope: FileScope, destinationPath: String?) {
        val cleanPath = normalizeRelPath(destinationPath)
        if (cleanPath.isBlank()) return
        if (!scopedOps.canWrite(scope)) return

        var partial = ""

        cleanPath
            .split("/")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { segment ->
                partial = if (partial.isBlank()) segment else "$partial/$segment"

                // Folder may already exist. That is fine; upload will still validate the final path.
                runCatching {
                    scopedOps.mkdir(scope, partial)
                }
            }
    }

    // PQNAS_INCOMING_ANDROID_SHARE_V1: upload files staged by IncomingShareActivity.
    fun uploadStagedIncomingShareManifest(
        manifestPath: String,
        destinationScope: FileScope,
        destinationPath: String?
    ) {
        if (uploadInProgress) {
            status = context.getString(R.string.upload_incoming_another_running)
            scope.launch { snackbarHostState.showSnackbar(status) }
            onIncomingShareConsumed()
            return
        }

        if (!scopedOps.canWrite(destinationScope)) {
            status = context.getString(R.string.move_copy_no_write_access)
            scope.launch { snackbarHostState.showSnackbar(status) }
            onIncomingShareConsumed()
            return
        }

        val scopeSnapshot = destinationScope
        val pathSnapshot = normalizeRelPath(destinationPath).ifBlank { null }
        val manifestFile = File(manifestPath)
        val stagedDir = manifestFile.parentFile

        onIncomingShareConsumed()

        uploadJob = scope.launch {
            var uploadedCount = 0

            try {
                ensureIncomingDestinationFolders(scopeSnapshot, pathSnapshot)

                if (!manifestFile.isFile) {
                    throw IllegalStateException("Incoming share manifest is missing.")
                }

                val manifestJson = JSONObject(
                    withContext(Dispatchers.IO) {
                        manifestFile.readText(Charsets.UTF_8)
                    }
                )

                val shareItems = manifestJson.getJSONArray("items")
                if (shareItems.length() <= 0) {
                    throw IllegalStateException("Incoming share had no uploadable items.")
                }

                val reservedNames = items
                    .map { it.name }
                    .toMutableSet()

                for (index in 0 until shareItems.length()) {
                    if (uploadCancelRequested) {
                        throw CancellationException("User cancelled incoming share upload")
                    }

                    val entry = shareItems.getJSONObject(index)
                    if (entry.optString("kind") != "file") continue

                    val stagedPath = entry.optString("path")
                    val stagedFile = File(stagedPath)
                    if (!stagedFile.isFile) {
                        throw IllegalStateException("Staged file is missing: $stagedPath")
                    }

                    val preferredName = entry
                        .optString("name")
                        .ifBlank { stagedFile.name }
                        .ifBlank { "shared_file.bin" }

                    val uploadName = allocateIncomingUploadName(
                        preferredName = preferredName,
                        reservedNames = reservedNames
                    )

                    val targetPath = buildItemPath(pathSnapshot, uploadName)
                    val mimeType = entry
                        .optString("mime")
                        .ifBlank { "application/octet-stream" }

                    var lastProgressUiUpdateAtMs = 0L
                    var lastProgressUiBytes = -1L
                    val totalBytes = stagedFile.length()

                    uploadInProgress = true
                    uploadCancelRequested = false
                    uploadFileName = uploadName
                    uploadBytesSent = 0L
                    uploadBytesTotal = totalBytes
                    status = context.getString(R.string.upload_shared_item_progress, index + 1, shareItems.length(), uploadName)

                    val onUploadProgress: (Long, Long) -> Unit = { sent, total ->
                        val nowMs = System.currentTimeMillis()
                        val bytesDelta = sent - lastProgressUiBytes
                        val shouldUpdate =
                            sent == total ||
                                    lastProgressUiBytes < 0L ||
                                    bytesDelta >= 256 * 1024L ||
                                    (nowMs - lastProgressUiUpdateAtMs) >= 100L

                        if (shouldUpdate) {
                            lastProgressUiBytes = sent
                            lastProgressUiUpdateAtMs = nowMs
                            mainThreadHandler.post {
                                uploadBytesSent = sent
                                uploadBytesTotal = total
                            }
                        }
                    }

                    scopedOps.uploadTempFile(
                        scope = scopeSnapshot,
                        path = targetPath,
                        file = stagedFile,
                        mimeType = mimeType,
                        overwrite = false,
                        onProgress = onUploadProgress,
                        isCancelled = { uploadCancelRequested }
                    )

                    uploadedCount += 1
                }

                if (uploadedCount <= 0) {
                    throw IllegalStateException(context.getString(R.string.upload_incoming_no_files))
                }

                status = "OK"
                snackbarHostState.showSnackbar(
                    if (uploadedCount == 1) {
                        context.getString(R.string.upload_shared_one)
                    } else {
                        context.getString(R.string.upload_shared_many, uploadedCount)
                    }
                )
                currentScope = scopeSnapshot
                currentPath = pathSnapshot
                load(pathSnapshot)
            } catch (e: CancellationException) {
                status = context.getString(R.string.upload_incoming_cancelled)
                snackbarHostState.showSnackbar(context.getString(R.string.upload_cancelled_snackbar))
            } catch (e: Exception) {
                val msg = friendlyHttpMessage("Incoming share upload", e)
                status = msg
                snackbarHostState.showSnackbar(msg)
            } finally {
                runCatching { stagedDir?.deleteRecursively() }
                clearUploadProgressState()
            }
        }
    }

    fun hasMediaLocationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_MEDIA_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    val mediaLocationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pendingSingle = pendingMediaLocationUploadUri
        val pendingMultiple = pendingMediaLocationUploadUris

        pendingMediaLocationUploadUri = null
        pendingMediaLocationUploadUris = emptyList()

        if (granted) {
            when {
                pendingSingle != null ->
                    uploadUri(pendingSingle, overwrite = false)

                pendingMultiple.isNotEmpty() ->
                    uploadUrisSequentially(pendingMultiple)
            }
        } else {
            // Fail closed: silently uploading a redacted copy would lose
            // location metadata from a photo backup.
            status = context.getString(R.string.upload_cancelled_snackbar)
            scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.upload_cancelled_snackbar)
                )
            }
        }
    }

    val uploadDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        if (
            requiresOriginalPhotoAccess(context, uri) &&
            !hasMediaLocationPermission()
        ) {
            pendingMediaLocationUploadUri = uri
            pendingMediaLocationUploadUris = emptyList()
            mediaLocationPermissionLauncher.launch(
                Manifest.permission.ACCESS_MEDIA_LOCATION
            )
            return@rememberLauncherForActivityResult
        }

        uploadUri(uri, overwrite = false)
    }

    val uploadMultipleDocumentsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult

        val needsOriginalPhotoAccess = uris.any {
            requiresOriginalPhotoAccess(context, it)
        }

        if (
            needsOriginalPhotoAccess &&
            !hasMediaLocationPermission()
        ) {
            pendingMediaLocationUploadUri = null
            pendingMediaLocationUploadUris = uris
            mediaLocationPermissionLauncher.launch(
                Manifest.permission.ACCESS_MEDIA_LOCATION
            )
            return@rememberLauncherForActivityResult
        }

        uploadUrisSequentially(uris)
    }

    LaunchedEffect(incomingShareNonce, incomingShareManifestPath) {
        val manifestPath = incomingShareManifestPath
        if (incomingShareNonce > 0 && !manifestPath.isNullOrBlank()) {
            // PQNAS_INCOMING_DESTINATION_PICKER_V1: hold the staged share until user chooses destination.
            pendingIncomingShareManifestPath = manifestPath
            incomingSharePhoneUploadsPath = defaultIncomingSharePhoneUploadsPath()
            incomingShareDestinationMode = "phone_uploads"
            showIncomingShareDestinationDialog = true
            onIncomingShareConsumed()
        }
    }

    LaunchedEffect(Unit) {
        delay(1_200L)
        startupEmptyStateGrace = false
    }

    LaunchedEffect(showAppsSheet) {
        if (showAppsSheet) {
            dropZoneAvailable = filesRepository.isServerAppAvailable("dropzone")
            echoStackAvailable = filesRepository.isServerAppAvailable("echostack")
            circleStackAvailable = filesRepository.isServerAppAvailable("circlestack")
            contactsAvailable = onOpenContacts != null && filesRepository.isServerAppAvailable("contacts")
            appsChecked = true
        }
    }

    fun refreshDropZones() {
        scope.launch {
            dropZoneLoading = true
            dropZoneStatus = ""

            runCatching {
                filesRepository.listDropZones()
            }.onSuccess { r ->
                if (r.ok) {
                    dropZones = r.drop_zones
                    if (dropZones.isEmpty()) {
                        dropZoneStatus = "No Drop Zones yet."
                    }
                } else {
                    dropZoneStatus = r.message ?: r.error ?: "Could not load Drop Zones."
                }
            }.onFailure { e ->
                dropZoneStatus = friendlyHttpMessage("Drop Zone", e)
            }

            dropZoneLoading = false
        }
    }

    fun createDropZoneFromSheet() {
        scope.launch{
            dropZoneCreating = true
            dropZoneStatus = ""
            dropZoneLatestUrl = ""

            runCatching {
                filesRepository.createDropZone(
                    name = dropZoneName,
                    destinationPath = dropZoneDestination,
                    password = dropZonePassword,
                    expiresInSeconds = dropZoneExpiresInSeconds,
                    maxFileBytes = parseDropZoneLimitBytes(dropZoneMaxFileBytes),
                    maxTotalBytes = parseDropZoneLimitBytes(dropZoneMaxTotalBytes),
                    duplicatePolicy = dropZoneDuplicatePolicy,
                    branding = DropZoneBrandingDto(
                        company_name = dropZoneBrandingCompanyName,
                        kicker = dropZoneBrandingKicker,
                        title = dropZoneBrandingTitle,
                        description = dropZoneBrandingDescription,
                        button_text = dropZoneBrandingButtonText,
                        footer_text = dropZoneBrandingFooterText,
                        logo_url = dropZoneBrandingLogoUrl,
                        primary_color = dropZoneBrandingPrimaryColor,
                        background_color = dropZoneBrandingBackgroundColor,
                        panel_color = dropZoneBrandingPanelColor,
                        text_color = dropZoneBrandingTextColor,
                        button_text_color = dropZoneBrandingButtonTextColor
                    )
                )
            }.onSuccess { r ->
                if (r.ok) {
                    dropZoneLatestUrl = r.full_url.ifBlank { r.url }
                    dropZoneStatus = "Drop Zone created. Link is ready to copy."
                    refreshDropZones()
                } else {
                    dropZoneStatus = r.message ?: r.error ?: "Could not create Drop Zone."
                }
            }.onFailure { e ->
                dropZoneStatus = friendlyHttpMessage("Create Drop Zone", e)
            }

            dropZoneCreating = false
        }
    }

    fun updateDropZoneFromSheet(
        id: String,
        name: String,
        maxFileBytesText: String,
        maxTotalBytesText: String,
        duplicatePolicy: String,
        branding: DropZoneBrandingDto
    ) {
        scope.launch {
            dropZoneStatus = "Updating Drop Zone..."

            runCatching {
                filesRepository.updateDropZone(
                    id = id,
                    name = name,
                    maxFileBytes = parseDropZoneLimitBytes(maxFileBytesText),
                    maxTotalBytes = parseDropZoneLimitBytes(maxTotalBytesText),
                    duplicatePolicy = duplicatePolicy,
                    branding = branding
                )
            }.onSuccess { r ->
                if (r.ok) {
                    dropZoneStatus = "Drop Zone updated."
                    refreshDropZones()
                } else {
                    dropZoneStatus = r.message ?: r.error ?: "Could not update Drop Zone."
                }
            }.onFailure { e ->
                dropZoneStatus = friendlyHttpMessage("Update Drop Zone", e)
            }
        }
    }

    fun renewDropZoneFromSheet(id: String, expiresInSeconds: Long) {
        scope.launch {
            dropZoneStatus = "Renewing Drop Zone..."
            try {
                val resp = filesRepository.renewDropZone(
                    id = id,
                    expiresInSeconds = expiresInSeconds
                )

                if (resp.ok) {
                    dropZoneStatus = "Drop Zone renewed."
                    refreshDropZones()
                } else {
                    dropZoneStatus = resp.message ?: resp.error ?: "Could not renew Drop Zone."
                }
            } catch (e: Exception) {
                dropZoneStatus = "Could not renew Drop Zone: ${e.message ?: "unknown error"}"
            }
        }
    }

    fun clearDropZoneHistoryFromSheet(id: String) {
        scope.launch {
            dropZoneStatus = "Clearing upload history..."
            try {
                val resp = filesRepository.clearDropZoneHistory(id)

                if (resp.ok) {
                    dropZoneStatus = "Upload history cleared (${resp.deleted_count} entries)."
                    refreshDropZones()
                } else {
                    dropZoneStatus = resp.message ?: resp.error ?: "Could not clear upload history."
                }
            } catch (e: Exception) {
                dropZoneStatus = "Could not clear upload history: ${e.message ?: "unknown error"}"
            }
        }
    }

    fun closeDropZoneHistoryFromSheet() {
        dropZoneHistoryOpen = false
        dropZoneHistoryTitle = ""
        dropZoneHistoryStatus = ""
        dropZoneHistoryUploads = emptyList()
        dropZoneHistoryLoading = false
    }

    fun showDropZoneHistoryFromSheet(id: String, title: String) {
        dropZoneHistoryOpen = true
        dropZoneHistoryTitle = title.ifBlank { "Drop Zone" }
        dropZoneHistoryStatus = ""
        dropZoneHistoryUploads = emptyList()
        dropZoneHistoryLoading = true

        scope.launch {
            try {
                val resp = filesRepository.listDropZoneUploads(id)

                if (resp.ok) {
                    dropZoneHistoryUploads = resp.uploads
                    dropZoneHistoryStatus = if (resp.uploads.isEmpty()) {
                        "No uploads in history."
                    } else {
                        "${resp.uploads.size} upload(s)"
                    }
                } else {
                    dropZoneHistoryStatus = resp.message ?: resp.error ?: "Could not load upload history."
                }
            } catch (e: Exception) {
                dropZoneHistoryStatus = "Could not load upload history: ${e.message ?: "unknown error"}"
            } finally {
                dropZoneHistoryLoading = false
            }
        }
    }

    fun disableDropZoneFromSheet(id: String) {
        scope.launch {
            dropZoneStatus = ""

            runCatching {
                filesRepository.disableDropZone(id, disabled = true)
            }.onSuccess { r ->
                if (r.ok) {
                    dropZoneStatus = "Drop Zone disabled."
                    refreshDropZones()
                } else {
                    dropZoneStatus = r.message ?: r.error ?: "Could not disable Drop Zone."
                }
            }.onFailure { e ->
                dropZoneStatus = friendlyHttpMessage("Disable Drop Zone", e)
            }
        }
    }

    fun copyLatestDropZoneLink() {
        if (dropZoneLatestUrl.isBlank()) return

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(context.getString(R.string.files_drop_zone_link), dropZoneLatestUrl)
        )

        dropZoneStatus = "Drop Zone link copied."
    }
    LaunchedEffect(Unit) {
        refreshWorkspaces()
        load(null)
    }

    if (showCircleStackScreen) {
        val circleStackRepository = remember(filesRepository) {
            CircleStackRepository(filesRepository.createCircleStackApiInternal())
        }
        val circleStackImageLoader = remember(filesRepository, context) {
            coil.ImageLoader.Builder(context)
                .okHttpClient(filesRepository.createAuthedOkHttpClient())
                .build()
        }

        CircleStackScreen(
            repository = circleStackRepository,
            filesRepository = filesRepository,
            baseUrl = filesRepository.baseUrlForDisplay(),
            imageLoader = circleStackImageLoader,
            onBeforeExternalPicker = onBeforeExternalPicker,
            onClose = { showCircleStackScreen = false }
        )
        return
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (!uploadInProgress) showCreateMenu = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.add)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = appTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = { showAppsSheet = true }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_apps_24),
                        contentDescription = stringResource(R.string.apps)
                    )
                }

                IconButton(
                    onClick = { showSettingsSheet = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.settings_and_info)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            if (serverHost.isNotBlank()) {
                // Keep the connected origin visible so runtime branding cannot
                // hide which server the user is actually connected to.
                Text(
                    text = serverHost,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(4.dp))
            }

            Text(
                text = stringResource(R.string.path_label, currentPath ?: "/"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            FilesScopeSection(
                currentScope = currentScope,
                workspaces = workspaces.map { ws ->
                    WorkspaceScopeOption(
                        workspaceId = ws.workspace_id,
                        label = ws.name.ifBlank { ws.workspace_id },
                        role = ws.role
                    )
                },
                onSelectUserScope = {
                    switchToUserScope()
                },
                onSelectWorkspaceScope = { ws ->
                    currentScope = FileScope.Workspace(
                        workspaceId = ws.workspaceId,
                        workspaceName = ws.label,
                        workspaceRole = ws.role
                    )
                    currentPath = null
                    load(null)
                }
            )

            // PQNAS_ANDROID_WORKSPACE_MESSAGES_LINKS_V1: workspace-only quick actions.
            (currentScope as? FileScope.Workspace)?.let { activeWorkspace ->
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { showWorkspaceMessagesSheet = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.messages))
                    }

                    Button(
                        enabled = scopedOps.canWrite(activeWorkspace),
                        onClick = { showWorkspaceUrlLinkDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.save_url))
                    }
                }
            }


            Spacer(Modifier.height(8.dp))

            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    status == "OK" -> MaterialTheme.colorScheme.tertiary
                    status.contains("failed", ignoreCase = true) ||
                            status.contains("denied", ignoreCase = true) ||
                            status.contains("expired", ignoreCase = true) ||
                            status.contains("not found", ignoreCase = true) ||
                            status.contains("cannot", ignoreCase = true) -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            if (uploadInProgress) {
                Spacer(Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = uploadFileName?.let {
                                when {
                                    uploadBytesTotal <= 0L -> stringResource(R.string.upload_preparing_item, it)
                                    uploadBytesSent >= uploadBytesTotal -> stringResource(R.string.upload_finalizing_item, it)
                                    else -> stringResource(R.string.upload_uploading_item, it)
                                }
                            } ?: when {
                                uploadBytesTotal <= 0L -> stringResource(R.string.upload_preparing)
                                uploadBytesSent >= uploadBytesTotal -> stringResource(R.string.upload_finalizing)
                                else -> stringResource(R.string.upload_uploading)
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = {
                                if (uploadBytesTotal <= 0L) 0f
                                else (uploadBytesSent.toFloat() / uploadBytesTotal.toFloat()).coerceIn(0f, 1f)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = if (uploadBytesTotal <= 0L) {
                                stringResource(R.string.upload_gathering_file)
                            } else if (uploadBytesSent >= uploadBytesTotal) {
                                stringResource(R.string.upload_processing_server)
                            } else {
                                "${((uploadBytesSent * 100) / uploadBytesTotal).coerceIn(0, 100)}% • ${
                                    formatBytes(uploadBytesSent)
                                } / ${formatBytes(uploadBytesTotal)}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                enabled = !uploadCancelRequested,
                                onClick = {
                                    uploadCancelRequested = true
                                    status = context.getString(R.string.upload_cancelling_status)
                                    uploadJob?.cancel(CancellationException("User cancelled upload"))
                                }
                            ) {
                                Text(if (uploadCancelRequested) stringResource(R.string.upload_cancelling) else stringResource(R.string.upload_cancel_button))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { load(parentPath(currentPath)) },
                    enabled = currentPath != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.up))
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxSize(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                if (items.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = when {
                                (listLoading || startupEmptyStateGrace) -> stringResource(R.string.file_list_loading_title)
                                favoritesOnly -> stringResource(R.string.file_list_no_favorites_title)
                                else -> stringResource(R.string.file_list_no_files_title)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = when {
                                (listLoading || startupEmptyStateGrace) ->
                                    stringResource(R.string.file_list_contacting_server)
                                favoritesOnly ->
                                    stringResource(R.string.file_list_no_favorites_desc)
                                else ->
                                    stringResource(R.string.file_list_empty_or_failed_desc)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(items) { item ->
                            FileRow(
                                item = item,
                                leadingVisual = {
                                    val fullItemPath = normalizeRelPath(
                                        buildItemPath(currentPath, item.name)
                                    )
                                    val hasComment = commentedPaths.contains(fullItemPath)

                                    Box(
                                        modifier = Modifier.size(42.dp)
                                    ) {
                                        FileLeadingVisual(
                                            filesRepository = filesRepository,
                                            imageLoader = thumbnailImageLoader,
                                            fileScope = currentScope,
                                            currentPath = currentPath,
                                            item = item,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        if (hasComment) {
                                            Text(
                                                text = "✎",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .background(
                                                        color = MaterialTheme.colorScheme.primary,
                                                        shape = MaterialTheme.shapes.small
                                                    )
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                },
                                onOpen = {
                                    if (item.type == "dir") {
                                        val next = listOfNotNull(currentPath, item.name)
                                            .joinToString("/")
                                        load(next)
                                    } else if (isProbablyImageFile(item.name)) {
                                        openImagePreview(item)
                                    } else if (isProbablyAudioFile(item.name)) {
                                        openAudioPlayer(item)
                                    } else if (isProbablyVideoFile(item.name)) {
                                        openVideoPlayer(item)
                                    } else if (isProbablyPdfFile(item.name)) {
                                        openPdfPreview(item)
                                    } else if (isProbablyTextFile(item.name)) {
                                        openTextEditor(item)
                                    }
                                },
                                onToggleFavorite = {
                                    toggleFavorite(item)
                                },
                                onMenuAction = { action, clickedItem ->
                                    when (action) {
                                        "Preview" -> openImagePreview(clickedItem)
                                        "PlayAudio" -> openAudioPlayer(clickedItem)
                                        "PlayVideo" -> openVideoPlayer(clickedItem)
                                        "PreviewPdf" -> openPdfPreview(clickedItem)
                                        "EditText" -> openTextEditor(clickedItem)
                                        "ToggleFavorite" -> toggleFavorite(clickedItem)
                                        "Share" -> openShareDialog(clickedItem)
                                        "Info" -> openInfoDialog(clickedItem)
                                        "Versions" -> versionsItem = clickedItem
                                        "Download" -> {
                                            if (clickedItem.type == "dir") {
                                                status = context.getString(R.string.files_folder_download_not_implemented, clickedItem.name)
                                            } else {
                                                pendingDownloadItem = clickedItem
                                                onBeforeExternalPicker()
                                                createDocumentLauncher.launch(clickedItem.name)
                                            }
                                        }
                                        "Move" -> openMoveCopyDialog("Move", item)
            "Copy" -> openMoveCopyDialog("Copy", item)
            "Rename" -> {
                                            if (clickedItem.isLocked) {
                                                val msg = "${clickedItem.name} is locked. Unlock it before renaming."
                                                status = msg
                                                scope.launch { snackbarHostState.showSnackbar(msg) }
                                            } else {
                                                renameItem = clickedItem
                                                renameText = clickedItem.name
                                            }
                                        }
                                        "Delete" -> {
                                            if (clickedItem.isLocked) {
                                                val msg = "${clickedItem.name} is locked. Unlock it before deleting."
                                                status = msg
                                                scope.launch { snackbarHostState.showSnackbar(msg) }
                                            } else {
                                                deleteItem = clickedItem
                                            }
                                        }
                                        else -> status = context.getString(R.string.files_action_not_implemented, action, clickedItem.name)
                                    }
                                }
                            )

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                            )
                        }

                        item {
                            Spacer(Modifier.height(104.dp))
                        }
                    }
                }
            }
        }
        }

    // PQNAS_REPAIR_INCOMING_DESTINATION_STYLE_SCOPE_V1
    if (showIncomingShareDestinationDialog) {
        // PQNAS_INCOMING_DESTINATION_PICKER_STYLE_V1
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 6.dp,
            onDismissRequest = {
                val manifestPath = pendingIncomingShareManifestPath
                if (!manifestPath.isNullOrBlank()) {
                    runCatching { File(manifestPath).parentFile?.deleteRecursively() }
                }

                pendingIncomingShareManifestPath = null
                showIncomingShareDestinationDialog = false
                onIncomingShareConsumed()
            },
            title = {
                Text(stringResource(R.string.incoming_share_title))
            },
            text = {
                // PQNAS_INCOMING_DESTINATION_PICKER_UNIFIED_STYLE_V1
                val currentDestinationLabel = incomingShareDestinationLabel(currentScope, currentPath)
                val phoneUploadsPath = incomingSharePhoneUploadsPath.ifBlank {
                    defaultIncomingSharePhoneUploadsPath()
                }
                val writableWorkspaces = workspaces.filter { ws ->
                    scopedOps.canWrite(
                        FileScope.Workspace(
                            workspaceId = ws.workspace_id,
                            workspaceName = ws.name,
                            workspaceRole = ws.role
                        )
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.incoming_share_choose_destination),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                    )

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.incoming_share_phone_uploads)) },
                        supportingContent = { Text("/$phoneUploadsPath") },
                        leadingContent = {
                            RadioButton(
                                selected = incomingShareDestinationMode == "phone_uploads",
                                onClick = { incomingShareDestinationMode = "phone_uploads" }
                            )
                        },
                        modifier = Modifier.clickable {
                            incomingShareDestinationMode = "phone_uploads"
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.incoming_share_current_location)) },
                        supportingContent = { Text(currentDestinationLabel) },
                        leadingContent = {
                            RadioButton(
                                selected = incomingShareDestinationMode == "current",
                                onClick = { incomingShareDestinationMode = "current" }
                            )
                        },
                        modifier = Modifier.clickable {
                            incomingShareDestinationMode = "current"
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.incoming_share_my_files_root)) },
                        supportingContent = { Text(stringResource(R.string.incoming_share_upload_root_desc)) },
                        leadingContent = {
                            RadioButton(
                                selected = incomingShareDestinationMode == "my_files_root",
                                onClick = { incomingShareDestinationMode = "my_files_root" }
                            )
                        },
                        modifier = Modifier.clickable {
                            incomingShareDestinationMode = "my_files_root"
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )

                    if (writableWorkspaces.isNotEmpty()) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        )

                        Text(
                            text = stringResource(R.string.incoming_share_workspace_roots),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                        )

                        writableWorkspaces.forEach { ws ->
                            val mode = "workspace:${ws.workspace_id}"
                            ListItem(
                                headlineContent = { Text(ws.name.ifBlank { ws.workspace_id }) },
                                supportingContent = { Text(stringResource(R.string.incoming_share_workspace_root_role, ws.role)) },
                                leadingContent = {
                                    RadioButton(
                                        selected = incomingShareDestinationMode == mode,
                                        onClick = { incomingShareDestinationMode = mode }
                                    )
                                },
                                modifier = Modifier.clickable {
                                    incomingShareDestinationMode = mode
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val manifestPath = pendingIncomingShareManifestPath
                        if (manifestPath.isNullOrBlank()) {
                            status = context.getString(R.string.incoming_share_missing)
                            showIncomingShareDestinationDialog = false
                            return@TextButton
                        }

                        var destinationScope = currentScope
                        var destinationPath: String? = currentPath

                        when {
                            incomingShareDestinationMode == "phone_uploads" -> {
                                destinationScope = FileScope.User
                                destinationPath = incomingSharePhoneUploadsPath.ifBlank {
                                    defaultIncomingSharePhoneUploadsPath()
                                }
                            }

                            incomingShareDestinationMode == "current" -> {
                                destinationScope = currentScope
                                destinationPath = currentPath
                            }

                            incomingShareDestinationMode == "my_files_root" -> {
                                destinationScope = FileScope.User
                                destinationPath = null
                            }

                            incomingShareDestinationMode.startsWith("workspace:") -> {
                                val workspaceId = incomingShareDestinationMode.removePrefix("workspace:")
                                val ws = workspaces.firstOrNull { it.workspace_id == workspaceId }
                                if (ws == null) {
                                    status = context.getString(R.string.incoming_share_workspace_missing)
                                    return@TextButton
                                }

                                destinationScope = FileScope.Workspace(
                                    workspaceId = ws.workspace_id,
                                    workspaceName = ws.name,
                                    workspaceRole = ws.role
                                )
                                destinationPath = null
                            }

                            else -> {
                                status = context.getString(R.string.incoming_share_unknown_destination)
                                return@TextButton
                            }
                        }

                        pendingIncomingShareManifestPath = null
                        showIncomingShareDestinationDialog = false

                        uploadStagedIncomingShareManifest(
                            manifestPath = manifestPath,
                            destinationScope = destinationScope,
                            destinationPath = destinationPath
                        )
                    }
                ) {
                    Text(stringResource(R.string.incoming_share_upload_button))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val manifestPath = pendingIncomingShareManifestPath
                        if (!manifestPath.isNullOrBlank()) {
                            runCatching { File(manifestPath).parentFile?.deleteRecursively() }
                        }

                        pendingIncomingShareManifestPath = null
                        showIncomingShareDestinationDialog = false
                        onIncomingShareConsumed()
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            confirmButton = {
                TextButton(
                    onClick = { showAboutDialog = false }
                ) {
                    Text(stringResource(R.string.close))
                }
            },
            title = {
                Text(aboutAppTitle)
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ConnectedBrandingSummary(
                        appTitle = appTitle,
                        serverHost = serverHost
                    )

                    SettingsAboutSection(
                        appTitle = appTitle
                    )
                }
            }
        )
    }

    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_info_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )


                SettingsStorageSection(
                    storage = myStorage,
                    storageStatus = storageStatus
                )

                if (onOpenAdmin != null) {
                    Button(
                        onClick = {
                            showSettingsSheet = false
                            onOpenAdmin()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.admin_tools))
                    }
                }

                Button(
                    onClick = {
                        showSettingsSheet = false
                        showAppSettingsDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.app_settings))
                }

                Button(
                    onClick = { showAboutDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(aboutAppTitle)
                }

                Button(
                    onClick = {
                        favoritesOnly = !favoritesOnly
                        showSettingsSheet = false
                        load(currentPath)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(if (favoritesOnly) R.string.show_all_items else R.string.show_favorites_only))
                }

                Button(
                    onClick = {
                        showSettingsSheet = false
                        refreshCurrent()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.refresh))
                }
                if (onLogout != null) {
                    Button(
                        onClick = {
                            showSettingsSheet = false
                            onLogout()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text(stringResource(R.string.logout))
                    }
                }
            }

        }
    }

    if (showAppSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showAppSettingsDialog = false },
            title = {
                Text(stringResource(R.string.app_settings))
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ThemeDropdownSection(
                        selectedTheme = appTheme,
                        onThemeSelected = onAppThemeChange
                    )

                    LanguageDropdownSection(
                        selectedLanguage = appLanguage,
                        onLanguageSelected = onAppLanguageChange
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppSettingsDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

        if (showAppsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAppsSheet = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.apps),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = appsForServerText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!appsChecked) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.apps_checking_available),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Text(
                                    text = stringResource(R.string.apps_checking_available_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showAppsSheet = false
                                    showSharesManager = true
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.share_manager),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Text(
                                text = stringResource(R.string.share_manager_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (contactsAvailable && onOpenContacts != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showAppsSheet = false
                                    onOpenContacts()
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.contacts),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Text(
                                    text = stringResource(R.string.contacts_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (circleStackAvailable) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showAppsSheet = false
                                    showCircleStackScreen = true
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.circle_stack),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Text(
                                    text = stringResource(R.string.circle_stack_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (echoStackAvailable) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showAppsSheet = false
                                    showEchoStackScreen = true
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.echo_stack),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Text(
                                    text = stringResource(R.string.echo_stack_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (dropZoneAvailable) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showAppsSheet = false
                                    showDropZoneSheet = true
                                    refreshDropZones()
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.drop_zone),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Text(
                                    text = stringResource(R.string.drop_zone_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    }

                    TextButton(
                        onClick = { showAppsSheet = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        }
        if (showDropZoneSheet) {
            DropZoneScreen(
                zones = dropZones,
                loading = dropZoneLoading,
                creating = dropZoneCreating,
                status = dropZoneStatus,
                latestUrl = dropZoneLatestUrl,
                name = dropZoneName,
                destination = dropZoneDestination,
                password = dropZonePassword,
                expiresInSeconds = dropZoneExpiresInSeconds,
                maxFileBytesText = dropZoneMaxFileBytes,
                maxTotalBytesText = dropZoneMaxTotalBytes,
                duplicatePolicy = dropZoneDuplicatePolicy,
                brandingCompanyName = dropZoneBrandingCompanyName,
                brandingKicker = dropZoneBrandingKicker,
                brandingTitle = dropZoneBrandingTitle,
                brandingDescription = dropZoneBrandingDescription,
                brandingButtonText = dropZoneBrandingButtonText,
                brandingFooterText = dropZoneBrandingFooterText,
                brandingLogoUrl = dropZoneBrandingLogoUrl,
                brandingPrimaryColor = dropZoneBrandingPrimaryColor,
                brandingBackgroundColor = dropZoneBrandingBackgroundColor,
                brandingPanelColor = dropZoneBrandingPanelColor,
                brandingTextColor = dropZoneBrandingTextColor,
                brandingButtonTextColor = dropZoneBrandingButtonTextColor,
                onNameChange = { dropZoneName = it },
                onDestinationChange = { dropZoneDestination = it },
                onPasswordChange = { dropZonePassword = it },
                onExpiresInSecondsChange = { dropZoneExpiresInSeconds = it },
                onMaxFileBytesTextChange = { dropZoneMaxFileBytes = it },
                onMaxTotalBytesTextChange = { dropZoneMaxTotalBytes = it },
                onDuplicatePolicyChange = { dropZoneDuplicatePolicy = it },
                onBrandingCompanyNameChange = { dropZoneBrandingCompanyName = it },
                onBrandingKickerChange = { dropZoneBrandingKicker = it },
                onBrandingTitleChange = { dropZoneBrandingTitle = it },
                onBrandingDescriptionChange = { dropZoneBrandingDescription = it },
                onBrandingButtonTextChange = { dropZoneBrandingButtonText = it },
                onBrandingFooterTextChange = { dropZoneBrandingFooterText = it },
                onBrandingLogoUrlChange = { dropZoneBrandingLogoUrl = it },
                onBrandingPrimaryColorChange = { dropZoneBrandingPrimaryColor = it },
                onBrandingBackgroundColorChange = { dropZoneBrandingBackgroundColor = it },
                onBrandingPanelColorChange = { dropZoneBrandingPanelColor = it },
                onBrandingTextColorChange = { dropZoneBrandingTextColor = it },
                onBrandingButtonTextColorChange = { dropZoneBrandingButtonTextColor = it },
                onRefresh = { refreshDropZones() },
                onCreate = { createDropZoneFromSheet() },
                onCopyLatest = { copyLatestDropZoneLink() },
                onUpdate = { id, name, maxFileBytesText, maxTotalBytesText, duplicatePolicy, branding ->
                    updateDropZoneFromSheet(
                        id = id,
                        name = name,
                        maxFileBytesText = maxFileBytesText,
                        maxTotalBytesText = maxTotalBytesText,
                        duplicatePolicy = duplicatePolicy,
                        branding = branding
                    )
                },
                onDisable = { id -> disableDropZoneFromSheet(id) },
                onRenew = { id, expiresInSeconds ->
                    renewDropZoneFromSheet(id, expiresInSeconds)
                },
                onClearHistory = { id ->
                    clearDropZoneHistoryFromSheet(id)
                },
                historyOpen = dropZoneHistoryOpen,
                historyTitle = dropZoneHistoryTitle,
                historyUploads = dropZoneHistoryUploads,
                historyLoading = dropZoneHistoryLoading,
                historyStatus = dropZoneHistoryStatus,
                onViewHistory = { zone ->
                    showDropZoneHistoryFromSheet(
                        id = zone.id,
                        title = zone.name.ifBlank { "Drop Zone" }
                    )
                },
                onCloseHistory = {
                    closeDropZoneHistoryFromSheet()
                },
                onClose = { showDropZoneSheet = false }
            )
        }
    if (showCreateMenu) {
        ModalBottomSheet(
            onDismissRequest = { showCreateMenu = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
            ) {
                Text(
                    text = stringResource(R.string.create_menu_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.create_upload_file)) },
                    supportingContent = { Text(stringResource(R.string.create_upload_file_desc)) },
                    leadingContent = {
                        Text(
                            text = "↑",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier.clickable {
                        showCreateMenu = false
                        onBeforeExternalPicker()
                        uploadDocumentLauncher.launch(arrayOf("*/*"))
                    }
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.create_upload_multiple)) },
                    supportingContent = { Text(stringResource(R.string.create_upload_multiple_desc)) },
                    leadingContent = {
                        Text(
                            text = "↑↑",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier.clickable {
                        showCreateMenu = false
                        onBeforeExternalPicker()
                        uploadMultipleDocumentsLauncher.launch(arrayOf("*/*"))
                    }
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.create_new_folder)) },
                    leadingContent = {
                        Text(
                            text = "📁",
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    modifier = Modifier.clickable {
                        showCreateMenu = false
                        newFolderDialogOpen = true
                    }
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.create_new_text_file)) },
                    leadingContent = {
                        Text(
                            text = "TXT",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier.clickable {
                        showCreateMenu = false
                        newTextFileDialogOpen = true
                    }
                )


                // PQNAS_ANDROID_WORKSPACE_MESSAGES_LINKS_V1: save a URL shortcut into the current workspace.
                if (currentScope is FileScope.Workspace && scopedOps.canWrite(currentScope)) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.create_save_url_link)) },
                        supportingContent = { Text(stringResource(R.string.create_save_url_link_desc)) },
                        leadingContent = {
                            Text(
                                text = "🔗",
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        modifier = Modifier.clickable {
                            showCreateMenu = false
                            showWorkspaceUrlLinkDialog = true
                        }
                    )
                }

            }
        }
    }

    if (newFolderDialogOpen) {
        AlertDialog(
            onDismissRequest = {
                newFolderDialogOpen = false
                newFolderName = ""
            },
            title = { Text(stringResource(R.string.create_new_folder)) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.create_folder_name)) }
                )
            },
            confirmButton = {
                TextButton(onClick = { createFolder(newFolderName) }) {
                    Text(stringResource(R.string.create_button))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        newFolderDialogOpen = false
                        newFolderName = ""
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (newTextFileDialogOpen) {
        AlertDialog(
            onDismissRequest = {
                newTextFileDialogOpen = false
                newTextFileName = ""
            },
            title = { Text(stringResource(R.string.create_new_text_file)) },
            text = {
                OutlinedTextField(
                    value = newTextFileName,
                    onValueChange = { newTextFileName = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.create_file_name)) }
                )
            },
            confirmButton = {
                TextButton(onClick = { createTextFile(newTextFileName) }) {
                    Text(stringResource(R.string.create_button))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        newTextFileDialogOpen = false
                        newTextFileName = ""
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    infoItem?.let { item ->
        AlertDialog(
            onDismissRequest = { closeInfoDialog() },
            title = { Text(stringResource(R.string.info_title)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    val fullPath = itemFullPath(item)
                    val canEditComment = scopedOps.canWrite(currentScope)

                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(stringResource(R.string.info_type_value, stringResource(if (item.type == "dir") R.string.info_type_folder else R.string.info_type_file)))
                    Text(stringResource(R.string.info_path_value, fullPath))

                    if (item.isFavorite) {
                        Text(stringResource(R.string.info_favorite_yes))
                    }

                    if (item.isShared) {
                        Text(stringResource(R.string.info_shared_yes))
                    }

                    if (item.isLocked) {
                        Text(
                            text = item.locked_by_display?.let { stringResource(R.string.info_locked_by, it) } ?: stringResource(R.string.info_locked),
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    HorizontalDivider()

                    Text(
                        text = stringResource(R.string.info_comment_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    when {
                        infoNoteLoading -> {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(stringResource(R.string.info_loading_comment))
                        }

                        canEditComment -> {
                            OutlinedTextField(
                                value = infoNoteText,
                                onValueChange = { infoNoteText = it },
                                minLines = 3,
                                maxLines = 8,
                                label = { Text(stringResource(R.string.info_file_comment_label)) },
                                placeholder = { Text(stringResource(R.string.info_file_comment_placeholder)) },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text(
                                text = stringResource(R.string.info_comment_server_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        infoNoteText.isBlank() -> {
                            Text(
                                text = stringResource(R.string.info_no_comment_yet),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        else -> {
                            Text(infoNoteText)
                        }
                    }

                    if (infoNoteStatus.isNotBlank()) {
                        Text(
                            text = infoNoteStatus,
                            color = if (infoNoteStatus.contains("failed", ignoreCase = true) ||
                                infoNoteStatus.contains("error", ignoreCase = true)
                            ) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            },
            confirmButton = {
                if (scopedOps.canWrite(currentScope)) {
                    TextButton(
                        enabled = !infoNoteLoading &&
                                !infoNoteSaving &&
                                infoNoteText.trim() != infoNoteOriginalText.trim(),
                        onClick = { saveInfoComment(item, infoNoteText) }
                    ) {
                        Text(if (infoNoteSaving) stringResource(R.string.info_saving) else stringResource(R.string.info_save))
                    }
                }
            },
            dismissButton = {
                Row {
                    if (scopedOps.canWrite(currentScope) && infoNoteOriginalText.isNotBlank()) {
                        TextButton(
                            enabled = !infoNoteLoading && !infoNoteSaving,
                            onClick = { saveInfoComment(item, "") }
                        ) {
                            Text(stringResource(R.string.info_clear))
                        }
                    }

                    TextButton(onClick = { closeInfoDialog() }) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        )
    }

    renameItem?.let { item ->
        AlertDialog(
            onDismissRequest = {
                renameItem = null
                renameText = ""
            },
            title = { Text(stringResource(R.string.rename_title)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.rename_new_name)) }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (item.isLocked) {
                            val msg = context.getString(R.string.rename_locked_message, item.name)
                            renameItem = null
                            renameText = ""
                            status = msg
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                            return@TextButton
                        }
                        val newName = renameText.trim()
                        if (newName.isBlank()) {
                            status = context.getString(R.string.rename_empty_name)
                            return@TextButton
                        }

                        if (newName == item.name) {
                            renameItem = null
                            renameText = ""
                            return@TextButton
                        }

                        scope.launch {
                            try {
                                val fromPath = buildItemPath(currentPath, item.name)
                                val toPath = buildItemPath(currentPath, newName)
                                scopedOps.move(currentScope, fromPath, toPath)
                                renameItem = null
                                renameText = ""
                                status = "OK"
                                snackbarHostState.showSnackbar(context.getString(R.string.rename_success, item.name, newName))
                                load(currentPath)
                            } catch (e: Exception) {
                                val msg = friendlyHttpMessage("Rename", e)
                                status = msg
                                snackbarHostState.showSnackbar(msg)
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.rename_button))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        renameItem = null
                        renameText = ""
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    moveCopyItem?.let { item ->
        AlertDialog(
            onDismissRequest = {
                moveCopyItem = null
                moveCopyDestination = ""
            },
            title = { Text(stringResource(if (moveCopyMode == "Copy") R.string.copy_title else R.string.move_title, item.name)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(stringResource(R.string.copy_move_choose_destination))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            enabled = !moveCopyPickerLoading,
                            onClick = { loadMoveCopyPicker(null) }
                        ) {
                            Text(stringResource(R.string.root))
                        }

                        TextButton(
                            enabled = !moveCopyPickerLoading && !moveCopyPickerPath.isNullOrBlank(),
                            onClick = { loadMoveCopyPicker(parentPath(moveCopyPickerPath)) }
                        ) {
                            Text(stringResource(R.string.up))
                        }

                        TextButton(
                            enabled = !moveCopyPickerLoading,
                            onClick = { loadMoveCopyPicker(moveCopyPickerPath) }
                        ) {
                            Text(stringResource(R.string.refresh))
                        }
                    }

                    Text(
                        text = stringResource(R.string.copy_move_current, normalizeRelPath(moveCopyPickerPath)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = stringResource(
                            R.string.copy_move_target,
                            normalizeRelPath(
                                buildItemPath(
                                    if (moveCopyDestination.isBlank()) null else moveCopyDestination,
                                    item.name
                                )
                            )
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider()

                    if (moveCopyPickerLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(stringResource(R.string.copy_move_loading_folders))
                    } else if (moveCopyPickerFolders.isEmpty()) {
                        Text(
                            text = if (moveCopyPickerStatus.isNotBlank()) {
                                moveCopyPickerStatus
                            } else {
                                stringResource(R.string.copy_move_no_subfolders)
                            },
                            color = if (moveCopyPickerStatus.isNotBlank()) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                        ) {
                            items(
                                moveCopyPickerFolders,
                                key = { folder -> folder.name }
                            ) { folder ->
                                val folderPath = normalizeRelPath(
                                    buildItemPath(moveCopyPickerPath, folder.name)
                                )

                                ListItem(
                                    headlineContent = {
                                        Text("📁 ${folder.name}")
                                    },
                                    supportingContent = {
                                        Text("/$folderPath")
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            loadMoveCopyPicker(folderPath)
                                        }
                                )

                                HorizontalDivider()
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !moveCopyPickerLoading,
                    onClick = {
                        runMoveCopy(item, moveCopyMode, moveCopyDestination)
                    }
                ) {
                    Text(stringResource(if (moveCopyMode == "Copy") R.string.copy_here else R.string.move_here))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        moveCopyItem = null
                        moveCopyDestination = ""
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    deleteItem?.let { item ->
        val trashItemType = stringResource(
            if (item.type == "dir") R.string.trash_item_folder else R.string.trash_item_file
        )
        val trashMovedSnackbar = stringResource(R.string.trash_moved_snackbar, item.name)

        AlertDialog(
            onDismissRequest = { deleteItem = null },
            title = { Text(stringResource(R.string.trash_title)) },
            text = {
                Text(stringResource(R.string.trash_confirm_message, trashItemType, item.name))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                val path = buildItemPath(currentPath, item.name)
                                scopedOps.delete(currentScope, path)
                                deleteItem = null
                                status = "OK"
                                snackbarHostState.showSnackbar(trashMovedSnackbar)
                                load(currentPath)
                            } catch (e: Exception) {
                                val msg = friendlyHttpMessage("Delete", e)
                                status = msg
                                snackbarHostState.showSnackbar(msg)
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.trash_title))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteItem = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (overwriteUploadTargetPath != null && overwriteUploadUri != null) {
        AlertDialog(
            onDismissRequest = {
                overwriteUploadTargetPath = null
                overwriteUploadUri = null
                pendingUploadUri = null
                pendingUploadName = null
            },
            title = { Text(stringResource(R.string.files_replace_file_title)) },
            text = {
                Text(stringResource(R.string.files_replace_file_confirm))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uri = overwriteUploadUri
                        if (uri != null) {
                            overwriteUploadTargetPath = null
                            overwriteUploadUri = null
                            pendingUploadUri = null
                            status = context.getString(R.string.upload_replacing_item, pendingUploadName ?: context.getString(R.string.info_type_file).lowercase())
                            pendingUploadName = null
                            uploadUri(uri, overwrite = true)
                        } else {
                            overwriteUploadTargetPath = null
                            overwriteUploadUri = null
                            pendingUploadUri = null
                            pendingUploadName = null
                        }
                    }
                ) {
                    Text(stringResource(R.string.files_replace))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        overwriteUploadTargetPath = null
                        overwriteUploadUri = null
                        pendingUploadUri = null
                        pendingUploadName = null
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

        shareDialogItem?.let { item ->
            AlertDialog(
                onDismissRequest = {
                    shareDialogItem = null
                    shareDialogUrl = ""
                    shareDialogStatus = ""
                    shareDialogExistingToken = null
                    shareDialogExpiry = defaultShareExpiryOption()
                },
                title = { Text(stringResource(R.string.files_share_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(item.name)

                        if (shareDialogUrl.isBlank()) {
                            Text(
                                text = stringResource(R.string.files_share_valid_for),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            SHARE_EXPIRY_OPTIONS.forEach { option ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { shareDialogExpiry = option }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = shareDialogExpiry == option,
                                        onClick = { shareDialogExpiry = option }
                                    )
                                    Text(
                                        text = option.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        if (shareDialogUrl.isNotBlank()) {
                            OutlinedTextField(
                                value = shareDialogUrl,
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.files_share_link_label)) }
                            )

                            Text(
                                text = stringResource(R.string.files_share_change_validity_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (shareDialogStatus.isNotBlank()) {
                            Text(
                                text = shareDialogStatus,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (shareDialogUrl.isBlank()) {
                            TextButton(
                                onClick = { createShareFor(item, shareDialogExpiry.expiresSec) }
                            ) {
                                Text(stringResource(R.string.files_share_create))
                            }
                        } else {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        val ok = copyText(context, shareDialogUrl)
                                        shareDialogStatus = if (ok) context.getString(R.string.shares_copied_link) else context.getString(R.string.shares_copy_failed)
                                    }
                                }
                            ) {
                                Text("Copy")
                            }

                            TextButton(
                                onClick = { revokeShareForCurrentDialog() }
                            ) {
                                Text(stringResource(R.string.files_share_revoke))
                            }
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            shareDialogItem = null
                            shareDialogUrl = ""
                            shareDialogStatus = ""
                            shareDialogExistingToken = null
                            shareDialogExpiry = defaultShareExpiryOption()
                        }
                    ) {
                        Text(stringResource(R.string.close))
                    }
                }
            )
        }

        versionsItem?.let { item ->
            FileVersionsSheet(
                filesRepository = filesRepository,
                fileScope = currentScope,
                relPath = buildItemPath(currentPath, item.name),
                displayName = item.name,
                onDismiss = {
                    versionsItem = null
                },
                onRestored = { message ->
                    status = message
                    load(currentPath)
                }
            )
        }

        if (imagePreviewStartIndex != null && imagePreviewItems.isNotEmpty()) {
            ImagePreviewScreen(
                filesRepository = filesRepository,
                fileScope = currentScope,
                currentPath = currentPath,
                images = imagePreviewItems,
                initialIndex = imagePreviewStartIndex!!,
                onClose = {
                    imagePreviewStartIndex = null
                    imagePreviewItems = emptyList()
                }
            )
        }
        audioPlayerStartIndex?.let { startIndex ->
            AudioPlayerScreen(
                filesRepository = filesRepository,
                fileScope = currentScope,
                currentPath = currentPath,
                audioFiles = audioPlayerItems,
                initialIndex = startIndex,
                onClose = {
                    audioPlayerStartIndex = null
                    audioPlayerItems = emptyList()
                }
            )
        }

        videoPlayerStartIndex?.let { startIndex ->
            VideoPlayerScreen(
                filesRepository = filesRepository,
                fileScope = currentScope,
                currentPath = currentPath,
                videoFiles = videoPlayerItems,
                initialIndex = startIndex,
                onClose = {
                    videoPlayerStartIndex = null
                    videoPlayerItems = emptyList()
                }
            )
        }
        if (showSharesManager) {
            SharesManagerScreen(
                filesRepository = filesRepository,
                onClose = {
                    showSharesManager = false
                    refreshCurrent()
                }
            )
        }
        if (showEchoStackScreen) {
            val echoStackRepository = remember(filesRepository) {
                EchoStackRepository(filesRepository.createEchoStackApiInternal())
            }

            EchoStackScreen(
                repository = echoStackRepository,
                onClose = {
                    showEchoStackScreen = false
                }
            )
        }

        // PQNAS_ANDROID_WORKSPACE_MESSAGES_LINKS_V1: workspace message drawer.
        if (showWorkspaceMessagesSheet) {
            val activeWorkspace = currentScope as? FileScope.Workspace
            if (activeWorkspace != null) {
                WorkspaceMessagesSheet(
                    filesRepository = filesRepository,
                    workspace = activeWorkspace,
                    onClose = { showWorkspaceMessagesSheet = false }
                )
            }
        }

        // PQNAS_ANDROID_WORKSPACE_MESSAGES_LINKS_V1: save URL shortcut into workspace.
        if (showWorkspaceUrlLinkDialog) {
            val activeWorkspace = currentScope as? FileScope.Workspace
            if (activeWorkspace != null) {
                WorkspaceUrlLinkDialog(
                    currentPath = currentPath,
                    onDismiss = { showWorkspaceUrlLinkDialog = false },
                    onSave = { title, url ->
                        val targetName = workspaceUrlLinkFileName(title, url)
                        val targetPath = normalizeRelPath(buildItemPath(currentPath, targetName))
                        scope.launch {
                            try {
                                // PQNAS_ANDROID_SAVE_URL_USE_UPLOAD_V1:
                                // Create a new .url shortcut through the normal file upload path.
                                // writeText is for editing/overwriting existing text files and may return
                                // "item not found" when the shortcut does not exist yet.
                                val shortcutBody = workspaceUrlShortcutContent(title, url)
                                    .toRequestBody(null)

                                scopedOps.upload(
                                    scope = activeWorkspace,
                                    path = targetPath,
                                    body = shortcutBody,
                                    overwrite = false
                                )
                                showWorkspaceUrlLinkDialog = false
                                status = context.getString(R.string.files_saved_url_link, targetName)
                                snackbarHostState.showSnackbar(status)
                                load(currentPath)
                            } catch (e: Exception) {
                                val msg = friendlyHttpMessage("Save URL link", e)
                                status = msg
                                snackbarHostState.showSnackbar(msg)
                            }
                        }
                    }
                )
            }
        }

        if (pdfPreviewPath != null && pdfPreviewName != null) {
            PdfPreviewScreen(
                filesRepository = filesRepository,
                fileScope = currentScope,
                relPath = pdfPreviewPath!!,
                displayName = pdfPreviewName!!,
                onClose = {
                    pdfPreviewPath = null
                    pdfPreviewName = null
                }
            )
        }

        if (textEditorPath != null && textEditorName != null) {
            TextEditorScreen(
                filesRepository = filesRepository,
                fileScope = currentScope,
                relPath = textEditorPath!!,
                displayName = textEditorName!!,
                onClose = {
                    textEditorPath = null
                    textEditorName = null
                },
                onSaved = {
                    load(currentPath)
                }
            )
        }
    }
}


@Composable
private fun ConnectedBrandingSummary(
    appTitle: String,
    serverHost: String
) {
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
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = appTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (serverHost.isNotBlank()) {
                // Runtime branding is display-only. Keep the real connected
                // domain visible so a server cannot hide its origin behind a logo/name.
                Text(
                    text = stringResource(R.string.connected_server_domain, serverHost),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class ShareExpiryOption(
    val label: String,
    val expiresSec: Long?
)

private val SHARE_EXPIRY_OPTIONS = listOf(
    ShareExpiryOption("1 hour", 3600L),
    ShareExpiryOption("1 day", 86400L),
    ShareExpiryOption("7 days", 7L * 86400L),
    ShareExpiryOption("Never", null)
)

private fun defaultShareExpiryOption(): ShareExpiryOption {
    return SHARE_EXPIRY_OPTIONS.first { it.expiresSec == 86400L }
}

private fun shareExpiryLabel(expiresSec: Long?): String {
    return when (expiresSec) {
        3600L -> "1 hour"
        86400L -> "1 day"
        7L * 86400L -> "7 days"
        null -> "never"
        else -> "${expiresSec}s"
    }
}
@Composable
private fun themeLabel(theme: PqnasAppTheme): String {
    return when (theme) {
        PqnasAppTheme.Dark -> stringResource(R.string.theme_dark_label)
        PqnasAppTheme.Bright -> stringResource(R.string.theme_bright_label)
        PqnasAppTheme.CpunkOrange -> stringResource(R.string.theme_cpunk_orange_label)
        PqnasAppTheme.WinClassic -> stringResource(R.string.theme_win_classic_label)
    }
}

@Composable
private fun themeDescription(theme: PqnasAppTheme): String {
    return when (theme) {
        PqnasAppTheme.Dark -> stringResource(R.string.theme_dark_desc)
        PqnasAppTheme.Bright -> stringResource(R.string.theme_bright_desc)
        PqnasAppTheme.CpunkOrange -> stringResource(R.string.theme_cpunk_orange_desc)
        PqnasAppTheme.WinClassic -> stringResource(R.string.theme_win_classic_desc)
    }
}

@Composable
private fun languageLabel(language: PqnasAppLanguage): String {
    return when (language) {
        PqnasAppLanguage.System -> stringResource(R.string.language_system_label)
        PqnasAppLanguage.English -> stringResource(R.string.language_english_label)
        PqnasAppLanguage.Finnish -> stringResource(R.string.language_finnish_label)
        PqnasAppLanguage.SimplifiedChinese -> stringResource(R.string.language_simplified_chinese_label)
        PqnasAppLanguage.Swedish -> stringResource(R.string.language_swedish_label)
        PqnasAppLanguage.Ukrainian -> stringResource(R.string.language_ukrainian_label)
        PqnasAppLanguage.German -> stringResource(R.string.language_german_label)
        PqnasAppLanguage.Estonian -> stringResource(R.string.language_estonian_label)
        PqnasAppLanguage.Polish -> stringResource(R.string.language_polish_label)
        PqnasAppLanguage.Spanish -> stringResource(R.string.language_spanish_label)
        PqnasAppLanguage.French -> stringResource(R.string.language_french_label)
        PqnasAppLanguage.Italian -> stringResource(R.string.language_italian_label)
        PqnasAppLanguage.Turkish -> stringResource(R.string.language_turkish_label)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageDropdownSection(
    selectedLanguage: PqnasAppLanguage,
    onLanguageSelected: (PqnasAppLanguage) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = languageLabel(selectedLanguage),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.language_select_label)) },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                PqnasAppLanguage.values().forEach { option ->
                    DropdownMenuItem(
                        text = { Text(languageLabel(option)) },
                        onClick = {
                            expanded = false
                            onLanguageSelected(option)
                        }
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.language_restart_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeDropdownSection(
    selectedTheme: PqnasAppTheme,
    onThemeSelected: (PqnasAppTheme) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = themeLabel(selectedTheme),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.theme_select_label)) },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                PqnasAppTheme.values().forEach { option ->
                    DropdownMenuItem(
                        text = { Text(themeLabel(option)) },
                        onClick = {
                            expanded = false
                            onThemeSelected(option)
                        }
                    )
                }
            }
        }

        Text(
            text = themeDescription(selectedTheme),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsStorageSection(
    storage: MeStorageResponse?,
    storageStatus: String
) {
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
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.storage),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when {
                storage != null -> {
                    val allocated = storage.storage_state == "allocated"
                    val progress = if (storage.quota_bytes > 0L) {
                        (storage.used_bytes.toFloat() / storage.quota_bytes.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }

                    val observedLocale = LocalConfiguration.current.locales[0]
                    val accentColor = when (storage.warn_level?.lowercase(observedLocale)) {
                        "crit" -> MaterialTheme.colorScheme.error
                        "warn" -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    }

                    if (!allocated) {
                        Text(
                            text = stringResource(R.string.storage_not_allocated),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(
                            text = "${formatBytes(storage.used_bytes)} / ${formatBytes(storage.quota_bytes)}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(4.dp))

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = stringResource(R.string.storage_usage, storage.used_percent.toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = accentColor
                        )

                        if (storage.partial) {
                            Text(
                                text = stringResource(R.string.storage_approximate),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                storageStatus.isNotBlank() -> {
                    Text(
                        text = storageStatus,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                else -> {
                    Text(
                        text = stringResource(R.string.storage_info_not_loaded),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun FileRow(
    item: FileItemDto,
    leadingVisual: @Composable () -> Unit,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMenuAction: (String, FileItemDto) -> Unit
) {
    val isDir = item.type == "dir"
    var menuExpanded by remember { mutableStateOf(false) }
    val rowBackground = if (item.isLocked) {
        Color(0x66FFC107)
    } else {
        Color.Transparent
    }

    val typeAndSize = if (isDir) {
        stringResource(R.string.file_type_directory)
    } else {
        stringResource(R.string.file_type_file_size, formatBytes(item.size_bytes ?: 0))
    }

    val dateText = item.mtime_unix?.takeIf { it > 0 }?.let { formatUnixTime(it) } ?: ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBackground)
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isDir || item.isLocked) FontWeight.SemiBold else FontWeight.Normal
                )

                if (item.isLocked) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = stringResource(R.string.file_locked),
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFFFFC107)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.size(width = 20.dp, height = 40.dp)
                ) {
                    if (item.isFavorite) {
                        Text(
                            text = "★",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Spacer(modifier = Modifier.height(18.dp))
                    }

                    if (item.isShared) {
                        Text(
                            text = "🔗",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                leadingVisual()

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = typeAndSize,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (dateText.isNotBlank()) {
                        Text(
                            text = dateText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (item.isLocked) {
                        Text(
                            text = item.lockSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFFC107),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(

                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (isDir) {
                        Text(
                            text = stringResource(R.string.file_open),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.file_more_actions)
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(if (item.isFavorite) R.string.menu_remove_from_favorites else R.string.menu_add_to_favorites)) },
                            onClick = {
                                menuExpanded = false
                                onMenuAction("ToggleFavorite", item)
                            }
                        )

                        DropdownMenuItem(
                            text = { Text(stringResource(if (item.isShared) R.string.menu_shared else R.string.menu_share)) },
                            onClick = {
                                menuExpanded = false
                                onMenuAction("Share", item)
                            }
                        )

                        if (!isDir && isProbablyImageFile(item.name)) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_open_preview)) },
                                onClick = {
                                    menuExpanded = false
                                    onMenuAction("Preview", item)
                                }
                            )
                        }

                        if (!isDir && isProbablyAudioFile(item.name)) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_play_audio)) },
                                onClick = {
                                    menuExpanded = false
                                    onMenuAction("PlayAudio", item)
                                }
                            )
                        }

                        if (!isDir && isProbablyVideoFile(item.name)) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_play_video)) },
                                onClick = {
                                    menuExpanded = false
                                    onMenuAction("PlayVideo", item)
                                }
                            )
                        }

                        if (!isDir && isProbablyPdfFile(item.name)) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_open_preview)) },
                                onClick = {
                                    menuExpanded = false
                                    onMenuAction("PreviewPdf", item)
                                }
                            )
                        }

                        if (!isDir && isProbablyTextFile(item.name)) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_edit_text)) },
                                onClick = {
                                    menuExpanded = false
                                    onMenuAction("EditText", item)
                                }
                            )
                        }
                        if (!isDir) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_versions)) },
                                onClick = {
                                    menuExpanded = false
                                    onMenuAction("Versions", item)
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_download)) },
                            onClick = {
                                menuExpanded = false
                                onMenuAction("Download", item)
                            }
                        )

                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_rename)) },
                            onClick = {
                                menuExpanded = false
                                onMenuAction("Rename", item)
                            }
                        )

                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_move)) },
                            onClick = {
                                menuExpanded = false
                                onMenuAction("Move", item)
                            }
                        )

                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_copy)) },
                            onClick = {
                                menuExpanded = false
                                onMenuAction("Copy", item)
                            }
                        )

                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_move_to_trash)) },
                            onClick = {
                                menuExpanded = false
                                onMenuAction("Delete", item)
                            }
                        )

                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_info)) },
                            onClick = {
                                menuExpanded = false
                                onMenuAction("Info", item)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileIcon(
    item: FileItemDto,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val assetPath = remember(item.type, item.name) {
        FileTypeIcons.assetPathFor(item)
    }

    val bitmap = remember(assetPath) {
        SvgIconLoader.loadBitmapFromAssets(
            context = context,
            assetPath = assetPath,
            sizePx = 64
        )
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = if (item.type == "dir") stringResource(R.string.file_directory_icon) else stringResource(R.string.file_file_icon),
            modifier = modifier
        )
    } else {
        Text(
            text = if (item.type == "dir") "📁" else "📄",
            modifier = modifier,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

private fun isProbablyTextFile(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase(Locale.getDefault())
    return ext in setOf(
        "txt", "md", "json", "js", "ts", "jsx", "tsx",
        "html", "htm", "css", "xml", "yml", "yaml",
        "toml", "ini", "conf", "log",
        "c", "cc", "cpp", "cxx", "h", "hh", "hpp", "hxx",
        "py", "sh", "bash", "zsh", "sql", "csv", "tsv",
        "java", "go", "rs", "rb", "php", "lua", "swift", "kt",
        "url", "webloc", "desktop"
    )
}
private fun isProbablyVideoFile(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase(Locale.getDefault())
    return ext in setOf(
        "mp4",
        "m4v",
        "mov",
        "mkv",
        "webm",
        "avi",
        "3gp",
        "3gpp"
    )
}
private fun isProbablyImageFile(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase(Locale.getDefault())
    return ext in setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "ico")
}

private fun isProbablyAudioFile(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase(Locale.getDefault())
    return ext in setOf(
        "mp3",
        "m4a",
        "aac",
        "wav",
        "ogg",
        "oga",
        "opus",
        "flac",
        "webm"
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB", "PB")
    val digitGroups = (ln(bytes.toDouble()) / ln(1024.0)).toInt()
    val value = bytes / 1024.0.pow(digitGroups.toDouble())
    return String.format(Locale.US, "%.1f %s", value, units[digitGroups - 1])
}

private fun parseDropZoneLimitBytes(raw: String): Long {
    val value = raw.trim()
    if (value.isBlank()) return 0L

    val compact = value
        .lowercase(Locale.US)
        .replace(",", ".")
        .replace(" ", "")

    val numberText = compact.takeWhile { it.isDigit() || it == '.' }
    if (numberText.isBlank()) return 0L

    val number = numberText.toDoubleOrNull() ?: return 0L
    if (number <= 0.0) return 0L

    val suffix = compact.drop(numberText.length)

    val multiplier = when {
        suffix.startsWith("tib") || suffix.startsWith("tb") || suffix == "t" -> 1024.0 * 1024.0 * 1024.0 * 1024.0
        suffix.startsWith("gib") || suffix.startsWith("gb") || suffix == "g" -> 1024.0 * 1024.0 * 1024.0
        suffix.startsWith("mib") || suffix.startsWith("mb") || suffix == "m" -> 1024.0 * 1024.0
        suffix.startsWith("kib") || suffix.startsWith("kb") || suffix == "k" -> 1024.0
        suffix == "b" || suffix.startsWith("byte") -> 1.0
        suffix.isBlank() -> 1024.0 * 1024.0
        else -> return 0L
    }

    return (number * multiplier)
        .toLong()
        .coerceAtLeast(0L)
}

private fun formatUnixTime(unixSeconds: Long): String {
    val date = Date(unixSeconds * 1000L)
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return fmt.format(date)
}

private fun buildItemPath(currentPath: String?, itemName: String): String {
    return listOfNotNull(currentPath, itemName)
        .filter { it.isNotBlank() }
        .joinToString("/")
}

private suspend fun saveDownloadedFile(
    context: Context,
    uri: Uri,
    bytes: ByteArray
) {
    withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(bytes)
            out.flush()
        } ?: throw IllegalStateException("Could not open output stream")
    }
}

private suspend fun copyText(context: Context, text: String): Boolean {
    return try {
        withContext(Dispatchers.Main) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(context.getString(R.string.files_share_link_clip_label), text)
            clipboard.setPrimaryClip(clip)
        }
        true
    } catch (_: Exception) {
        false
    }
}
private fun readHttpErrorToken(error: Throwable): String {
    val e = error as? HttpException ?: return ""

    val raw = try {
        e.response()?.errorBody()?.string().orEmpty()
    } catch (_: Exception) {
        ""
    }

    if (raw.isBlank()) return ""

    val json = try {
        JSONObject(raw)
    } catch (_: Exception) {
        return raw.lowercase(Locale.getDefault())
    }

    return listOf(
        json.optString("error").orEmpty(),
        json.optString("code").orEmpty(),
        json.optString("message").orEmpty()
    )
        .joinToString(" ")
        .lowercase(Locale.getDefault())
}
private fun friendlyHttpMessage(
    action: String,
    error: Throwable
): String {
    val serverToken = readHttpErrorToken(error)
    val lowMessage = error.message.orEmpty().lowercase(Locale.getDefault())

    if (
        serverToken.contains("storage_unallocated") ||
        serverToken.contains("storage not allocated") ||
        serverToken.contains("no file storage assigned") ||
        lowMessage.contains("storage_unallocated") ||
        lowMessage.contains("storage not allocated") ||
        lowMessage.contains("no file storage assigned")
    ) {
        return "Storage not allocated yet. Your device is paired, but this account has no file storage assigned. Ask an administrator to allocate storage in the web admin panel → Admin → User profiles."
    }

    val http = (error as? HttpException)?.code()
        ?: Regex("""\bHTTP\s+(\d{3})\b""")
            .find(error.message.orEmpty())
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    if (
        http == 423 ||
        serverToken.contains("locked") ||
        serverToken.contains("file_locked") ||
        lowMessage.contains("locked") ||
        lowMessage.contains("file_locked")
    ) {
        return "$action failed: file is locked. Unlock it before changing or deleting it."
    }
    return when (http) {
        400 -> "$action failed: invalid request."
        401 -> "Session expired. Please pair again."
        403 -> "Access denied."
        404 -> "Item not found."
        409 -> when (action) {
            "Rename" -> "Cannot rename: a file or folder with that name already exists."
            "Move" -> "Cannot move: destination already exists."
            "Delete" -> "Cannot move to trash: item is in a conflicting state."
            "Upload" -> "Upload failed: a file or folder with that name already exists."
            "Create text file" -> "Cannot create file: a file or folder with that name already exists."
            "Create folder" -> "Cannot create folder: path conflicts with an existing item."
            "Write text" -> "File changed on server. Reload and review before saving again."
            else -> "$action failed: destination already exists."
        }
        411 -> "Upload failed: server requires a known file size."
        413 -> "Upload failed: file is too large."
        500 -> "$action failed: server error."
        else -> {
            val type = error::class.java.simpleName
            val msg = error.message?.takeIf { it.isNotBlank() } ?: "unknown error"
            "$action failed: [$type] $msg"
        }
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
    context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) {
            return cursor.getString(nameIndex)
        }
    }
    return null
}


@Composable
private fun SettingsAboutSection(
    appTitle: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.cpunk_about),
                contentDescription = stringResource(R.string.files_about_mascot_desc),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Text(
            text = appTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = stringResource(R.string.about_files_description, appTitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        )

        Text(
            text = stringResource(R.string.files_about_security_stack),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = stringResource(R.string.files_about_security_stack_desc),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// PQNAS_ANDROID_SAVE_URL_USE_UPLOAD_V1


private fun isProbablyPdfFile(name: String): Boolean =
    name.lowercase(Locale.getDefault()).endsWith(".pdf")
