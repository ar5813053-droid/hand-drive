package com.handdrive

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.handdrive.ui.navigation.HandDriveNavHost
import com.handdrive.ui.theme.HandDriveTheme

/**
 * Host activity. Landscape lock for calibration is owned here so Compose
 * config-change dispose cycles cannot flip orientation back and forth.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        if (calibrationLandscapeLocked) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        enableEdgeToEdge()
        setContent {
            HandDriveTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HandDriveNavHost()
                }
            }
        }
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "HandDriveMain"

        @Volatile
        private var instance: MainActivity? = null

        @Volatile
        var calibrationLandscapeLocked: Boolean = false
            private set

        fun setCalibrationLandscape(enabled: Boolean) {
            calibrationLandscapeLocked = enabled
            val act = instance
            if (act == null) {
                Log.w(TAG, "setCalibrationLandscape($enabled) but activity is null")
                return
            }
            try {
                act.requestedOrientation = if (enabled) {
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
                Log.i(TAG, "calibration landscape locked=$enabled")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set orientation: ${e.message}", e)
            }
        }

        fun lockLandscape() = setCalibrationLandscape(true)
        fun unlockOrientation() = setCalibrationLandscape(false)
    }
}
