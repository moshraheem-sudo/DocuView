package com.example.documentviewer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.documentviewer.data.AppLanguage
import com.example.documentviewer.data.ReaderSettingsState
import com.example.documentviewer.data.ThemeMode
import com.example.documentviewer.model.DocumentFormat

@Composable
fun SettingsTabContent(
  settings: ReaderSettingsState,
  onThemeChange: (ThemeMode) -> Unit,
  onLanguageChange: (AppLanguage) -> Unit,
  onContinuousPdfChange: (Boolean) -> Unit,
  onKeepScreenOnChange: (Boolean) -> Unit,
  onClearHistory: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 16.dp, vertical = 8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Surface(
      shape = RoundedCornerShape(16.dp),
      color = MaterialTheme.colorScheme.surface,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
      tonalElevation = 1.dp,
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Text(
          text = stringResource(R.string.settings_title),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth()
        )

        HorizontalDivider()

        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Text(
            text = stringResource(R.string.settings_app_theme),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
          ) {
            FilterChip(
              selected = settings.themeMode == ThemeMode.SYSTEM,
              onClick = { onThemeChange(ThemeMode.SYSTEM) },
              label = { Text(stringResource(R.string.settings_theme_system), maxLines = 1, softWrap = false) }
            )
            FilterChip(
              selected = settings.themeMode == ThemeMode.LIGHT,
              onClick = { onThemeChange(ThemeMode.LIGHT) },
              label = { Text(stringResource(R.string.settings_theme_light), maxLines = 1, softWrap = false) }
            )
            FilterChip(
              selected = settings.themeMode == ThemeMode.DARK,
              onClick = { onThemeChange(ThemeMode.DARK) },
              label = { Text(stringResource(R.string.settings_theme_dark), maxLines = 1, softWrap = false) }
            )
          }
        }

        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Text(
            text = stringResource(R.string.settings_language),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
          ) {
            FilterChip(
              selected = settings.appLanguage == AppLanguage.SYSTEM,
              onClick = { onLanguageChange(AppLanguage.SYSTEM) },
              label = { Text(stringResource(R.string.settings_lang_system), maxLines = 1, softWrap = false) }
            )
            FilterChip(
              selected = settings.appLanguage == AppLanguage.ARABIC,
              onClick = { onLanguageChange(AppLanguage.ARABIC) },
              label = { Text(stringResource(R.string.settings_lang_ar), maxLines = 1, softWrap = false) }
            )
            FilterChip(
              selected = settings.appLanguage == AppLanguage.ENGLISH,
              onClick = { onLanguageChange(AppLanguage.ENGLISH) },
              label = { Text(stringResource(R.string.settings_lang_en), maxLines = 1, softWrap = false) }
            )
          }
        }

        HorizontalDivider()

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = stringResource(R.string.settings_continuous_pdf),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
          )
          Switch(
            checked = settings.continuousPdfScroll,
            onCheckedChange = onContinuousPdfChange
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = stringResource(R.string.settings_keep_screen_on),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
          )
          Switch(
            checked = settings.keepScreenOn,
            onCheckedChange = onKeepScreenOnChange
          )
        }

        Box(
          modifier = Modifier.fillMaxWidth(),
          contentAlignment = Alignment.Center
        ) {
          OutlinedButton(
            onClick = onClearHistory,
            modifier = Modifier
              .fillMaxWidth(0.5f)
              .minimumInteractiveComponentSize()
              .testTag("clear_history_button"),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
          ) {
            Icon(Icons.Outlined.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
              text = stringResource(R.string.settings_clear_history),
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              textAlign = TextAlign.Center
            )
          }
        }
      }
    }

    AboutAppDeveloperCard()

    Spacer(Modifier.height(84.dp))
  }
}

@Composable
fun AboutAppDeveloperCard(modifier: Modifier = Modifier) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
    tonalElevation = 2.dp,
    modifier = modifier
      .fillMaxWidth()
      .testTag("about_developer_card")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 14.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Text(
        text = stringResource(R.string.about_app_card_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
      )
      HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "${stringResource(R.string.app_name_label)}: ",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          text = stringResource(R.string.app_name),
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          softWrap = false
        )
        Spacer(Modifier.width(10.dp))
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        ) {
          Text(
            text = stringResource(R.string.version_value),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
          )
        }
      }

      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        Text(
          text = stringResource(R.string.developer_label),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center
        )
        Text(
          text = stringResource(R.string.developer_name),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
          textAlign = TextAlign.Center,
          maxLines = 1,
          softWrap = false,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}

@Composable
fun FormatBadge(format: DocumentFormat, modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(8.dp),
    color = format.accentColor
  ) {
    Text(
      text = format.label,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
      color = Color.White,
      maxLines = 1,
      softWrap = false,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(
  settings: ReaderSettingsState,
  onThemeChange: (ThemeMode) -> Unit,
  onLanguageChange: (AppLanguage) -> Unit,
  onContinuousPdfChange: (Boolean) -> Unit,
  onKeepScreenOnChange: (Boolean) -> Unit,
  onClearHistory: () -> Unit,
  onDismiss: () -> Unit
) {
  ModalBottomSheet(onDismissRequest = onDismiss) {
    SettingsTabContent(
      settings = settings,
      onThemeChange = onThemeChange,
      onLanguageChange = onLanguageChange,
      onContinuousPdfChange = onContinuousPdfChange,
      onKeepScreenOnChange = onKeepScreenOnChange,
      onClearHistory = {
        onClearHistory()
        onDismiss()
      }
    )
  }
}
