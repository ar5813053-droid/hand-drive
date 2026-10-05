package com.handdrive.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.handdrive.R
import com.handdrive.domain.CameraFacing
import com.handdrive.settings.SettingsRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = rememberSettingsRepo(context)
    val settings by repo.settingsFlow.collectAsStateWithLifecycle(
        initialValue = com.handdrive.settings.AppSettings.DEFAULT
    )
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            SectionHeader("Camera")
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = settings.cameraFacing == CameraFacing.FRONT,
                    onClick = {
                        scope.launch {
                            repo.updateSettings { it.copy(cameraFacing = CameraFacing.FRONT) }
                        }
                    },
                    label = { Text("Front") }
                )
                Spacer(Modifier.padding(8.dp))
                FilterChip(
                    selected = settings.cameraFacing == CameraFacing.REAR,
                    onClick = {
                        scope.launch {
                            repo.updateSettings { it.copy(cameraFacing = CameraFacing.REAR) }
                        }
                    },
                    label = { Text("Rear") }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionHeader("Steering")

            SliderSetting(
                title = "Sensitivity",
                value = settings.sensitivity,
                range = 0.5f..2.0f,
                label = "%.1f".format(settings.sensitivity)
            ) { v ->
                scope.launch { repo.updateSettings { it.copy(sensitivity = v) } }
            }
            SliderSetting(
                title = "Smoothing",
                value = settings.smoothing,
                range = 0f..0.9f,
                label = "%.2f".format(settings.smoothing)
            ) { v ->
                scope.launch { repo.updateSettings { it.copy(smoothing = v) } }
            }
            SliderSetting(
                title = "Dead zone",
                value = settings.deadZone,
                range = 0f..0.3f,
                label = "%.2f".format(settings.deadZone)
            ) { v ->
                scope.launch { repo.updateSettings { it.copy(deadZone = v) } }
            }
            SliderSetting(
                title = "Max angle (°)",
                value = settings.maxAngleDegrees,
                range = 30f..120f,
                label = "%.0f°".format(settings.maxAngleDegrees)
            ) { v ->
                scope.launch { repo.updateSettings { it.copy(maxAngleDegrees = v) } }
            }
            SwitchSetting(
                title = "Invert steering",
                subtitle = if (settings.invertSteering) "Natural (hand right → steer right)"
                else "Default inverted (hand right → steer left)",
                checked = settings.invertSteering
            ) { checked ->
                scope.launch { repo.updateSettings { it.copy(invertSteering = checked) } }
            }
            SwitchSetting(
                title = "Auto-center",
                subtitle = "Return toward neutral near center / on track loss",
                checked = settings.autoCenterEnabled
            ) { checked ->
                scope.launch { repo.updateSettings { it.copy(autoCenterEnabled = checked) } }
            }
            SliderSetting(
                title = "Auto-center speed",
                value = settings.autoCenterSpeed,
                range = 0.02f..0.4f,
                label = "%.2f".format(settings.autoCenterSpeed)
            ) { v ->
                scope.launch { repo.updateSettings { it.copy(autoCenterSpeed = v) } }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionHeader("Gestures")

            SwitchSetting(
                title = "Open-palm brake",
                subtitle = "Open palm → brake ON",
                checked = settings.openPalmBrakeEnabled
            ) { checked ->
                scope.launch { repo.updateSettings { it.copy(openPalmBrakeEnabled = checked) } }
            }
            SliderSetting(
                title = "Gesture sensitivity",
                value = settings.gestureSensitivity,
                range = 0.3f..0.9f,
                label = "%.2f".format(settings.gestureSensitivity)
            ) { v ->
                scope.launch { repo.updateSettings { it.copy(gestureSensitivity = v) } }
            }
            SliderSetting(
                title = "Detection delay (ms)",
                value = settings.detectionDelayMs.toFloat(),
                range = 40f..400f,
                label = "${settings.detectionDelayMs} ms"
            ) { v ->
                scope.launch { repo.updateSettings { it.copy(detectionDelayMs = v.toLong()) } }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Settings are saved locally. No internet required.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun rememberSettingsRepo(context: android.content.Context): SettingsRepository {
    return androidx.compose.runtime.remember { SettingsRepository(context) }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun SliderSetting(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    label: String,
    onChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun SwitchSetting(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
