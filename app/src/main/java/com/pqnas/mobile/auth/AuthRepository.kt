package com.pqnas.mobile.auth

import android.os.Build
import com.pqnas.mobile.BuildConfig
import com.pqnas.mobile.api.ApiFactory
import com.pqnas.mobile.api.AuthApi
import com.pqnas.mobile.api.PairConsumeRequest
import com.pqnas.mobile.api.RevokeRefreshTokenRequest

class AuthRepository(
    private val tokenStore: TokenStore
) {
    suspend fun saveBaseUrl(baseUrl: String) {
        tokenStore.saveBaseUrl(baseUrl)
    }

    suspend fun consumePair(
        baseUrl: String,
        pairToken: String,
        tlsPinSha256: String,
        deviceName: String = "DNA-Nexus Android",
        serverDisplayNameFallback: String = ""
    ): Boolean {
        val api = ApiFactory.createAuthApi(
            baseUrl = baseUrl,
            tlsPinSha256 = tlsPinSha256
        )

        val manufacturer = Build.MANUFACTURER ?: ""
        val model = Build.MODEL ?: ""
        val osVersion = "Android ${Build.VERSION.RELEASE ?: ""}".trim()
        val resp = api.consumePair(
            PairConsumeRequest(
                pair_token = pairToken,
                device_name = deviceName,
                platform = "android",
                app_version = BuildConfig.VERSION_NAME,
                device_model = model,
                device_manufacturer = manufacturer,
                os_version = osVersion
            )
        )

        if (!resp.ok ||
            resp.access_token.isBlank() ||
            resp.refresh_token.isBlank() ||
            resp.device_id.isBlank()
        ) {
            return false
        }

        val serverDisplayName = resolveServerDisplayName(
            api = api,
            fallback = serverDisplayNameFallback
        )

        tokenStore.saveTokens(
            accessToken = resp.access_token,
            refreshToken = resp.refresh_token,
            deviceId = resp.device_id,
            fingerprintHex = resp.fingerprint_hex,
            role = resp.role,
            baseUrl = baseUrl,
            tlsPinSha256 = tlsPinSha256,
            serverDisplayName = serverDisplayName
        )

        return true
    }

    /**
     * Revoke refresh token on the server, then wipe local auth state.
     * Best-effort: local state is always cleared even if the server call fails.
     */
    suspend fun previewServerDisplayName(
        baseUrl: String,
        tlsPinSha256: String,
        fallback: String
    ): String {
        val api = ApiFactory.createAuthApi(
            baseUrl = baseUrl,
            tlsPinSha256 = tlsPinSha256
        )

        return resolveServerDisplayName(
            api = api,
            fallback = fallback
        )
    }

    private suspend fun resolveServerDisplayName(
        api: AuthApi,
        fallback: String
    ): String {
        val fromServer = runCatching {
            val branding = api.publicBranding()
            val preferred = branding.mobile?.display_name
                ?.takeIf { it.isNotBlank() }
                ?: branding.product_name.ifBlank { branding.product_short_name }

            cleanServerDisplayName(preferred)
        }.getOrDefault("")

        return fromServer.ifBlank {
            cleanServerDisplayName(fallback)
        }
    }

    private fun cleanServerDisplayName(raw: String?): String {
        // Runtime branding is untrusted display metadata from the connected
        // server. Strip control characters so it cannot spoof layout/log output.
        val cleaned = raw.orEmpty()
            .map { ch ->
                if (ch.code < 0x20 || ch.code == 0x7f) ' ' else ch
            }
            .joinToString("")
            .trim()

        return cleaned.take(80)
    }

    suspend fun logout() {
        try {
            val state = tokenStore.getAuthStateOnce()
            if (state.refreshToken.isNotBlank() &&
                state.deviceId.isNotBlank() &&
                state.baseUrl.isNotBlank() &&
                state.tlsPinSha256.isNotBlank()
            ) {
                ApiFactory.createAuthApi(
                    baseUrl = state.baseUrl,
                    tlsPinSha256 = state.tlsPinSha256
                ).revokeRefreshToken(
                    RevokeRefreshTokenRequest(
                        refresh_token = state.refreshToken,
                        device_id = state.deviceId
                    )
                )
            }
        } catch (_: Exception) {
            // Best-effort: server may be unreachable
        }
        tokenStore.clearAll()
        tokenStore.clearWorkspaceEditorSession()
        tokenStore.clearFileListCache()
    }
}
