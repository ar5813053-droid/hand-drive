package com.handdrive.controller

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.handdrive.camera.CameraController
import com.handdrive.domain.CalibrationData
import com.handdrive.domain.CameraFacing
import com.handdrive.domain.ControllerStatus
import com.handdrive.domain.GestureState
import com.handdrive.domain.SteeringCommand
import com.handdrive.domain.TrackingResult
import com.handdrive.gestures.GestureDetector
import com.handdrive.settings.AppSettings
import com.handdrive.settings.SettingsRepository
import com.handdrive.steering.SteeringEngine
import com.handdrive.tracking.HandTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ControllerViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val handTracker = HandTracker(application)
    private val cameraController = CameraController(application, handTracker)
    private val steeringEngine = SteeringEngine()
    private val gestureDetector = GestureDetector()

    private val _status = MutableStateFlow(ControllerStatus())
    val status: StateFlow<ControllerStatus> = _status.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings.DEFAULT)
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _calibration = MutableStateFlow(CalibrationData.DEFAULT)
    val calibration: StateFlow<CalibrationData> = _calibration.asStateFlow()

    private var processingJob: Job? = null
    private var active = false

    init {
        viewModelScope.launch {
            settingsRepo.settingsFlow.collect { _settings.value = it }
        }
        viewModelScope.launch {
            settingsRepo.calibrationFlow.collect { _calibration.value = it }
        }

        cameraController.onCameraReady = { ready ->
            _status.update { it.copy(cameraReady = ready) }
        }
        cameraController.onError = { msg ->
            _status.update { it.copy(errorMessage = msg, cameraReady = false) }
        }
    }

    fun setPermissionGranted(granted: Boolean) {
        _status.update { it.copy(permissionGranted = granted) }
    }

    fun startController(
        lifecycleOwner: androidx.lifecycle.LifecycleOwner,
        previewView: androidx.camera.view.PreviewView
    ) {
        if (active) return
        active = true
        val facing = _settings.value.cameraFacing
        val ok = handTracker.initialize()
        if (!ok) {
            _status.update {
                it.copy(
                    isActive = false,
                    errorMessage = "Hand tracker failed to initialize"
                )
            }
            active = false
            return
        }
        steeringEngine.reset()
        gestureDetector.reset()
        cameraController.start(lifecycleOwner, previewView, facing)
        _status.update {
            it.copy(
                isActive = true,
                cameraFacing = facing,
                errorMessage = null
            )
        }
        startProcessingLoop()
    }

    fun stopController() {
        active = false
        processingJob?.cancel()
        processingJob = null
        cameraController.stop()
        handTracker.close()
        steeringEngine.reset()
        gestureDetector.reset()
        _status.update {
            ControllerStatus(
                isActive = false,
                cameraFacing = _settings.value.cameraFacing,
                permissionGranted = it.permissionGranted
            )
        }
    }

    /** Emergency Stop — same as stop for Phases 2–5 */
    fun emergencyStop() {
        stopController()
    }

    fun switchCamera(
        lifecycleOwner: androidx.lifecycle.LifecycleOwner,
        previewView: androidx.camera.view.PreviewView
    ) {
        val next = if (_settings.value.cameraFacing == CameraFacing.FRONT) {
            CameraFacing.REAR
        } else {
            CameraFacing.FRONT
        }
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(cameraFacing = next) }
        }
        if (active) {
            cameraController.switchCamera(lifecycleOwner, previewView, next)
            _status.update { it.copy(cameraFacing = next) }
        }
    }

    fun captureCalibrationPoint(point: CalPoint): Boolean {
        val tracking = handTracker.lastResult
        if (!tracking.isValid) return false
        val metric = steeringEngine.computeOrientationMetric(tracking.landmarks) ?: return false
        val cal = _calibration.value
        val updated = when (point) {
            CalPoint.CENTER -> cal.copy(centerMetric = metric)
            CalPoint.LEFT -> cal.copy(leftMetric = metric)
            CalPoint.RIGHT -> cal.copy(rightMetric = metric)
        }
        val complete = updated.leftMetric != updated.rightMetric
        val final = updated.copy(isCalibrated = complete)
        viewModelScope.launch { settingsRepo.saveCalibration(final) }
        _calibration.value = final
        return true
    }

    fun resetCalibration() {
        viewModelScope.launch { settingsRepo.clearCalibration() }
        _calibration.value = CalibrationData.DEFAULT
    }

    private fun startProcessingLoop() {
        processingJob?.cancel()
        processingJob = viewModelScope.launch {
            while (isActive && active) {
                val tracking = handTracker.lastResult
                val settings = _settings.value
                val cal = _calibration.value
                val now = System.currentTimeMillis()
                val steering = steeringEngine.process(tracking, cal, settings, now)
                val gesture = gestureDetector.process(tracking, settings, now)
                _status.update {
                    it.copy(
                        tracking = tracking,
                        steering = steering,
                        gesture = gesture
                    )
                }
                delay(16L) // ~60 Hz UI update budget
            }
        }
    }

    override fun onCleared() {
        stopController()
        super.onCleared()
    }

    enum class CalPoint { CENTER, LEFT, RIGHT }
}
