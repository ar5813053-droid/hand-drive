package com.handdrive.ui.settings

import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.handdrive.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
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
            SectionHeader("Steering")
            SettingsPlaceholderItem("Sensitivity", "Phase 4")
            SettingsPlaceholderItem("Smoothing", "Phase 4")
            SettingsPlaceholderItem("Dead zone", "Phase 4")
            SettingsPlaceholderItem("Maximum angle", "Phase 4")
            SettingsPlaceholderItem("Invert steering", "Phase 4")
            SettingsPlaceholderItem("Auto-center speed", "Phase 4")

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionHeader("Gestures")
            SettingsPlaceholderItem("Brake gesture", "Phase 5")
            SettingsPlaceholderItem("Gesture sensitivity", "Phase 5")
            SettingsPlaceholderItem("Detection delay", "Phase 5")

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionHeader("Accessibility")
            SettingsPlaceholderItem("Service status", "Phase 6")
            SettingsPlaceholderItem("Open Accessibility Settings", "Phase 6")

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.phase1_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
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
private fun SettingsPlaceholderItem(title: String, subtitle: String) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = Modifier.fillMaxWidth()
    )
}
