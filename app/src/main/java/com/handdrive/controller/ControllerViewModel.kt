package com.handdrive.controller

import android.app.Application
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.handdrive.accessibility.AccessibilityHelper
import com.handdrive.accessibility.HandDriveAccessibilityService
import com.handdrive.camera.CameraController
import com.handdrive.domain.CalibrationData
import com.handdrive.domain.CameraFacing
import com.handdrive.domain.ControllerStatus
import com.handdrive.domain.TrackingState
import com.handdrive.gestures.GestureDetector
import com.handdrive.input.AccessibilityStatus
import com.handdrive.input.GestureController
import com.handdrive.input.InputCommand
import com.handdrive.input.InputLayout
import com.handdrive.input.InputState
import com.handdrive.input.InputStatus
import com.handdrive.settings.AppSettings
import com.handdrive.settings.SettingsRepository
import com.handdrive.steering.SteeringEngine
import com.handdrive.tracking.HandTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ControllerViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val handTracker = HandTracker(application)
    private val cameraController = CameraController(application, handTracker)
    private val steeringEngine = SteeringEngine()
    private val gestureDetector = GestureDetector()
    private val gestureController = GestureController()

    private val _status = MutableStateFlow(ControllerStatus())
    val status: StateFlow<ControllerStatus> = _status.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings.DEFAULT)
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _calibration = MutableStateFlow(CalibrationData.DEFAULT)
    val calibration: StateFlow<CalibrationData> = _calibration.asStateFlow()

    private val _inputStatus = MutableStateFlow(InputStatus())
    val inputStatus: StateFlow<InputStatus> = _inputStatus.asStateFlow()

    private var processingJob: Job? = null
    private var a11yPollJob: Job? = null
    private var active = false

    init {
        // Default layout from display metrics
        val layout = buildDefaultLayout()
        gestureController.setLayout(layout)
        _inputStatus.update { it.copy(layout = layout) }

        viewModelScope.launch {
            settingsRepo.settingsFlow.collect { _settings.value = it }
        }
        viewModelScope.launch {
            settingsRepo.calibrationFlow.collect { _calibration.value = it }
        }

        cameraController.onCameraReady = { ready ->
            _status.update { it.copy(cameraReady = ready) }
            if (!ready && active) {
                // Camera failure → release input
                gestureController.releaseAll()
                refreshInputStatus()
            }
        }
        cameraController.onError = { msg ->
            _status.update { it.copy(errorMessage = msg, cameraReady = false) }
            gestureController.releaseAll()
            refreshInputStatus()
        }

        HandDriveAccessibilityService.statusListener = { st ->
            _inputStatus.update { it.copy(accessibility = st) }
            if (st != AccessibilityStatus.CONNECTED) {
                gestureController.releaseAll()
            }
            refreshInputStatus()
        }

        // Poll accessibility status (user may enable/disable outside app)
        a11yPollJob = viewModelScope.launch {
            while (isActive) {
                refreshAccessibilityStatus()
                delay(1500L)
            }
        }
    }

    private fun buildDefaultLayout(): InputLayout {
        val wm = getApplication<Application>().getSystemService(WindowManager::class.java)
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm?.defaultDisplay?.getRealMetrics(metrics)
        val w = metrics.widthPixels.toFloat().coerceAtLeast(1080f)
        val h = metrics.heightPixels.toFloat().coerceAtLeast(1920f)
        return InputLayout.defaults(w, h)
    }

    fun refreshAccessibilityStatus() {
        val st = AccessibilityHelper.resolveStatus(getApplication())
        _inputStatus.update { it.copy(accessibility = st) }
    }

    private fun refreshInputStatus() {
        _inputStatus.update {
            it.copy(
                inputState = gestureController.inputState,
                lastError = gestureController.lastError,
                accessibility = AccessibilityHelper.resolveStatus(getApplication())
            )
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
        if (HandDriveAccessibilityService.isConnected()) {
            gestureController.enable()
        }
        cameraController.start(lifecycleOwner, previewView, facing)
        _status.update {
            it.copy(
                isActive = true,
                cameraFacing = facing,
                errorMessage = null
            )
        }
        startProcessingLoop()
        refreshInputStatus()
    }

    fun stopController() {
        active = false
        processingJob?.cancel()
        processingJob = null
        gestureController.releaseAll()
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
        refreshInputStatus()
    }

    /** Emergency Stop — immediate release, no waiting for next frame */
    fun emergencyStop() {
        active = false
        processingJob?.cancel()
        processingJob = null
        gestureController.emergencyStop()
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
        refreshInputStatus()
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

    // ── Accessibility Test commands ──────────────────────────────────────

    fun testTap() {
        val layout = gestureController.getLayout()
        gestureController.execute(
            InputCommand.TestTap(layout.steeringCenterX, layout.steeringCenterY)
        )
        refreshInputStatus()
    }

    fun testLeft() {
        gestureController.execute(InputCommand.TestSteer(-0.8f))
        refreshInputStatus()
    }

    fun testRight() {
        gestureController.execute(InputCommand.TestSteer(0.8f))
        refreshInputStatus()
    }

    fun testBrake() {
        gestureController.execute(InputCommand.TestBrake)
        refreshInputStatus()
    }

    fun testReleaseAll() {
        gestureController.execute(InputCommand.ReleaseAll)
        refreshInputStatus()
    }

    fun openAccessibilitySettings() {
        AccessibilityHelper.openAccessibilitySettings(getApplication())
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

                // Safety: tracking loss / low confidence → neutralize input
                if (tracking.state != TrackingState.TRACKING || !tracking.isValid) {
                    if (gestureController.isEnabled()) {
                        // Engines already neutralize; still push neutral + brake off
                        gestureController.onSteeringAndBrake(steering, gesture, now)
                    }
                } else if (gestureController.isEnabled() ||
                    HandDriveAccessibilityService.isConnected()
                ) {
                    if (!gestureController.isEnabled() &&
                        HandDriveAccessibilityService.isConnected()
                    ) {
                        gestureController.enable()
                    }
                    gestureController.onSteeringAndBrake(steering, gesture, now)
                }

                _status.update {
                    it.copy(
                        tracking = tracking,
                        steering = steering,
                        gesture = gesture
                    )
                }
                refreshInputStatus()
                delay(16L)
            }
        }
    }

    override fun onCleared() {
        a11yPollJob?.cancel()
        HandDriveAccessibilityService.statusListener = null
        stopController()
        super.onCleared()
    }

    enum class CalPoint { CENTER, LEFT, RIGHT }
}
