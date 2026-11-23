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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.documentviewer.data.AppLanguage
import com.example.documentviewer.data.ReaderSettingsState
import com.example.documentviewer.data.ThemeMode
import com.example.documentviewer.model.DocumentFormat
import com.example.documentviewer.update.AppUpdateManager
import com.example.documentviewer.update.AppUpdateState
import com.example.documentviewer.update.ReleaseInfo
import com.example.ui.theme.SupportFullGreen

@Composable
fun SettingsTabContent(
  settings: ReaderSettingsState,
  onThemeChange: (ThemeMode) -> Unit,
  onLanguageChange: (AppLanguage) -> Unit,
  onContinuousPdfChange: (Boolean) -> Unit,
  onKeepScreenOnChange: (Boolean) -> Unit,
  onClearHistory: () -> Unit,
  modifier: Modifier = Modifier,
  updateState: AppUpdateState = AppUpdateState.Idle,
  onCheckForUpdates: () -> Unit = {},
  onCancelUpdate: () -> Unit = {},
  onRetryUpdateDownload: (ReleaseInfo) -> Unit = {},
  onInstallUpdate: () -> Unit = {},
  onDismissUpdate: () -> Unit = {},
  onAutoCheckUpdatesChange: (Boolean) -> Unit = {},
  showUpdateSection: Boolean = true
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 16.dp, vertical = 8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    if (showUpdateSection) {
      AppUpdateSettingsCard(
        updateState = updateState,
        autoCheckEnabled = settings.autoCheckUpdates,
        onAutoCheckChange = onAutoCheckUpdatesChange,
        onCheckForUpdates = onCheckForUpdates,
        onCancelUpdate = onCancelUpdate,
        onRetryDownload = onRetryUpdateDownload,
        onInstallUpdate = onInstallUpdate,
        onDismissUpdate = onDismissUpdate
      )
    }

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
fun AppUpdateSettingsCard(
  updateState: AppUpdateState,
  autoCheckEnabled: Boolean,
  onAutoCheckChange: (Boolean) -> Unit,
  onCheckForUpdates: () -> Unit,
  onCancelUpdate: () -> Unit,
  onRetryDownload: (ReleaseInfo) -> Unit,
  onInstallUpdate: () -> Unit,
  onDismissUpdate: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
    tonalElevation = 2.dp,
    modifier = modifier
      .fillMaxWidth()
      .testTag("app_update_card")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.primaryContainer,
          modifier = Modifier.size(42.dp)
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Outlined.SystemUpdate,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(22.dp)
            )
          }
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.update_card_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = stringResource(R.string.update_card_subtitle),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.secondaryContainer
        ) {
          Text(
            text = "v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = stringResource(R.string.update_auto_check),
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.weight(1f)
        )
        Switch(
          checked = autoCheckEnabled,
          onCheckedChange = onAutoCheckChange,
          modifier = Modifier.testTag("auto_check_updates_switch")
        )
      }

      HorizontalDivider()

      when (updateState) {
        AppUpdateState.Idle -> {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              text = stringResource(R.string.update_current_version_label, BuildConfig.VERSION_NAME),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.weight(1f)
            )
            Button(
              onClick = onCheckForUpdates,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("check_updates_button")
            ) {
              Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text(stringResource(R.string.update_check_now), maxLines = 1, softWrap = false)
            }
          }
        }

        AppUpdateState.Checking -> {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = stringResource(R.string.update_checking),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.Medium
            )
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
              OutlinedButton(
                onClick = onCancelUpdate,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("cancel_update_button")
              ) {
                Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.update_cancel_button))
              }
            }
          }
        }

        is AppUpdateState.UpToDate -> {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Outlined.CheckCircle,
              contentDescription = null,
              tint = SupportFullGreen,
              modifier = Modifier.size(20.dp)
            )
            Text(
              text = stringResource(R.string.update_up_to_date, BuildConfig.VERSION_NAME),
              style = MaterialTheme.typography.bodyMedium,
              color = SupportFullGreen,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.weight(1f)
            )
            OutlinedButton(
              onClick = onCheckForUpdates,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("check_updates_button")
            ) {
              Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            }
          }
        }

        is AppUpdateState.UpdateAvailable -> {
          UpdateDownloadingBody(
            release = updateState.release,
            downloadedBytes = 0L,
            totalBytes = updateState.release.sizeBytes,
            onCancelUpdate = onCancelUpdate
          )
        }

        is AppUpdateState.Downloading -> {
          UpdateDownloadingBody(
            release = updateState.release,
            downloadedBytes = updateState.downloadedBytes,
            totalBytes = updateState.totalBytes,
            onCancelUpdate = onCancelUpdate
          )
        }

        is AppUpdateState.ReadyToInstall -> {
          val sizeLabel = AppUpdateManager.formatSingleMegabytes(
            context,
            updateState.release.sizeBytes.takeIf { it > 0L }
              ?: java.io.File(updateState.apkFilePath).length()
          )
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(
                imageVector = Icons.Outlined.InstallMobile,
                contentDescription = null,
                tint = SupportFullGreen,
                modifier = Modifier.size(22.dp)
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = stringResource(R.string.update_ready_title, updateState.release.versionName),
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = SupportFullGreen
                )
                Text(
                  text = if (updateState.needsInstallPermission) {
                    stringResource(R.string.update_needs_permission_desc)
                  } else {
                    "${stringResource(R.string.update_ready_desc)} ($sizeLabel)"
                  },
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
            if (updateState.release.notes.isNotBlank()) {
              Text(
                text = updateState.release.notes,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
              )
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
              TextButton(
                onClick = onDismissUpdate,
                modifier = Modifier.minimumInteractiveComponentSize()
              ) {
                Text(stringResource(R.string.update_dismiss))
              }
              Button(
                onClick = onInstallUpdate,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("install_update_button")
              ) {
                Icon(Icons.Outlined.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                  text = stringResource(
                    if (updateState.needsInstallPermission) {
                      R.string.update_allow_install_button
                    } else {
                      R.string.update_install_button
                    }
                  ),
                  maxLines = 1,
                  softWrap = false
                )
              }
            }
          }
        }

        is AppUpdateState.Cancelled -> {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = stringResource(R.string.update_cancelled_msg, updateState.release.versionName),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
              TextButton(
                onClick = onDismissUpdate,
                modifier = Modifier.minimumInteractiveComponentSize()
              ) {
                Text(stringResource(R.string.update_dismiss))
              }
              Button(
                onClick = { onRetryDownload(updateState.release) },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("retry_update_download_button")
              ) {
                Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.update_retry_download))
              }
            }
          }
        }

        is AppUpdateState.Error -> {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
              )
              Text(
                text = updateState.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
              )
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
              TextButton(
                onClick = onDismissUpdate,
                modifier = Modifier.minimumInteractiveComponentSize()
              ) {
                Text(stringResource(R.string.update_dismiss))
              }
              Button(
                onClick = {
                  val rel = updateState.release
                  if (rel != null) onRetryDownload(rel) else onCheckForUpdates()
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("check_updates_button")
              ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.retry))
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun UpdateDownloadingBody(
  release: ReleaseInfo,
  downloadedBytes: Long,
  totalBytes: Long,
  onCancelUpdate: () -> Unit
) {
  val context = LocalContext.current
  val progressMbText = AppUpdateManager.formatProgressMegabytes(context, downloadedBytes, totalBytes)
  val percent = if (totalBytes > 0L) {
    ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
  } else {
    -1
  }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = stringResource(R.string.update_available_banner_title, release.versionName),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary
        )
        Text(
          text = stringResource(R.string.update_downloading_banner_subtitle),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      if (percent >= 0) {
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.primaryContainer
        ) {
          Text(
            text = "$percent%",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
          )
        }
      }
    }

    if (totalBytes > 0L) {
      LinearProgressIndicator(
        progress = { (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth()
      )
    } else {
      LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Text(
        text = progressMbText,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.testTag("update_mb_progress_text")
      )
      OutlinedButton(
        onClick = onCancelUpdate,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("cancel_update_button")
      ) {
        Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.update_cancel_button), maxLines = 1, softWrap = false)
      }
    }
  }
}

@Composable
fun AppUpdateBanner(
  updateState: AppUpdateState,
  onCancelUpdate: () -> Unit,
  onRetryDownload: (ReleaseInfo) -> Unit,
  onInstallUpdate: () -> Unit,
  onDismissUpdate: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val shouldShow = updateState is AppUpdateState.UpdateAvailable ||
    updateState is AppUpdateState.Downloading ||
    updateState is AppUpdateState.ReadyToInstall ||
    updateState is AppUpdateState.Cancelled

  if (!shouldShow) return

  Surface(
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
    tonalElevation = 2.dp,
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp, vertical = 6.dp)
      .testTag("home_update_banner")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      when (updateState) {
        is AppUpdateState.UpdateAvailable -> {
          UpdateDownloadingBody(
            release = updateState.release,
            downloadedBytes = 0L,
            totalBytes = updateState.release.sizeBytes,
            onCancelUpdate = onCancelUpdate
          )
        }
        is AppUpdateState.Downloading -> {
          UpdateDownloadingBody(
            release = updateState.release,
            downloadedBytes = updateState.downloadedBytes,
            totalBytes = updateState.totalBytes,
            onCancelUpdate = onCancelUpdate
          )
        }
        is AppUpdateState.ReadyToInstall -> {
          val sizeLabel = AppUpdateManager.formatSingleMegabytes(
            context,
            updateState.release.sizeBytes.takeIf { it > 0L }
              ?: java.io.File(updateState.apkFilePath).length()
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Outlined.InstallMobile,
              contentDescription = null,
              tint = SupportFullGreen,
              modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = stringResource(R.string.update_ready_title, updateState.release.versionName),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = sizeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Button(
              onClick = onInstallUpdate,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("banner_install_update_button")
            ) {
              Text(
                text = stringResource(
                  if (updateState.needsInstallPermission) R.string.update_allow_install_button
                  else R.string.update_install_button
                ),
                maxLines = 1,
                softWrap = false
              )
            }
            IconButtonCompatClose(onClick = onDismissUpdate)
          }
        }
        is AppUpdateState.Cancelled -> {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Text(
              text = stringResource(R.string.update_cancelled_msg, updateState.release.versionName),
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.weight(1f)
            )
            OutlinedButton(
              onClick = { onRetryDownload(updateState.release) },
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.minimumInteractiveComponentSize()
            ) {
              Text(stringResource(R.string.update_retry_download), maxLines = 1, softWrap = false)
            }
            IconButtonCompatClose(onClick = onDismissUpdate)
          }
        }
        else -> Unit
      }
    }
  }
}

@Composable
private fun IconButtonCompatClose(onClick: () -> Unit) {
  androidx.compose.material3.IconButton(
    onClick = onClick,
    modifier = Modifier.minimumInteractiveComponentSize()
  ) {
    Icon(
      imageVector = Icons.Outlined.Close,
      contentDescription = stringResource(R.string.update_dismiss),
      modifier = Modifier.size(18.dp)
    )
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
            text = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
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
      },
      showUpdateSection = false
    )
  }
}
