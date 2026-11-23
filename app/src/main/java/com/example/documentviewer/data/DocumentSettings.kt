package com.example.documentviewer.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.docuSettingsStore by preferencesDataStore(name = "docuview_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class AppLanguage { SYSTEM, ARABIC, ENGLISH }

data class ReaderSettingsState(
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val appLanguage: AppLanguage = AppLanguage.SYSTEM,
  val defaultNightReader: Boolean = false,
  val continuousPdfScroll: Boolean = true,
  val keepScreenOn: Boolean = true,
  val autoCheckUpdates: Boolean = true
)

class DocumentSettings(private val context: Context) {

  private object Keys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val APP_LANGUAGE = stringPreferencesKey("app_language")
    val NIGHT_READER = booleanPreferencesKey("night_reader")
    val CONTINUOUS_PDF = booleanPreferencesKey("continuous_pdf")
    val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    val AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates")
  }

  val settingsFlow: Flow<ReaderSettingsState> = context.docuSettingsStore.data.map { prefs ->
    ReaderSettingsState(
      themeMode = runCatching {
        ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
      }.getOrDefault(ThemeMode.SYSTEM),
      appLanguage = runCatching {
        AppLanguage.valueOf(prefs[Keys.APP_LANGUAGE] ?: AppLanguage.SYSTEM.name)
      }.getOrDefault(AppLanguage.SYSTEM),
      defaultNightReader = prefs[Keys.NIGHT_READER] ?: false,
      continuousPdfScroll = prefs[Keys.CONTINUOUS_PDF] ?: true,
      keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: true,
      autoCheckUpdates = prefs[Keys.AUTO_CHECK_UPDATES] ?: true
    )
  }

  suspend fun setThemeMode(mode: ThemeMode) {
    context.docuSettingsStore.edit { it[Keys.THEME_MODE] = mode.name }
  }

  suspend fun setAppLanguage(language: AppLanguage) {
    context.docuSettingsStore.edit { it[Keys.APP_LANGUAGE] = language.name }
  }

  suspend fun setDefaultNightReader(enabled: Boolean) {
    context.docuSettingsStore.edit { it[Keys.NIGHT_READER] = enabled }
  }

  suspend fun setContinuousPdfScroll(enabled: Boolean) {
    context.docuSettingsStore.edit { it[Keys.CONTINUOUS_PDF] = enabled }
  }

  suspend fun setKeepScreenOn(enabled: Boolean) {
    context.docuSettingsStore.edit { it[Keys.KEEP_SCREEN_ON] = enabled }
  }

  suspend fun setAutoCheckUpdates(enabled: Boolean) {
    context.docuSettingsStore.edit { it[Keys.AUTO_CHECK_UPDATES] = enabled }
  }
}
