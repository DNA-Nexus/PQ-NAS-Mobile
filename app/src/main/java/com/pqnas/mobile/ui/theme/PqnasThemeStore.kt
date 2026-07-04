package com.pqnas.mobile.ui.theme

import androidx.core.content.edit
import android.content.Context

class PqnasThemeStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "pqnas_ui",
        Context.MODE_PRIVATE
    )

    fun loadTheme(): PqnasAppTheme {
        return PqnasAppTheme.fromStorageKey(prefs.getString(KEY, PqnasAppTheme.Dark.storageKey))
    }

    fun saveTheme(theme: PqnasAppTheme) {
        prefs.edit {
            putString(KEY, theme.storageKey)
        }
    }

    private companion object {
        // Keep the same logical setting key as server theme.js / localStorage.
        const val KEY = "pqnas_theme"
    }
}
