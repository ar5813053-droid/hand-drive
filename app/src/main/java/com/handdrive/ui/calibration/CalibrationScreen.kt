package com.handdrive.ui.calibration

import android.app.Activity
import android.content.pm.ActivityInfo
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.handdrive.R
import com.handdrive.controller.ControllerViewModel
import com.handdrive.profiles.ControlActivation
import com.handdrive.profiles.ControlLayout
import com.handdrive.profiles.CustomControl
import com.handdrive.profiles.NormPoint
import com.handdrive.profiles.ScreenOrientation
import kotlin.math.roundToInt

private enum class EditTarget {
    STEERING_CENTER, STEERING_LEFT, STEERING_RIGHT, BRAKE, THROTTLE, CUSTOM
}

/**
 * Dedicated full-screen landscape control editor.
 * Norm coordinates are relative to the calibration workspace which fills the
 * landscape activity; screen W/H stored for orientation validation.
 */
@Composable
fun CalibrationScreen(
    onBack: () -> Unit,
    viewModel: ControllerViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()

    val metrics = remember {
        val m = DisplayMetrics()
        @Suppress("DEPRECATION")
        context.getSystemService(WindowManager::class.java)?.defaultDisplay?.getRealMetrics(m)
        m
    }
    val screenW = metrics.widthPixels.coerceAtLeast(1).toFloat()
    val screenH = metrics.heightPixels.coerceAtLeast(1).toFloat()
    val orientation = if (screenW > screenH) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT

    val initial = activeProfile?.layout ?: ControlLayout()
    var steeringCenter by remember { mutableStateOf(initial.steeringCenter) }
    var steeringLeft by remember { mutableStateOf(initial.steeringLeft) }
    var steeringRight by remember { mutableStateOf(initial.steeringRight) }
    var brake by remember { mutableStateOf(initial.brake) }
    var throttle by remember { mutableStateOf(initial.throttle) }
    var customs by remember { mutableStateOf(initial.customControls) }
    var selected by remember { mutableStateOf<EditTarget?>(null) }
    var selectedCustomId by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        viewModel.prepareForCalibrationEditor()
        val previous = activity?.requestedOrientation
            ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = previous
            viewModel.exitCalibrationEditor()
        }
    }

    fun cancelAndExit() {
        viewModel.exitCalibrationEditor()
        onBack()
    }

    fun finishAndSave() {
        val layout = ControlLayout(
            steeringCenter = steeringCenter,
            steeringLeft = steeringLeft,
            steeringRight = steeringRight,
            brake = brake,
            throttle = throttle,
            customControls = customs,
            calibrated = true,
            calibrationScreenWidth = screenW.roundToInt(),
            calibrationScreenHeight = screenH.roundToInt(),
            calibrationOrientation = orientation
        )
        viewModel.saveControlLayoutFromEditor(layout)
        viewModel.exitCalibrationEditor()
        onBack()
    }

    BackHandler { cancelAndExit() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1B2A))
    ) {
        Image(
            painter = painterResource(R.drawable.bg_calibration_racing),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.9f
        )
        Box(Modifier.fillMaxSize().background(Color(0x99000000)))

        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xCC0A1628))
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { cancelAndExit() }) {
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
                TextButton(onClick = { cancelAndExit() }) {
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

                fun toOffset(p: NormPoint) =
                    Offset(p.x.coerceIn(0f, 1f) * boxW, p.y.coerceIn(0f, 1f) * boxH)

                Canvas(Modifier.fillMaxSize()) {
                    val l = toOffset(steeringLeft)
                    val c = toOffset(steeringCenter)
                    val r = toOffset(steeringRight)
                    val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                    drawLine(Color(0xAA00E676), l, c, strokeWidth = 4f, cap = StrokeCap.Round, pathEffect = dash)
                    drawLine(Color(0xAA00E676), c, r, strokeWidth = 4f, cap = StrokeCap.Round, pathEffect = dash)
                }

                DraggableMarker("L", "Left", steeringLeft, selected == EditTarget.STEERING_LEFT, Color(0xFF4FC3F7), boxW, boxH,
                    onSelect = { selected = EditTarget.STEERING_LEFT; selectedCustomId = null },
                    onMove = { steeringLeft = it })
                DraggableMarker("C", "Center", steeringCenter, selected == EditTarget.STEERING_CENTER, Color(0xFF00E676), boxW, boxH,
                    onSelect = { selected = EditTarget.STEERING_CENTER; selectedCustomId = null },
                    onMove = { steeringCenter = it })
                DraggableMarker("R", "Right", steeringRight, selected == EditTarget.STEERING_RIGHT, Color(0xFF4FC3F7), boxW, boxH,
                    onSelect = { selected = EditTarget.STEERING_RIGHT; selectedCustomId = null },
                    onMove = { steeringRight = it })
                DraggableMarker("B", "Brake", brake, selected == EditTarget.BRAKE, Color(0xFFFF5252), boxW, boxH,
                    onSelect = { selected = EditTarget.BRAKE; selectedCustomId = null },
                    onMove = { brake = it })
                DraggableMarker("T", "Throttle", throttle, selected == EditTarget.THROTTLE, Color(0xFFFFD740), boxW, boxH,
                    onSelect = { selected = EditTarget.THROTTLE; selectedCustomId = null },
                    onMove = { throttle = it })

                customs.forEach { cc ->
                    DraggableMarker(
                        cc.name.take(1).uppercase(), cc.name, cc.point,
                        selected == EditTarget.CUSTOM && selectedCustomId == cc.id,
                        Color(0xFFCE93D8), boxW, boxH,
                        onSelect = { selected = EditTarget.CUSTOM; selectedCustomId = cc.id },
                        onMove = { np -> customs = customs.map { if (it.id == cc.id) it.copy(point = np) else it } }
                    )
                }

                selected?.let { target ->
                    val point = when (target) {
                        EditTarget.STEERING_CENTER -> steeringCenter
                        EditTarget.STEERING_LEFT -> steeringLeft
                        EditTarget.STEERING_RIGHT -> steeringRight
                        EditTarget.BRAKE -> brake
                        EditTarget.THROTTLE -> throttle
                        EditTarget.CUSTOM -> customs.find { it.id == selectedCustomId }?.point ?: NormPoint(0.5f, 0.5f)
                    }
                    val title = when (target) {
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
                        canDelete = target == EditTarget.CUSTOM,
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
                val cc = CustomControl(name = name, point = NormPoint(0.5f, 0.5f), activation = activation)
                customs = customs + cc
                selected = EditTarget.CUSTOM
                selectedCustomId = cc.id
                showAddDialog = false
            }
        )
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
    val sizeDp = if (selected) 52.dp else 44.dp
    val density = LocalDensity.current
    val sizePx = with(density) { sizeDp.toPx() }
    var x by remember { mutableStateOf(point.x.coerceIn(0f, 1f) * boxW) }
    var y by remember { mutableStateOf(point.y.coerceIn(0f, 1f) * boxH) }

    LaunchedEffect(point.x, point.y, boxW, boxH) {
        x = point.x.coerceIn(0f, 1f) * boxW
        y = point.y.coerceIn(0f, 1f) * boxH
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .offset { IntOffset((x - sizePx / 2).roundToInt(), (y - sizePx / 2).roundToInt()) }
            .pointerInput(boxW, boxH) {
                detectDragGestures(
                    onDragStart = { onSelect() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        x = (x + dragAmount.x).coerceIn(0f, boxW)
                        y = (y + dragAmount.y).coerceIn(0f, boxH)
                        onMove(NormPoint((x / boxW).coerceIn(0f, 1f), (y / boxH).coerceIn(0f, 1f)))
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
                "X: %.2f  Y: %.2f  ·  %.0f×%.0f px".format(point.x, point.y, point.x * screenW, point.y * screenH),
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
