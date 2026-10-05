package com.handdrive.ui.calibration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.handdrive.R
import com.handdrive.controller.ControllerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(
    onBack: () -> Unit,
    viewModel: ControllerViewModel = viewModel()
) {
    val calibration by viewModel.calibration.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.calibration_title)) },
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                "Hand calibration",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Start the Controller first so tracking is active, then capture each pose.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Text(
                if (calibration.isCalibrated) "Status: CALIBRATED"
                else "Status: Using defaults",
                style = MaterialTheme.typography.titleMedium,
                color = if (calibration.isCalibrated)
                    MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Tracking: ${status.tracking.state}",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(24.dp))

            FilledTonalButton(
                onClick = { viewModel.captureCalibrationPoint(ControllerViewModel.CalPoint.CENTER) },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("1 · Capture CENTER pose")
            }
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(
                onClick = { viewModel.captureCalibrationPoint(ControllerViewModel.CalPoint.LEFT) },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("2 · Capture LEFT turn")
            }
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(
                onClick = { viewModel.captureCalibrationPoint(ControllerViewModel.CalPoint.RIGHT) },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("3 · Capture RIGHT turn")
            }
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(
                onClick = { viewModel.resetCalibration() },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Reset calibration")
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Center: ${"%.3f".format(calibration.centerMetric)}\n" +
                    "Left: ${"%.3f".format(calibration.leftMetric)}\n" +
                    "Right: ${"%.3f".format(calibration.rightMetric)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
