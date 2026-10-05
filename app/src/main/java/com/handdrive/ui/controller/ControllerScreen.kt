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
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControllerScreen(
    onBack: () -> Unit,
    viewModel: ControllerViewModel = viewModel()
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
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
        onDispose { viewModel.stopController() }
    }

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
                            previewView?.let {
                                viewModel.switchCamera(lifecycleOwner, it)
                            }
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
                    .height(260.dp)
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
                    LandmarkOverlay(
                        landmarks = status.tracking.landmarks,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (!status.permissionGranted) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Camera permission required",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    onClick = { ensurePermissionAndStart() },
                    enabled = !status.isActive,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Start")
                }
                FilledTonalButton(
                    onClick = { viewModel.stopController() },
                    enabled = status.isActive,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Stop")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            StatusRow(
                "Camera",
                when {
                    !status.permissionGranted -> "NO PERMISSION"
                    status.cameraReady -> "${status.cameraFacing.name} · READY"
                    status.isActive -> "STARTING…"
                    else -> "IDLE"
                }
            )
            StatusRow(
                "Tracking",
                when (status.tracking.state) {
                    TrackingState.TRACKING ->
                        "ACTIVE · ${(status.tracking.confidence * 100).toInt()}%"
                    TrackingState.LOW_CONFIDENCE -> "LOW CONFIDENCE"
                    TrackingState.LOST -> "LOST"
                }
            )
            StatusRow(
                "Steering",
                String.format("%.0f°  (%.2f)", status.steering.angleDegrees, status.steering.value)
            )
            StatusRow(
                "Brake",
                if (status.gesture.brakeOn) "ON" else "OFF"
            )

            Spacer(modifier = Modifier.height(12.dp))

            SteeringWheelViz(
                angleDegrees = status.steering.angleDegrees,
                modifier = Modifier.size(140.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Invert: ${if (settings.invertSteering) "ON (natural)" else "OFF (default inverted)"} · " +
                    "Sens: ${"%.1f".format(settings.sensitivity)} · " +
                    "DZ: ${"%.2f".format(settings.deadZone)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            status.errorMessage?.let { err ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.emergencyStop() },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.emergency_stop),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Local tracking & steering only.\nInput injection will be added in Phase 6.",
                style = MaterialTheme.typography.bodyMedium,
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
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
            drawCircle(
                color = Color(0xFF00E676),
                radius = 5f,
                center = Offset(lm.x * w, lm.y * h)
            )
        }
    }
}

@Composable
private fun SteeringWheelViz(
    angleDegrees: Float,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension / 2f * 0.85f
        drawCircle(color = surface, radius = r, center = Offset(cx, cy))
        drawCircle(
            color = primary,
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = 8f)
        )
        val rad = Math.toRadians(angleDegrees.toDouble())
        val x = cx + (r * 0.75f * sin(rad)).toFloat()
        val y = cy - (r * 0.75f * cos(rad)).toFloat()
        drawLine(
            color = primary,
            start = Offset(cx, cy),
            end = Offset(x, y),
            strokeWidth = 10f,
            cap = StrokeCap.Round
        )
        drawCircle(color = primary, radius = 12f, center = Offset(cx, cy))
    }
}
