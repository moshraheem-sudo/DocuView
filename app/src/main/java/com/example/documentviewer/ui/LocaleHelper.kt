package com.example.documentviewer.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.example.documentviewer.data.AppLanguage
import java.util.Locale

@Composable
fun LocalizedAppContainer(
  appLanguage: AppLanguage,
  content: @Composable (isArabic: Boolean) -> Unit
) {
  val baseContext = LocalContext.current
  val systemLocale = baseContext.resources.configuration.locales[0]
  val targetLocale = remember(appLanguage, systemLocale) {
    when (appLanguage) {
      AppLanguage.ARABIC -> Locale("ar")
      AppLanguage.ENGLISH -> Locale("en")
      AppLanguage.SYSTEM -> systemLocale
    }
  }
  val isArabic = targetLocale.language.equals("ar", ignoreCase = true)
  val layoutDirection = if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr

  val localizedContext = remember(baseContext, targetLocale, appLanguage) {
    if (appLanguage == AppLanguage.SYSTEM) {
      baseContext
    } else {
      createLocalizedContext(baseContext, targetLocale)
    }
  }

  CompositionLocalProvider(
    LocalContext provides localizedContext,
    LocalLayoutDirection provides layoutDirection
  ) {
    content(isArabic)
  }
}

fun createLocalizedContext(base: Context, locale: Locale): Context {
  val config = Configuration(base.resources.configuration)
  config.setLocale(locale)
  config.setLayoutDirection(locale)
  return base.createConfigurationContext(config)
}
