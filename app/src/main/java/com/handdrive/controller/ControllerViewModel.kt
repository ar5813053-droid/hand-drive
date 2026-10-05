package com.handdrive.controller

import android.app.Application
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.handdrive.MainActivity
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
import com.handdrive.input.InputStatus
import com.handdrive.profiles.ControlLayout
import com.handdrive.profiles.GameProfile
import com.handdrive.profiles.ProfileRepository
import com.handdrive.profiles.ScreenOrientation
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
    private val profileRepo = ProfileRepository(application)
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

    private val _profiles = MutableStateFlow<List<GameProfile>>(emptyList())
    val profiles: StateFlow<List<GameProfile>> = _profiles.asStateFlow()

    private val _activeProfile = MutableStateFlow<GameProfile?>(null)
    val activeProfile: StateFlow<GameProfile?> = _activeProfile.asStateFlow()

    private var processingJob: Job? = null
    private var a11yPollJob: Job? = null
    private var active = false

    init {
        applyDefaultLayout()
        viewModelScope.launch { settingsRepo.settingsFlow.collect { _settings.value = it } }
        viewModelScope.launch { settingsRepo.calibrationFlow.collect { _calibration.value = it } }
        viewModelScope.launch { profileRepo.profilesFlow.collect { _profiles.value = it } }
        viewModelScope.launch {
            profileRepo.activeProfileFlow.collect { profile ->
                _activeProfile.value = profile
                profile?.let { applyProfileLayout(it) }
            }
        }
        cameraController.onCameraReady = { ready ->
            _status.update { it.copy(cameraReady = ready) }
            if (!ready && active) { gestureController.releaseAll(); refreshInputStatus() }
        }
        cameraController.onError = { msg ->
            _status.update { it.copy(errorMessage = msg, cameraReady = false) }
            gestureController.releaseAll(); refreshInputStatus()
        }
        HandDriveAccessibilityService.statusListener = { st ->
            _inputStatus.update { it.copy(accessibility = st) }
            if (st != AccessibilityStatus.CONNECTED) gestureController.releaseAll()
            refreshInputStatus()
        }
        a11yPollJob = viewModelScope.launch {
            while (isActive) { refreshAccessibilityStatus(); delay(1500L) }
        }
    }

    private fun screenSize(): Pair<Float, Float> {
        val wm = getApplication<Application>().getSystemService(WindowManager::class.java)
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm?.defaultDisplay?.getRealMetrics(metrics)
        return metrics.widthPixels.toFloat().coerceAtLeast(1080f) to
            metrics.heightPixels.toFloat().coerceAtLeast(1920f)
    }

    private fun currentOrientation(): ScreenOrientation {
        val (w, h) = screenSize()
        return if (w > h) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT
    }

    private fun applyDefaultLayout() {
        val (w, h) = screenSize()
        val layout = InputLayout.defaults(w, h)
        gestureController.setLayout(layout)
        _inputStatus.update { it.copy(layout = layout) }
    }

    private fun applyProfileLayout(profile: GameProfile) {
        val (w, h) = screenSize()
        val layout = InputLayout.fromControlLayout(profile.layout, w, h, profile.throttleMode)
        val mismatch = profile.layout.calibrated &&
            profile.layout.calibrationOrientation != currentOrientation()
        gestureController.setLayout(layout)
        _inputStatus.update { it.copy(layout = layout, orientationMismatch = mismatch) }
    }

    fun refreshAccessibilityStatus() {
        _inputStatus.update {
            it.copy(accessibility = AccessibilityHelper.resolveStatus(getApplication()))
        }
    }

    private fun refreshInputStatus() {
        _inputStatus.update {
            it.copy(
                inputState = gestureController.inputState,
                throttleOn = gestureController.isThrottleHeld,
                brakeOn = gestureController.isBrakeHeld,
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
        val issues = profileReadyIssues().filter {
            // Allow start with uncalibrated defaults for testing, but warn
            it != "No profile selected"
        }
        val blocking = issues.filter {
            it.contains("Orientation mismatch") || it.contains("Accessibility")
        }
        if (blocking.isNotEmpty()) {
            _status.update {
                it.copy(errorMessage = blocking.joinToString(" · "))
            }
            // Orientation / a11y still block hard
            if (blocking.any { it.contains("Orientation") || it.contains("Accessibility") }) {
                // Still allow camera preview for testing without a11y, but don't enable injection
            }
        }
        _activeProfile.value?.let { applyProfileLayout(it) } ?: applyDefaultLayout()
        if (_inputStatus.value.orientationMismatch) {
            _status.update {
                it.copy(errorMessage = "Profile orientation mismatch — recalibrate or rotate device")
            }
            return
        }
        active = true
        val facing = _settings.value.cameraFacing
        if (!handTracker.initialize()) {
            _status.update { it.copy(isActive = false, errorMessage = "Hand tracker failed") }
            active = false
            return
        }
        steeringEngine.reset(); gestureDetector.reset()
        if (HandDriveAccessibilityService.isConnected()) gestureController.enable()
        cameraController.start(lifecycleOwner, previewView, facing)
        _status.update { it.copy(isActive = true, cameraFacing = facing, errorMessage = null) }
        startProcessingLoop(); refreshInputStatus()
    }

    fun stopController() {
        active = false; processingJob?.cancel(); processingJob = null
        gestureController.releaseAll(); cameraController.stop(); handTracker.close()
        steeringEngine.reset(); gestureDetector.reset()
        _status.update {
            ControllerStatus(false, _settings.value.cameraFacing, permissionGranted = it.permissionGranted)
        }
        refreshInputStatus()
    }

    fun emergencyStop() {
        active = false; processingJob?.cancel(); processingJob = null
        gestureController.emergencyStop(); cameraController.stop(); handTracker.close()
        steeringEngine.reset(); gestureDetector.reset()
        _status.update {
            ControllerStatus(false, _settings.value.cameraFacing, permissionGranted = it.permissionGranted)
        }
        refreshInputStatus()
    }

    fun switchCamera(
        lifecycleOwner: androidx.lifecycle.LifecycleOwner,
        previewView: androidx.camera.view.PreviewView
    ) {
        val next = if (_settings.value.cameraFacing == CameraFacing.FRONT) CameraFacing.REAR else CameraFacing.FRONT
        viewModelScope.launch { settingsRepo.updateSettings { it.copy(cameraFacing = next) } }
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
        val final = updated.copy(isCalibrated = updated.leftMetric != updated.rightMetric)
        viewModelScope.launch { settingsRepo.saveCalibration(final) }
        _calibration.value = final
        return true
    }

    fun resetCalibration() {
        viewModelScope.launch { settingsRepo.clearCalibration() }
        _calibration.value = CalibrationData.DEFAULT
    }

    fun createProfile(name: String) { viewModelScope.launch { profileRepo.create(name) } }
    fun selectProfile(id: String) { viewModelScope.launch { profileRepo.setActive(id) } }
    fun deleteProfile(id: String) { viewModelScope.launch { profileRepo.delete(id) } }
    fun duplicateProfile(id: String) { viewModelScope.launch { profileRepo.duplicate(id) } }
    fun updateProfile(profile: GameProfile) { viewModelScope.launch { profileRepo.update(profile) } }
    fun resetProfileCalibration(id: String) { viewModelScope.launch { profileRepo.resetCalibration(id) } }

    /**
     * Enter dedicated LANDSCAPE calibration mode:
     * 1) Release all game input
     * 2) Lock activity to landscape
     * 3) Start Accessibility overlay with refreshed landscape metrics
     * 4) On finish/cancel unlock orientation — input stays off until Start
     */
    /**
     * Called when dedicated CalibrationScreen opens.
     * Stops controller and releases all injected input.
     * Does not require Accessibility (editing is pure UI).
     */
    fun prepareForCalibrationEditor() {
        try {
            stopController()
            gestureController.releaseAll()
            _status.update { it.copy(errorMessage = null) }
            _inputStatus.update { it.copy(lastError = null) }
            if (_activeProfile.value == null) {
                viewModelScope.launch {
                    try {
                        profileRepo.create("Default")
                    } catch (e: Exception) {
                        _status.update {
                            it.copy(errorMessage = "Create a game profile before calibration.")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            _status.update {
                it.copy(errorMessage = "Calibration couldn't be started. Please try again.")
            }
            throw e
        }
    }

    /** ReleaseAll only — orientation unchanged (safe during config-change dispose). */
    fun exitCalibrationEditorKeepOrientation() {
        try {
            gestureController.releaseAll()
        } catch (_: Exception) {
        }
    }

    /** ReleaseAll + unlock landscape. Call only on explicit Finish/Cancel/Back. */
    fun exitCalibrationEditor() {
        try {
            gestureController.releaseAll()
        } catch (_: Exception) {
        }
        try {
            MainActivity.setCalibrationLandscape(false)
        } catch (_: Exception) {
        }
    }

    /**
     * Persist ControlLayout from the dedicated CalibrationScreen editor.
     * Auto-creates Default profile if needed.
     */
    fun saveControlLayoutFromEditor(layout: ControlLayout) {
        viewModelScope.launch {
            var profile = _activeProfile.value
            if (profile == null) {
                profile = profileRepo.create("Default")
            }
            profileRepo.update(
                profile.copy(
                    layout = layout,
                    updatedAtMs = System.currentTimeMillis()
                )
            )
            _status.update {
                it.copy(errorMessage = "Calibration saved. Press Start Controller when ready.")
            }
        }
    }

    /** @deprecated Overlay calibration removed — navigate to CalibrationScreen instead. */
    fun startControlCalibration(onDone: (Boolean) -> Unit = {}) {
        // Legacy no-op path kept for binary compatibility of older call sites.
        // UI should navigate to Screen.Calibration.
        prepareForCalibrationEditor()
        onDone(false)
    }

    /** Validate profile readiness before starting controller. */
    fun profileReadyIssues(): List<String> {
        val profile = _activeProfile.value ?: return listOf("No profile selected")
        val issues = profile.readinessIssues().toMutableList()
        if (_inputStatus.value.orientationMismatch) {
            issues.add("Orientation mismatch — rotate device or recalibrate")
        }
        if (_inputStatus.value.accessibility != AccessibilityStatus.CONNECTED) {
            issues.add("Accessibility service not connected")
        }
        return issues
    }

    fun triggerCustomControl(controlId: String) {
        val profile = _activeProfile.value ?: return
        val control = profile.layout.customControls.find { it.id == controlId } ?: return
        val layout = gestureController.getLayout()
        val screenW = (layout.steeringCenterX / 0.25f).coerceAtLeast(1f) // fallback rough
        // Use normalized → pixel via profile layout conversion
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        getApplication<Application>().getSystemService(android.view.WindowManager::class.java)
            ?.defaultDisplay?.getRealMetrics(metrics)
        val w = metrics.widthPixels.toFloat().coerceAtLeast(1f)
        val h = metrics.heightPixels.toFloat().coerceAtLeast(1f)
        val (px, py) = control.point.toPixel(w, h)
        when (control.activation) {
            com.handdrive.profiles.ControlActivation.TAP ->
                gestureController.execute(InputCommand.CustomTap(px, py))
            com.handdrive.profiles.ControlActivation.HOLD ->
                gestureController.execute(InputCommand.CustomHold(px, py, down = true))
        }
        refreshInputStatus()
    }

    fun setCompatibility(
        status: com.handdrive.profiles.CompatibilityStatus,
        notes: String = ""
    ) {
        val profile = _activeProfile.value ?: return
        viewModelScope.launch {
            profileRepo.update(
                profile.copy(
                    compatibilityStatus = status,
                    compatibilityNotes = notes,
                    updatedAtMs = System.currentTimeMillis()
                )
            )
        }
    }

    fun testTap() {
        val l = gestureController.getLayout()
        gestureController.execute(InputCommand.TestTap(l.steeringCenterX, l.steeringCenterY))
        refreshInputStatus()
    }
    fun testLeft() { gestureController.execute(InputCommand.TestSteer(-0.8f)); refreshInputStatus() }
    fun testRight() { gestureController.execute(InputCommand.TestSteer(0.8f)); refreshInputStatus() }
    fun testBrake() { gestureController.execute(InputCommand.TestBrake); refreshInputStatus() }
    fun testThrottle() { gestureController.execute(InputCommand.TestThrottle); refreshInputStatus() }
    fun testReleaseAll() { gestureController.execute(InputCommand.ReleaseAll); refreshInputStatus() }
    fun openAccessibilitySettings() { AccessibilityHelper.openAccessibilitySettings(getApplication()) }

    private fun startProcessingLoop() {
        processingJob?.cancel()
        processingJob = viewModelScope.launch {
            while (isActive && active) {
                val tracking = handTracker.lastResult
                val global = _settings.value
                val profile = _activeProfile.value
                val settings = if (profile != null) global.copy(
                    sensitivity = profile.sensitivity,
                    smoothing = profile.smoothing,
                    deadZone = profile.deadZone,
                    invertSteering = profile.invertSteering,
                    maxAngleDegrees = profile.maxAngleDegrees
                ) else global
                val now = System.currentTimeMillis()
                val steering = steeringEngine.process(tracking, _calibration.value, settings, now)
                val gesture = gestureDetector.process(tracking, settings, now)
                val valid = tracking.state == TrackingState.TRACKING && tracking.isValid
                if (gestureController.isEnabled() || HandDriveAccessibilityService.isConnected()) {
                    if (!gestureController.isEnabled() && HandDriveAccessibilityService.isConnected()) {
                        gestureController.enable()
                    }
                    gestureController.onSteeringAndBrake(steering, gesture, valid, now)
                }
                _status.update { it.copy(tracking = tracking, steering = steering, gesture = gesture) }
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
