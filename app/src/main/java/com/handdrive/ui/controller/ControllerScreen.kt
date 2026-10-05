package com.handdrive.ui.controller

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.handdrive.R
import com.handdrive.controller.ControllerViewModel
import com.handdrive.domain.TrackingState
import com.handdrive.input.AccessibilityStatus
import com.handdrive.input.InputState
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ControllerScreen(
    onBack: () -> Unit,
    onCalibrate: () -> Unit = {},
    viewModel: ControllerViewModel = viewModel()
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val inputStatus by viewModel.inputStatus.collectAsStateWithLifecycle()
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.setPermissionGranted(granted)
        if (granted && previewView != null) {
            viewModel.startController(lifecycleOwner, previewView!!)
        }
    }

    fun ensurePermissionAndStart() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.setPermissionGranted(granted)
        if (granted) {
            previewView?.let { viewModel.startController(lifecycleOwner, it) }
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    DisposableEffect(Unit) {
        viewModel.refreshAccessibilityStatus()
        onDispose { viewModel.stopController() }
    }

    val a11yConnected = inputStatus.accessibility == AccessibilityStatus.CONNECTED

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.controller_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.stopController()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            previewView?.let { viewModel.switchCamera(lifecycleOwner, it) }
                        },
                        enabled = status.permissionGranted
                    ) {
                        Icon(Icons.Default.Cameraswitch, contentDescription = "Switch camera")
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).also {
                            it.scaleType = PreviewView.ScaleType.FILL_CENTER
                            previewView = it
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                if (status.tracking.state == TrackingState.TRACKING &&
                    status.tracking.landmarks.isNotEmpty()
                ) {
                    LandmarkOverlay(status.tracking.landmarks, Modifier.fillMaxSize())
                }
                if (!status.permissionGranted) {
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Camera permission required", color = Color.White)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    onClick = { ensurePermissionAndStart() },
                    enabled = !status.isActive,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Start")
                }
                FilledTonalButton(
                    onClick = { viewModel.stopController() },
                    enabled = status.isActive,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Default.Stop, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Stop")
                }
            }

            Spacer(Modifier.height(10.dp))

            StatusRow("Camera", when {
                !status.permissionGranted -> "NO PERMISSION"
                status.cameraReady -> "${status.cameraFacing.name} · READY"
                status.isActive -> "STARTING…"
                else -> "IDLE"
            })
            StatusRow("Tracking", when (status.tracking.state) {
                TrackingState.TRACKING -> "ACTIVE · ${(status.tracking.confidence * 100).toInt()}%"
                TrackingState.LOW_CONFIDENCE -> "LOW CONFIDENCE"
                TrackingState.LOST -> "LOST"
            })
            StatusRow(
                "Steering",
                String.format("%.0f°  (%.2f)", status.steering.angleDegrees, status.steering.value)
            )
            StatusRow("Brake", if (status.gesture.brakeOn || inputStatus.brakeOn) "ON" else "OFF")
            StatusRow("Throttle", if (inputStatus.throttleOn) "ON" else "OFF")
            StatusRow(
                "Profile",
                activeProfile?.let {
                    val cal = if (it.layout.calibrated) "✓" else "not calibrated"
                    "${it.name} ($cal)"
                } ?: "None"
            )
            if (inputStatus.orientationMismatch) {
                StatusRow("Warning", "Orientation mismatch — recalibrate")
            }
            StatusRow(
                "Accessibility",
                when (inputStatus.accessibility) {
                    AccessibilityStatus.CONNECTED -> "CONNECTED"
                    AccessibilityStatus.DISCONNECTED -> "DISCONNECTED"
                    AccessibilityStatus.NOT_ENABLED -> "NOT ENABLED"
                }
            )
            StatusRow(
                "Input",
                when (inputStatus.inputState) {
                    InputState.IDLE -> "Idle"
                    InputState.STEERING_LEFT -> "Steering Left"
                    InputState.STEERING_RIGHT -> "Steering Right"
                    InputState.STEERING_CENTER -> "Steering Center"
                    InputState.BRAKE_ON -> "Brake ON"
                    InputState.THROTTLE_ON -> "Throttle ON"
                    InputState.EMERGENCY_STOP -> "EMERGENCY STOP"
                    InputState.ERROR -> "Error"
                }
            )

            inputStatus.layout?.let { layout ->
                Text(
                    "Test coords — Steer: (${layout.steeringCenterX.toInt()}, ${layout.steeringCenterY.toInt()})  " +
                        "Brake: (${layout.brakeX.toInt()}, ${layout.brakeY.toInt()})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            inputStatus.lastError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(8.dp))
            SteeringWheelViz(status.steering.angleDegrees, Modifier.size(110.dp))

            Spacer(Modifier.height(12.dp))
            // Always clickable — ViewModel shows clear errors if A11y missing.
            // Auto-creates a Default profile if none selected.
            FilledTonalButton(
                onClick = { onCalibrate() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Calibrate Controls")
            }
            if (!a11yConnected) {
                Text(
                    "Accessibility must be enabled for calibration overlay.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (activeProfile == null) {
                Text(
                    "No profile selected — a Default profile will be created.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "Profile: ${activeProfile!!.name}" +
                        if (activeProfile!!.layout.calibrated) " · Calibrated" else " · Not calibrated",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            status.errorMessage?.let { msg ->
                if (msg.isNotBlank()) {
                    Text(
                        msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Accessibility Test",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                if (a11yConnected) "Service connected — tests inject real touches"
                else "Accessibility service required",
                style = MaterialTheme.typography.bodySmall,
                color = if (a11yConnected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(6.dp))

            if (!a11yConnected) {
                OutlinedButton(
                    onClick = { viewModel.openAccessibilitySettings() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open Accessibility Settings")
                }
                Spacer(Modifier.height(6.dp))
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilledTonalButton(
                    onClick = { viewModel.testTap() },
                    enabled = a11yConnected
                ) { Text("Test Tap") }
                FilledTonalButton(
                    onClick = { viewModel.testLeft() },
                    enabled = a11yConnected
                ) { Text("Test Left") }
                FilledTonalButton(
                    onClick = { viewModel.testRight() },
                    enabled = a11yConnected
                ) { Text("Test Right") }
                FilledTonalButton(
                    onClick = { viewModel.testBrake() },
                    enabled = a11yConnected
                ) { Text("Test Brake") }
                FilledTonalButton(
                    onClick = { viewModel.testThrottle() },
                    enabled = a11yConnected
                ) { Text("Test Throttle") }
                FilledTonalButton(
                    onClick = { viewModel.testReleaseAll() },
                    enabled = a11yConnected
                ) { Text("Release All") }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { viewModel.emergencyStop() },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Stop, null, Modifier.size(28.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.emergency_stop),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "Live hand → steering/brake is active when tracking + Accessibility are both on.\n" +
                    "Use Game Profiles + Calibrate Controls to set exact positions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LandmarkOverlay(
    landmarks: List<com.handdrive.domain.Landmark>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        for (lm in landmarks) {
            drawCircle(Color(0xFF00E676), 5f, Offset(lm.x * w, lm.y * h))
        }
    }
}

@Composable
private fun SteeringWheelViz(angleDegrees: Float, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension / 2f * 0.85f
        drawCircle(surface, r, Offset(cx, cy))
        drawCircle(primary, r, Offset(cx, cy), style = Stroke(8f))
        val rad = Math.toRadians(angleDegrees.toDouble())
        val x = cx + (r * 0.75f * sin(rad)).toFloat()
        val y = cy - (r * 0.75f * cos(rad)).toFloat()
        drawLine(primary, Offset(cx, cy), Offset(x, y), 10f, cap = StrokeCap.Round)
        drawCircle(primary, 12f, Offset(cx, cy))
    }
}
