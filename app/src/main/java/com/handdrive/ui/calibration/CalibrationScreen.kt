package com.handdrive.ui.calibration

import android.app.Activity
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.handdrive.MainActivity
import com.handdrive.controller.ControllerViewModel
import com.handdrive.profiles.ControlActivation
import com.handdrive.profiles.ControlLayout
import com.handdrive.profiles.CustomControl
import com.handdrive.profiles.NormPoint
import com.handdrive.profiles.ScreenOrientation
import kotlin.math.roundToInt

private const val TAG = "CalibrationScreen"

private enum class EditTarget {
    STEERING_CENTER, STEERING_LEFT, STEERING_RIGHT, BRAKE, THROTTLE, CUSTOM
}

/**
 * Dedicated full-screen landscape control editor.
 *
 * Orientation is locked via [MainActivity.setCalibrationLandscape] and is NOT
 * unlocked in Dispose during config change (that caused an orientation loop crash).
 * Unlock only happens on explicit Finish / Cancel / Back.
 */
@Composable
fun CalibrationScreen(
    onBack: () -> Unit,
    viewModel: ControllerViewModel = viewModel()
) {
    val context = LocalContext.current
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()

    var errorMsg by remember { mutableStateOf<String?>(null) }
    var leaving by remember { mutableStateOf(false) }
    val onBackLatest = rememberUpdatedState(onBack)

    // Prepare data once; never crash the UI if preparation fails
    LaunchedEffect(Unit) {
        try {
            viewModel.prepareForCalibrationEditor()
            MainActivity.setCalibrationLandscape(true)
        } catch (e: Exception) {
            Log.e(TAG, "prepare failed", e)
            errorMsg = "Calibration couldn't be started. Please try again."
        }
    }

    // Do NOT unlock orientation in onDispose — config change would restore portrait
    // and immediately re-lock landscape → infinite Activity recreation (app exit).
    DisposableEffect(Unit) {
        onDispose {
            // Intentionally empty for orientation. Cleanup input only.
            try {
                viewModel.exitCalibrationEditorKeepOrientation()
            } catch (e: Exception) {
                Log.e(TAG, "exit cleanup failed", e)
            }
        }
    }

    fun leaveCalibration(save: Boolean) {
        if (leaving) return
        leaving = true
        try {
            if (save) {
                // save handled by caller before leaveCalibration(true)
            }
            viewModel.exitCalibrationEditor()
        } catch (e: Exception) {
            Log.e(TAG, "leave cleanup failed", e)
        } finally {
            MainActivity.setCalibrationLandscape(false)
            onBackLatest.value()
        }
    }

    BackHandler {
        leaveCalibration(save = false)
    }

    // Display metrics — refresh after landscape if needed
    val metrics = remember {
        readDisplayMetrics(context)
    }
    var screenW by remember { mutableStateOf(metrics.first.coerceAtLeast(1f)) }
    var screenH by remember { mutableStateOf(metrics.second.coerceAtLeast(1f)) }

    LaunchedEffect(Unit) {
        // Re-read after orientation settles
        kotlinx.coroutines.delay(300)
        val m = readDisplayMetrics(context)
        if (m.first > 1f && m.second > 1f) {
            screenW = m.first
            screenH = m.second
        }
    }

    val orientation = if (screenW >= screenH) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT

    val initial = activeProfile?.layout ?: ControlLayout()
    var steeringCenter by remember { mutableStateOf(initial.steeringCenter) }
    var steeringLeft by remember { mutableStateOf(initial.steeringLeft) }
    var steeringRight by remember { mutableStateOf(initial.steeringRight) }
    var brake by remember { mutableStateOf(initial.brake) }
    var throttle by remember { mutableStateOf(initial.throttle) }
    var customs by remember { mutableStateOf(initial.customControls.toList()) }
    var selected by remember { mutableStateOf<EditTarget?>(null) }
    var selectedCustomId by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    // When profile arrives asynchronously (Default created), seed if still defaults-only
    LaunchedEffect(activeProfile?.id) {
        val layout = activeProfile?.layout ?: return@LaunchedEffect
        if (layout.calibrated) {
            steeringCenter = layout.steeringCenter
            steeringLeft = layout.steeringLeft
            steeringRight = layout.steeringRight
            brake = layout.brake
            throttle = layout.throttle
            customs = layout.customControls.toList()
        }
    }

    fun finishAndSave() {
        try {
            // Always record LANDSCAPE when calibration landscape lock was used
            val savedOrient = if (
                MainActivity.calibrationLandscapeLocked || screenW >= screenH
            ) ScreenOrientation.LANDSCAPE else orientation
            val layout = ControlLayout(
                steeringCenter = clampPoint(steeringCenter),
                steeringLeft = clampPoint(steeringLeft),
                steeringRight = clampPoint(steeringRight),
                brake = clampPoint(brake),
                throttle = clampPoint(throttle),
                customControls = customs.map { it.copy(point = clampPoint(it.point)) },
                calibrated = true,
                calibrationScreenWidth = screenW.roundToInt().coerceAtLeast(1),
                calibrationScreenHeight = screenH.roundToInt().coerceAtLeast(1),
                calibrationOrientation = savedOrient
            )
            Log.i(TAG, "Saving layout orient=$savedOrient size=${screenW.toInt()}x${screenH.toInt()}")
            viewModel.saveControlLayoutFromEditor(layout)
            leaveCalibration(save = true)
        } catch (e: Exception) {
            Log.e(TAG, "save failed", e)
            errorMsg = "Could not save calibration. Please try again."
            leaving = false
        }
    }

    if (errorMsg != null) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0D1B2A)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Text(errorMsg ?: "", color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(onClick = { leaveCalibration(save = false) }) {
                    Text("Go Back")
                }
            }
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF0D1B2A), Color(0xFF1B2838), Color(0xFF0A1628))
                )
            )
    ) {
        Box(Modifier.fillMaxSize().background(Color(0x66000000)))

        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xCC0A1628))
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { leaveCalibration(save = false) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text("Calibrate Controls", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(
                        "Drag markers to game controls · ${screenW.roundToInt()}×${screenH.roundToInt()}",
                        color = Color(0xFFB0BEC5),
                        fontSize = 11.sp
                    )
                }
                TextButton(onClick = { leaveCalibration(save = false) }) {
                    Text("Cancel", color = Color(0xFFFFAB91))
                }
                FilledTonalButton(onClick = { finishAndSave() }) {
                    Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Finish")
                }
            }

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val boxW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                val boxH = constraints.maxHeight.toFloat().coerceAtLeast(1f)

                // Wait for real layout — avoid zero-size crash paths
                if (constraints.maxWidth <= 0 || constraints.maxHeight <= 0) {
                    return@BoxWithConstraints
                }

                fun toOffset(p: NormPoint) =
                    Offset(p.x.coerceIn(0f, 1f) * boxW, p.y.coerceIn(0f, 1f) * boxH)

                Canvas(Modifier.fillMaxSize()) {
                    try {
                        val l = toOffset(steeringLeft)
                        val c = toOffset(steeringCenter)
                        val r = toOffset(steeringRight)
                        val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                        drawLine(Color(0xAA00E676), l, c, strokeWidth = 4f, cap = StrokeCap.Round, pathEffect = dash)
                        drawLine(Color(0xAA00E676), c, r, strokeWidth = 4f, cap = StrokeCap.Round, pathEffect = dash)
                    } catch (e: Exception) {
                        Log.e(TAG, "canvas draw failed", e)
                    }
                }

                DraggableMarker("L", "Left", steeringLeft, selected == EditTarget.STEERING_LEFT, Color(0xFF4FC3F7), boxW, boxH,
                    onSelect = { selected = EditTarget.STEERING_LEFT; selectedCustomId = null },
                    onMove = { steeringLeft = clampPoint(it) })
                DraggableMarker("C", "Center", steeringCenter, selected == EditTarget.STEERING_CENTER, Color(0xFF00E676), boxW, boxH,
                    onSelect = { selected = EditTarget.STEERING_CENTER; selectedCustomId = null },
                    onMove = { steeringCenter = clampPoint(it) })
                DraggableMarker("R", "Right", steeringRight, selected == EditTarget.STEERING_RIGHT, Color(0xFF4FC3F7), boxW, boxH,
                    onSelect = { selected = EditTarget.STEERING_RIGHT; selectedCustomId = null },
                    onMove = { steeringRight = clampPoint(it) })
                DraggableMarker("B", "Brake", brake, selected == EditTarget.BRAKE, Color(0xFFFF5252), boxW, boxH,
                    onSelect = { selected = EditTarget.BRAKE; selectedCustomId = null },
                    onMove = { brake = clampPoint(it) })
                DraggableMarker("T", "Throttle", throttle, selected == EditTarget.THROTTLE, Color(0xFFFFD740), boxW, boxH,
                    onSelect = { selected = EditTarget.THROTTLE; selectedCustomId = null },
                    onMove = { throttle = clampPoint(it) })

                customs.forEach { cc ->
                    DraggableMarker(
                        cc.name.take(1).uppercase(), cc.name, cc.point,
                        selected == EditTarget.CUSTOM && selectedCustomId == cc.id,
                        Color(0xFFCE93D8), boxW, boxH,
                        onSelect = { selected = EditTarget.CUSTOM; selectedCustomId = cc.id },
                        onMove = { np ->
                            customs = customs.map {
                                if (it.id == cc.id) it.copy(point = clampPoint(np)) else it
                            }
                        }
                    )
                }

                val sel = selected
                if (sel != null) {
                    val point = when (sel) {
                        EditTarget.STEERING_CENTER -> steeringCenter
                        EditTarget.STEERING_LEFT -> steeringLeft
                        EditTarget.STEERING_RIGHT -> steeringRight
                        EditTarget.BRAKE -> brake
                        EditTarget.THROTTLE -> throttle
                        EditTarget.CUSTOM -> customs.find { it.id == selectedCustomId }?.point
                            ?: NormPoint(0.5f, 0.5f)
                    }
                    val title = when (sel) {
                        EditTarget.STEERING_CENTER -> "Steering Center"
                        EditTarget.STEERING_LEFT -> "Steering Left"
                        EditTarget.STEERING_RIGHT -> "Steering Right"
                        EditTarget.BRAKE -> "Brake"
                        EditTarget.THROTTLE -> "Throttle"
                        EditTarget.CUSTOM -> customs.find { it.id == selectedCustomId }?.name ?: "Custom"
                    }
                    ContextualPanel(
                        title = title,
                        point = point,
                        screenW = screenW,
                        screenH = screenH,
                        canDelete = sel == EditTarget.CUSTOM,
                        onDeselect = { selected = null; selectedCustomId = null },
                        onDelete = {
                            selectedCustomId?.let { id -> customs = customs.filterNot { it.id == id } }
                            selected = null
                            selectedCustomId = null
                        },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = Color(0xFF00E676),
            contentColor = Color.Black
        ) {
            Icon(Icons.Default.Add, "Add control")
        }
    }

    if (showAddDialog) {
        AddControlDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, activation ->
                val cc = CustomControl(
                    name = name.ifBlank { "Custom" },
                    point = NormPoint(0.5f, 0.5f),
                    activation = activation
                )
                customs = customs + cc
                selected = EditTarget.CUSTOM
                selectedCustomId = cc.id
                showAddDialog = false
            }
        )
    }
}

private fun clampPoint(p: NormPoint) = NormPoint(p.x.coerceIn(0f, 1f), p.y.coerceIn(0f, 1f))

private fun readDisplayMetrics(context: android.content.Context): Pair<Float, Float> {
    return try {
        val m = DisplayMetrics()
        @Suppress("DEPRECATION")
        context.getSystemService(WindowManager::class.java)?.defaultDisplay?.getRealMetrics(m)
        m.widthPixels.toFloat().coerceAtLeast(1f) to m.heightPixels.toFloat().coerceAtLeast(1f)
    } catch (e: Exception) {
        Log.e(TAG, "metrics failed", e)
        1920f to 1080f
    }
}

@Composable
private fun DraggableMarker(
    label: String,
    name: String,
    point: NormPoint,
    selected: Boolean,
    color: Color,
    boxW: Float,
    boxH: Float,
    onSelect: () -> Unit,
    onMove: (NormPoint) -> Unit
) {
    if (boxW <= 0f || boxH <= 0f) return

    val sizeDp = if (selected) 52.dp else 44.dp
    val density = LocalDensity.current
    val sizePx = with(density) { sizeDp.toPx() }.coerceAtLeast(1f)
    var x by remember { mutableStateOf(point.x.coerceIn(0f, 1f) * boxW) }
    var y by remember { mutableStateOf(point.y.coerceIn(0f, 1f) * boxH) }

    LaunchedEffect(point.x, point.y, boxW, boxH) {
        x = point.x.coerceIn(0f, 1f) * boxW
        y = point.y.coerceIn(0f, 1f) * boxH
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .offset {
                IntOffset(
                    (x - sizePx / 2).roundToInt().coerceIn(-sizePx.roundToInt(), boxW.roundToInt()),
                    (y - sizePx / 2).roundToInt().coerceIn(-sizePx.roundToInt(), boxH.roundToInt())
                )
            }
            .pointerInput(boxW, boxH) {
                detectDragGestures(
                    onDragStart = { onSelect() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        x = (x + dragAmount.x).coerceIn(0f, boxW)
                        y = (y + dragAmount.y).coerceIn(0f, boxH)
                        onMove(
                            NormPoint(
                                (x / boxW).coerceIn(0f, 1f),
                                (y / boxH).coerceIn(0f, 1f)
                            )
                        )
                    }
                )
            }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(sizeDp)
                .background(color.copy(alpha = if (selected) 0.95f else 0.72f), CircleShape)
                .border(if (selected) 3.dp else 1.dp, Color.White, CircleShape)
        ) {
            Text(label, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Text(name, color = Color.White, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun ContextualPanel(
    title: String,
    point: NormPoint,
    screenW: Float,
    screenH: Float,
    canDelete: Boolean,
    onDeselect: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = Color(0xEE1B2838)) {
        Column(Modifier.padding(12.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(
                "X: %.2f  Y: %.2f  ·  %.0f×%.0f px".format(
                    point.x, point.y, point.x * screenW, point.y * screenH
                ),
                color = Color(0xFF90A4AE),
                fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDeselect) {
                    Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Done")
                }
                if (canDelete) {
                    OutlinedButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, null, Modifier.size(16.dp), tint = Color(0xFFFF5252))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete", color = Color(0xFFFF5252))
                    }
                }
            }
        }
    }
}

@Composable
private fun AddControlDialog(
    onDismiss: () -> Unit,
    onAdd: (String, ControlActivation) -> Unit
) {
    val presets = listOf("Nitro", "Drift", "Horn", "Gear", "Handbrake", "Camera", "Custom")
    var name by remember { mutableStateOf("Nitro") }
    var activation by remember { mutableStateOf(ControlActivation.TAP) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Control") },
        text = {
            Column {
                presets.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        row.forEach { p ->
                            TextButton(onClick = { name = p }) { Text(p, fontSize = 12.sp) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row {
                    TextButton(onClick = { activation = ControlActivation.TAP }) {
                        Text("TAP", fontWeight = if (activation == ControlActivation.TAP) FontWeight.Bold else FontWeight.Normal)
                    }
                    TextButton(onClick = { activation = ControlActivation.HOLD }) {
                        Text("HOLD", fontWeight = if (activation == ControlActivation.HOLD) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name.ifBlank { "Custom" }, activation) }) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
