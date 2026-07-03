package com.pqnas.mobile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.pqnas.mobile.auth.AuthRepository
import com.pqnas.mobile.auth.PairQrPayload
import com.pqnas.mobile.auth.TokenStore
import com.pqnas.mobile.files.FilesRepository
import com.pqnas.mobile.security.AppUnlockPolicy
import com.pqnas.mobile.ui.screens.AppLockScreen
import com.pqnas.mobile.ui.screens.FilesScreen
import com.pqnas.mobile.ui.screens.PairConfirmScreen
import com.pqnas.mobile.ui.screens.ScanPairQrScreen
import com.pqnas.mobile.ui.settings.PqnasAppLanguageStore
import com.pqnas.mobile.ui.screens.ServerSetupScreen
import com.pqnas.mobile.ui.theme.PQNASTheme
import com.pqnas.mobile.ui.theme.PqnasThemeStore
import kotlinx.coroutines.launch
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pqnas.mobile.admin.AdminRepository
import com.pqnas.mobile.api.ApiFactory
import com.pqnas.mobile.ui.screens.AdminScreen
import com.pqnas.mobile.contacts.ContactsRepository
import com.pqnas.mobile.ui.screens.ContactsScreen

class MainActivity : FragmentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(PqnasAppLanguageStore.wrapContext(newBase))
    }

    // PQNAS_INCOMING_ANDROID_SHARE_V1
    private var incomingShareHandler: ((Intent) -> Unit)? = null

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingShareHandler?.invoke(intent)
    }

    private fun extractIncomingShareManifestPath(intent: Intent?): String? {
        return intent
            ?.getStringExtra(IncomingShareActivity.EXTRA_MANIFEST_PATH)
            ?.takeIf { it.isNotBlank() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContent {
            val context = LocalContext.current
            val languageStore = remember { PqnasAppLanguageStore(context) }
            var appLanguage by remember { mutableStateOf(languageStore.loadLanguage()) }
            val themeStore = remember { PqnasThemeStore(context) }
            var appTheme by remember { mutableStateOf(themeStore.loadTheme()) }

            PQNASTheme(appTheme = appTheme) {
                val tokenStore = remember { TokenStore(context) }
                val lifecycleOwner = LocalLifecycleOwner.current
                val authRepository = remember { AuthRepository(tokenStore) }
                val scope = rememberCoroutineScope()

                var screen by remember { mutableStateOf("server") }
                var baseUrl by remember { mutableStateOf("") }
                var pairPayload by remember { mutableStateOf<PairQrPayload?>(null) }
                var authLoaded by remember { mutableStateOf(false) }
                var isAdmin by remember { mutableStateOf(false) }

                // PQNAS_INCOMING_ANDROID_SHARE_V1: pending Android Sharesheet batch, processed after app unlock.
                var incomingShareManifestPath by remember {
                    mutableStateOf(extractIncomingShareManifestPath(intent))
                }
                var incomingShareNonce by remember {
                    mutableIntStateOf(if (incomingShareManifestPath.isNullOrBlank()) 0 else 1)
                }

                DisposableEffect(Unit) {
                    incomingShareHandler = { incoming ->
                        val nextManifestPath = extractIncomingShareManifestPath(incoming)
                        if (!nextManifestPath.isNullOrBlank()) {
                            incomingShareManifestPath = nextManifestPath
                            incomingShareNonce += 1
                        }
                    }

                    onDispose {
                        incomingShareHandler = null
                    }
                }

                var appUnlocked by remember { mutableStateOf(false) }
                var appLockStatus by remember { mutableStateOf("") }
                var unlockPromptActive by remember { mutableStateOf(false) }

                // Android file picker temporarily moves our app through onStop().
                // Do not lock the app for that intentional external picker handoff.
                val externalPickerLaunchedAtMs = remember { mutableLongStateOf(0L) }
                DisposableEffect(lifecycleOwner, authLoaded, screen) {
                    val observer = object : DefaultLifecycleObserver {
                        override fun onStop(owner: LifecycleOwner) {
                            if (authLoaded && (screen == "files" || screen == "admin" || screen == "contacts")) {
                                val pickerHandoffAgeMs =
                                    System.currentTimeMillis() - externalPickerLaunchedAtMs.longValue

                                if (pickerHandoffAgeMs in 0L..2_000L) {
                                    externalPickerLaunchedAtMs.longValue = 0L
                                    return
                                }

                                appUnlocked = false
                                unlockPromptActive = false
                                appLockStatus = ""
                            }
                        }
                    }

                    lifecycleOwner.lifecycle.addObserver(observer)

                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }
                fun logoutToServerScreen() {
                    scope.launch {
                        authRepository.logout()
                        baseUrl = ""
                        isAdmin = false
                        pairPayload = null
                        appUnlocked = false
                        appLockStatus = ""
                        screen = "server"
                    }
                }

                fun requestAppUnlock(force: Boolean = false) {
                    if (appUnlocked) return
                    if (unlockPromptActive && !force) return

                    val authenticators = AppUnlockPolicy.allowedAuthenticators()
                    val canAuthenticate = BiometricManager
                        .from(this@MainActivity)
                        .canAuthenticate(authenticators)

                    if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
                        appLockStatus = when (canAuthenticate) {
                            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                                context.getString(R.string.app_lock_no_biometric_hardware)
                            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                                context.getString(R.string.app_lock_biometric_hw_unavailable)
                            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                                context.getString(R.string.app_lock_no_credential_enrolled)
                            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                                context.getString(R.string.app_lock_security_update_required)
                            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED ->
                                context.getString(R.string.app_lock_unsupported)
                            BiometricManager.BIOMETRIC_STATUS_UNKNOWN ->
                                context.getString(R.string.app_lock_status_unknown)
                            else ->
                                context.getString(R.string.app_lock_unavailable)
                        }
                        return
                    }

                    unlockPromptActive = true
                    appLockStatus = context.getString(R.string.app_lock_waiting_auth)

                    val executor = ContextCompat.getMainExecutor(this@MainActivity)

                    val prompt = BiometricPrompt(
                        this@MainActivity,
                        executor,
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(
                                result: BiometricPrompt.AuthenticationResult
                            ) {
                                unlockPromptActive = false
                                appUnlocked = true
                                appLockStatus = ""
                            }

                            override fun onAuthenticationError(
                                errorCode: Int,
                                errString: CharSequence
                            ) {
                                unlockPromptActive = false
                                appLockStatus = context.getString(R.string.app_lock_cancelled_or_failed, errString)
                            }

                            override fun onAuthenticationFailed() {
                                appLockStatus = context.getString(R.string.app_lock_auth_failed)
                            }
                        }
                    )

                    val promptBuilder = BiometricPrompt.PromptInfo.Builder()
                        .setTitle(context.getString(R.string.app_lock_prompt_title))
                        .setSubtitle(context.getString(R.string.app_lock_prompt_subtitle))
                        .setAllowedAuthenticators(authenticators)

                    if (!AppUnlockPolicy.allowsDeviceCredential(authenticators)) {
                        promptBuilder.setNegativeButtonText(context.getString(R.string.cancel))
                    }

                    prompt.authenticate(promptBuilder.build())
                }

                LaunchedEffect(Unit) {
                    val state = tokenStore.getAuthStateOnce()
                    baseUrl = state.baseUrl
                    isAdmin = state.role == "admin"
                    screen = if (state.isLoggedIn) "files" else "server"
                    authLoaded = true
                }

                LaunchedEffect(authLoaded, screen, appUnlocked) {
                    if (authLoaded && (screen == "files" || screen == "admin" || screen == "contacts") && !appUnlocked) {
                        requestAppUnlock()
                    }
                }
                LaunchedEffect(unlockPromptActive, appUnlocked) {
                    if (unlockPromptActive && !appUnlocked) {
                        kotlinx.coroutines.delay(30_000L)

                        if (unlockPromptActive && !appUnlocked) {
                            unlockPromptActive = false
                            appLockStatus = context.getString(R.string.app_lock_timeout)
                        }
                    }
                }
                // PQNAS_ANDROID_BACK_NAV_V1:
                // Handle Android's system Back button for the app-level screens.
                // Without this, screens stored as plain state can let the Activity finish
                // instead of returning to the previous DNA-Nexus screen.
                BackHandler(
                    enabled = authLoaded && screen != "server" && screen != "files"
                ) {
                    screen = when (screen) {
                        "pair_confirm" -> "scan_pair"
                        "scan_pair" -> "server"
                        "contacts" -> "files"
                        "admin" -> "files"
                        else -> "files"
                    }
                }

                when (screen) {
                    "server" -> ServerSetupScreen(
                        onScanPair = { url ->
                            scope.launch {
                                tokenStore.saveBaseUrl(url)
                                baseUrl = url
                                appUnlocked = false
                                screen = "scan_pair"
                            }
                        }
                    )

                    "scan_pair" -> ScanPairQrScreen(
                        onParsed = { payload ->
                            pairPayload = payload
                            screen = "pair_confirm"
                        },
                        onBack = {
                            screen = "server"
                        }
                    )

                    "pair_confirm" -> {
                        val payload = pairPayload
                        if (payload == null) {
                            screen = "server"
                        } else {
                            PairConfirmScreen(
                                payload = payload,
                                configuredBaseUrl = baseUrl,
                                authRepository = authRepository,
                                onPaired = {
                                    scope.launch {
                                        val s = tokenStore.getAuthStateOnce()
                                        baseUrl = s.baseUrl
                                        isAdmin = s.role == "admin"

                                        // Pairing just completed successfully, so do not immediately
                                        // force a second unlock prompt in the same foreground session.
                                        appUnlocked = true
                                        appLockStatus = ""
                                        screen = "files"
                                    }
                                },
                                onBack = {
                                    screen = "scan_pair"
                                }
                            )
                        }
                    }


                    "contacts" -> {
                        if (!appUnlocked) {
                            AppLockScreen(
                                status = appLockStatus,
                                onUnlock = {
                                    requestAppUnlock(force = true)
                                },
                                onLogout = {
                                    logoutToServerScreen()
                                }
                            )
                        } else {
                            val contactsRepository = remember(tokenStore, baseUrl) {
                                ContactsRepository(
                                    ApiFactory.createContactsApi(
                                        baseUrl = baseUrl,
                                        tokenStore = tokenStore
                                    )
                                )
                            }

                            ContactsScreen(
                                repository = contactsRepository,
                                onClose = {
                                    screen = "files"
                                }
                            )
                        }
                    }

                    "admin" -> {
                        if (!appUnlocked) {
                            AppLockScreen(
                                status = appLockStatus,
                                onUnlock = {
                                    requestAppUnlock(force = true)
                                },
                                onLogout = {
                                    logoutToServerScreen()
                                }
                            )
                        } else {
                            val adminRepository = remember(tokenStore, baseUrl) {
                                AdminRepository(
                                    ApiFactory.createAdminApi(
                                        baseUrl = baseUrl,
                                        tokenStore = tokenStore
                                    )
                                )
                            }

                            AdminScreen(
                                repository = adminRepository,
                                onBack = {
                                    screen = "files"
                                }
                            )
                        }
                    }
                    "files" -> {
                        if (!appUnlocked) {
                            AppLockScreen(
                                status = appLockStatus,
                                onUnlock = {
                                    requestAppUnlock(force = true)
                                },
                                onLogout = {
                                    logoutToServerScreen()
                                }
                            )
                        } else {
                            val filesRepository = remember(tokenStore, baseUrl) {
                                FilesRepository(
                                    tokenStore = tokenStore,
                                    baseUrlProvider = { baseUrl }
                                )
                            }

                            FilesScreen(
                                filesRepository = filesRepository,
                                appTheme = appTheme,
                                onAppThemeChange = { nextTheme ->
                                    appTheme = nextTheme
                                    themeStore.saveTheme(nextTheme)
                                },
                                appLanguage = appLanguage,
                                onAppLanguageChange = { nextLanguage ->
                                    languageStore.saveLanguage(nextLanguage)
                                    appLanguage = nextLanguage
                                    this@MainActivity.recreate()
                                },
                                onLogout = {
                                    logoutToServerScreen()
                                },
                                onOpenContacts = {
                                    screen = "contacts"
                                },
                                onOpenAdmin = if (isAdmin) {
                                    { screen = "admin" }
                                } else null,
                                onBeforeExternalPicker = {
                                    externalPickerLaunchedAtMs.longValue = System.currentTimeMillis()
                                },
                                incomingShareManifestPath = incomingShareManifestPath,
                                incomingShareNonce = incomingShareNonce,
                                onIncomingShareConsumed = {
                                    incomingShareManifestPath = null
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
