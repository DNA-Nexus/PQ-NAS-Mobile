package com.pqnas.mobile.ui.settings

import androidx.core.content.edit
import android.content.Context
import android.content.res.Configuration
import java.util.Locale

enum class PqnasAppLanguage(val id: String) {
    System("system"),
    English("en"),
    Finnish("fi"),
    SimplifiedChinese("zh-CN"),
    Swedish("sv"),
    Ukrainian("uk"),
    German("de"),
    Estonian("et"),
    Polish("pl"),
    Spanish("es"),
    French("fr"),
    Italian("it"),
    Turkish("tr");

    companion object {
        fun fromId(id: String?): PqnasAppLanguage {
            return values().firstOrNull { it.id == id } ?: System
        }
    }
}

class PqnasAppLanguageStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadLanguage(): PqnasAppLanguage {
        return PqnasAppLanguage.fromId(prefs.getString(KEY_LANGUAGE, PqnasAppLanguage.System.id))
    }

    fun saveLanguage(language: PqnasAppLanguage) {
        prefs.edit {
            putString(KEY_LANGUAGE, language.id)
        }
    }

    companion object {
        private const val PREFS_NAME = "pqnas_app_language"
        private const val KEY_LANGUAGE = "language"

        fun wrapContext(base: Context): Context {
            val prefs = base.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val selected = PqnasAppLanguage.fromId(prefs.getString(KEY_LANGUAGE, PqnasAppLanguage.System.id))

            if (selected == PqnasAppLanguage.System) {
                return base
            }

            val locale = Locale.forLanguageTag(selected.id)
            Locale.setDefault(locale)

            val config = Configuration(base.resources.configuration)
            config.setLocale(locale)

            return base.createConfigurationContext(config)
        }
    }
}
