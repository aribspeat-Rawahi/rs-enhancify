package com.aribs.rsenhancify.update

import android.content.ActivityNotFoundException
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.aribs.rsenhancify.BuildConfig
import com.aribs.rsenhancify.ui.components.GlassButton
import com.aribs.rsenhancify.ui.components.GlassColors
import com.aribs.rsenhancify.ui.components.GlassOutlineButton
import com.aribs.rsenhancify.ui.components.glass
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

private sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateUiState
    data class NeedsPermission(val info: UpdateInfo, val apk: File) : UpdateUiState
    data class ReadyToInstall(val info: UpdateInfo, val apk: File) : UpdateUiState
    data class Failed(val message: String, val info: UpdateInfo?) : UpdateUiState
}

/**
 * Update host. Checks silently on every app launch (only shows UI if an update exists or a
 * manual check was requested). "Later" just closes it — the prompt comes back next launch.
 * [manualTrigger]: increment to run a visible check ("Check for updates").
 */
@Composable
fun AppUpdatePrompt(manualTrigger: Int = 0) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    fun startCheck(silent: Boolean) {
        state = if (silent) UpdateUiState.Idle else UpdateUiState.Checking
        scope.launch {
            val next = try {
                val info = AppUpdater.fetchNewerVersion()
                when {
                    info != null -> UpdateUiState.Available(info)
                    silent -> UpdateUiState.Idle
                    else -> UpdateUiState.UpToDate
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Offline or server problem: stay quiet on automatic checks
                if (silent) UpdateUiState.Idle
                else UpdateUiState.Failed("Could not check for updates. Please check your internet connection.", null)
            }
            // Don't interrupt a download/install the user already started
            if (state is UpdateUiState.Idle || state is UpdateUiState.Checking) state = next
        }
    }

    fun startDownload(info: UpdateInfo) {
        state = UpdateUiState.Downloading(info, 0f)
        downloadJob = scope.launch {
            state = try {
                val apk = AppUpdater.download(context, info) { p ->
                    if (state is UpdateUiState.Downloading) state = UpdateUiState.Downloading(info, p)
                }
                if (AppUpdater.canInstall(context)) UpdateUiState.ReadyToInstall(info, apk)
                else UpdateUiState.NeedsPermission(info, apk)
            } catch (e: CancellationException) {
                UpdateUiState.Idle
            } catch (e: Exception) {
                UpdateUiState.Failed(e.message ?: "Download failed", info)
            }
        }
    }

    fun install(info: UpdateInfo, apk: File) {
        if (!AppUpdater.canInstall(context)) {
            state = UpdateUiState.NeedsPermission(info, apk)
            return
        }
        state = try {
            AppUpdater.launchInstaller(context, apk)
            UpdateUiState.ReadyToInstall(info, apk) // keep dialog in case the user cancels the installer
        } catch (e: ActivityNotFoundException) {
            UpdateUiState.Failed("No package installer found on this phone.", info)
        } catch (e: Exception) {
            UpdateUiState.Failed(e.message ?: "Could not start the installer", info)
        }
    }

    LaunchedEffect(Unit) {
        AppUpdater.cleanup(context) // leftover APK from a previous (installed) update
        startCheck(silent = true)
    }
    LaunchedEffect(manualTrigger) { if (manualTrigger > 0) startCheck(silent = false) }

    // Coming back from the "Install unknown apps" settings screen: continue automatically
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val s = state
        if (s is UpdateUiState.NeedsPermission && AppUpdater.canInstall(context)) install(s.info, s.apk)
    }

    val close = { state = UpdateUiState.Idle }

    when (val s = state) {
        UpdateUiState.Idle -> Unit

        UpdateUiState.Checking -> GlassDialog(Icons.Rounded.SystemUpdate, "Checking for updates…", onDismiss = close) {
            Progress(null)
        }

        UpdateUiState.UpToDate -> GlassDialog(
            Icons.Rounded.SystemUpdate, "You're up to date", onDismiss = close,
            secondary = "OK" to close
        ) {
            Body("Rs Enhancify ${BuildConfig.VERSION_NAME} is the latest version.")
        }

        is UpdateUiState.Available -> GlassDialog(
            Icons.Rounded.SystemUpdate, "Update available", onDismiss = close,
            primary = Triple("Update now", Icons.Rounded.Download) { startDownload(s.info) },
            secondary = "Later" to close
        ) {
            Body("Version ${s.info.versionName} is ready (you have ${BuildConfig.VERSION_NAME}).")
            if (s.info.sizeBytes > 0) Body("Download size: ${"%.1f".format(s.info.sizeBytes / 1_048_576.0)} MB")
            if (s.info.notes.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("What's new", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Column(Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState())) {
                    Body(s.info.notes.take(800))
                }
            }
            Body("Downloads inside the app. Your photos and files are not affected.")
        }

        is UpdateUiState.Downloading -> GlassDialog(
            Icons.Rounded.Download, "Downloading update…",
            dismissible = false, onDismiss = {},
            secondary = "Cancel" to { downloadJob?.cancel(); Unit }
        ) {
            Body("Version ${s.info.versionName}")
            Progress(if (s.progress >= 0f) s.progress else null)
        }

        is UpdateUiState.NeedsPermission -> GlassDialog(
            Icons.Rounded.Settings, "Allow app updates", onDismiss = close,
            primary = Triple("Open settings", Icons.Rounded.Settings) {
                try {
                    AppUpdater.openInstallPermissionSettings(context)
                } catch (e: Exception) {
                    state = UpdateUiState.Failed("Could not open settings", s.info)
                }
            },
            secondary = "Later" to close
        ) {
            Body("One-time step: Android needs your permission for Rs Enhancify to install its own updates.")
            Body("Turn on \"Allow from this source\", then come back — installation continues automatically.")
        }

        is UpdateUiState.ReadyToInstall -> GlassDialog(
            Icons.Rounded.InstallMobile, "Ready to install", onDismiss = close,
            primary = Triple("Install", Icons.Rounded.InstallMobile) { install(s.info, s.apk) },
            secondary = "Later" to close
        ) {
            Body("Version ${s.info.versionName} has been downloaded and verified.")
        }

        is UpdateUiState.Failed -> GlassDialog(
            Icons.Rounded.SystemUpdate, "Update failed", onDismiss = close,
            primary = s.info?.let { info -> Triple("Retry", Icons.Rounded.Download) { startDownload(info) } },
            secondary = "Close" to close
        ) {
            Body(s.message)
        }
    }
}

@Composable
private fun Body(text: String) {
    Text(text, color = GlassColors.TextSecondary, fontSize = 14.sp, lineHeight = 20.sp)
}

@Composable
private fun Progress(value: Float?) {
    Column {
        Spacer(Modifier.height(6.dp))
        val mod = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(8.dp))
        if (value != null) {
            LinearProgressIndicator(
                progress = { value }, modifier = mod,
                color = GlassColors.Cyan, trackColor = Color(0x22FFFFFF), strokeCap = StrokeCap.Round
            )
            Spacer(Modifier.height(6.dp))
            Text("${(value * 100).toInt()}%", color = GlassColors.TextSecondary, fontSize = 13.sp)
        } else {
            LinearProgressIndicator(
                modifier = mod, color = GlassColors.Cyan, trackColor = Color(0x22FFFFFF), strokeCap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun GlassDialog(
    icon: ImageVector,
    title: String,
    onDismiss: () -> Unit,
    dismissible: Boolean = true,
    primary: Triple<String, ImageVector, () -> Unit>? = null,
    secondary: Pair<String, () -> Unit>? = null,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = { if (dismissible) onDismiss() },
        properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = dismissible)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(listOf(Color(0xF21E1650), Color(0xF20E1A3A)))
                )
                .glass(RoundedCornerShape(28.dp), strong = true)
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(GlassColors.Violet, GlassColors.Cyan))),
                    contentAlignment = Alignment.Center
                ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
                Spacer(Modifier.width(12.dp))
                Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
            content()
            if (primary != null || secondary != null) {
                Spacer(Modifier.height(6.dp))
                Row {
                    if (secondary != null) {
                        GlassOutlineButton(secondary.first, Modifier.weight(1f), onClick = secondary.second)
                    }
                    if (primary != null && secondary != null) Spacer(Modifier.width(10.dp))
                    if (primary != null) {
                        GlassButton(primary.first, Modifier.weight(1.3f), primary.second, onClick = primary.third)
                    }
                }
            }
        }
    }
}
