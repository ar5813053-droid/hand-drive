package com.handdrive.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.handdrive.domain.CalibrationData
import com.handdrive.domain.CameraFacing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "handdrive_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val CAMERA_FACING = stringPreferencesKey("camera_facing")
        val SENSITIVITY = floatPreferencesKey("sensitivity")
        val SMOOTHING = floatPreferencesKey("smoothing")
        val DEAD_ZONE = floatPreferencesKey("dead_zone")
        val MAX_ANGLE = floatPreferencesKey("max_angle")
        val INVERT_STEERING = booleanPreferencesKey("invert_steering")
        val AUTO_CENTER = booleanPreferencesKey("auto_center")
        val AUTO_CENTER_SPEED = floatPreferencesKey("auto_center_speed")
        val OPEN_PALM_BRAKE = booleanPreferencesKey("open_palm_brake")
        val GESTURE_SENSITIVITY = floatPreferencesKey("gesture_sensitivity")
        val DETECTION_DELAY = longPreferencesKey("detection_delay")

        val CAL_CENTER = floatPreferencesKey("cal_center")
        val CAL_LEFT = floatPreferencesKey("cal_left")
        val CAL_RIGHT = floatPreferencesKey("cal_right")
        val CAL_DONE = booleanPreferencesKey("cal_done")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            cameraFacing = when (prefs[Keys.CAMERA_FACING]) {
                "REAR" -> CameraFacing.REAR
                else -> CameraFacing.FRONT
            },
            sensitivity = prefs[Keys.SENSITIVITY] ?: AppSettings.DEFAULT.sensitivity,
            smoothing = prefs[Keys.SMOOTHING] ?: AppSettings.DEFAULT.smoothing,
            deadZone = prefs[Keys.DEAD_ZONE] ?: AppSettings.DEFAULT.deadZone,
            maxAngleDegrees = prefs[Keys.MAX_ANGLE] ?: AppSettings.DEFAULT.maxAngleDegrees,
            invertSteering = prefs[Keys.INVERT_STEERING] ?: AppSettings.DEFAULT.invertSteering,
            autoCenterEnabled = prefs[Keys.AUTO_CENTER] ?: AppSettings.DEFAULT.autoCenterEnabled,
            autoCenterSpeed = prefs[Keys.AUTO_CENTER_SPEED] ?: AppSettings.DEFAULT.autoCenterSpeed,
            openPalmBrakeEnabled = prefs[Keys.OPEN_PALM_BRAKE] ?: AppSettings.DEFAULT.openPalmBrakeEnabled,
            gestureSensitivity = prefs[Keys.GESTURE_SENSITIVITY] ?: AppSettings.DEFAULT.gestureSensitivity,
            detectionDelayMs = prefs[Keys.DETECTION_DELAY] ?: AppSettings.DEFAULT.detectionDelayMs
        )
    }

    val calibrationFlow: Flow<CalibrationData> = context.dataStore.data.map { prefs ->
        val done = prefs[Keys.CAL_DONE] ?: false
        if (!done) {
            CalibrationData.DEFAULT
        } else {
            CalibrationData(
                centerMetric = prefs[Keys.CAL_CENTER] ?: 0f,
                leftMetric = prefs[Keys.CAL_LEFT] ?: -0.6f,
                rightMetric = prefs[Keys.CAL_RIGHT] ?: 0.6f,
                isCalibrated = true
            )
        }
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val current = AppSettings(
                cameraFacing = when (prefs[Keys.CAMERA_FACING]) {
                    "REAR" -> CameraFacing.REAR
                    else -> CameraFacing.FRONT
                },
                sensitivity = prefs[Keys.SENSITIVITY] ?: AppSettings.DEFAULT.sensitivity,
                smoothing = prefs[Keys.SMOOTHING] ?: AppSettings.DEFAULT.smoothing,
                deadZone = prefs[Keys.DEAD_ZONE] ?: AppSettings.DEFAULT.deadZone,
                maxAngleDegrees = prefs[Keys.MAX_ANGLE] ?: AppSettings.DEFAULT.maxAngleDegrees,
                invertSteering = prefs[Keys.INVERT_STEERING] ?: AppSettings.DEFAULT.invertSteering,
                autoCenterEnabled = prefs[Keys.AUTO_CENTER] ?: AppSettings.DEFAULT.autoCenterEnabled,
                autoCenterSpeed = prefs[Keys.AUTO_CENTER_SPEED] ?: AppSettings.DEFAULT.autoCenterSpeed,
                openPalmBrakeEnabled = prefs[Keys.OPEN_PALM_BRAKE] ?: AppSettings.DEFAULT.openPalmBrakeEnabled,
                gestureSensitivity = prefs[Keys.GESTURE_SENSITIVITY] ?: AppSettings.DEFAULT.gestureSensitivity,
                detectionDelayMs = prefs[Keys.DETECTION_DELAY] ?: AppSettings.DEFAULT.detectionDelayMs
            )
            val next = transform(current)
            prefs[Keys.CAMERA_FACING] = next.cameraFacing.name
            prefs[Keys.SENSITIVITY] = next.sensitivity
            prefs[Keys.SMOOTHING] = next.smoothing
            prefs[Keys.DEAD_ZONE] = next.deadZone
            prefs[Keys.MAX_ANGLE] = next.maxAngleDegrees
            prefs[Keys.INVERT_STEERING] = next.invertSteering
            prefs[Keys.AUTO_CENTER] = next.autoCenterEnabled
            prefs[Keys.AUTO_CENTER_SPEED] = next.autoCenterSpeed
            prefs[Keys.OPEN_PALM_BRAKE] = next.openPalmBrakeEnabled
            prefs[Keys.GESTURE_SENSITIVITY] = next.gestureSensitivity
            prefs[Keys.DETECTION_DELAY] = next.detectionDelayMs
        }
    }

    suspend fun saveCalibration(data: CalibrationData) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CAL_CENTER] = data.centerMetric
            prefs[Keys.CAL_LEFT] = data.leftMetric
            prefs[Keys.CAL_RIGHT] = data.rightMetric
            prefs[Keys.CAL_DONE] = data.isCalibrated
        }
    }

    suspend fun clearCalibration() {
        context.dataStore.edit { prefs ->
            prefs[Keys.CAL_DONE] = false
        }
    }
}
