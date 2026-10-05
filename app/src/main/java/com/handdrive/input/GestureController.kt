package com.handdrive.input

import android.accessibilityservice.GestureDescription
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.handdrive.accessibility.HandDriveAccessibilityService
import com.handdrive.domain.GestureState
import com.handdrive.domain.SteeringCommand
import com.handdrive.profiles.ThrottleMode
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * Translates SteeringCommand + GestureState into throttled Accessibility gestures.
 * Phase 7: simultaneous steering + throttle, brake overrides throttle.
 */
class GestureController {

    private val enabled = AtomicBoolean(false)
    private var layout: InputLayout = InputLayout.defaults(1080f, 1920f)

    private var lastSteerValue: Float = 0f
    private var lastSteerDispatchMs: Long = 0L
    private var brakeHeld: Boolean = false
    private var throttleHeld: Boolean = false
    private var lastThrottleTapMs: Long = 0L
    private var activeStroke: GestureDescription.StrokeDescription? = null
    private var currentFingerX: Float = 0f
    private var currentFingerY: Float = 0f
    private var steeringActive: Boolean = false
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    var inputState: InputState = InputState.IDLE
        private set

    @Volatile
    var lastError: String? = null
        private set

    val isBrakeHeld: Boolean get() = brakeHeld
    val isThrottleHeld: Boolean get() = throttleHeld

    fun setLayout(layout: InputLayout) {
        this.layout = layout
    }

    fun getLayout(): InputLayout = layout

    fun enable() {
        enabled.set(true)
        inputState = InputState.IDLE
        lastError = null
    }

    fun disable() {
        enabled.set(false)
        releaseAll()
    }

    fun isEnabled(): Boolean = enabled.get()

    /**
     * Live pipeline: steering + brake + throttle.
     * @param trackingValid true when hand is tracked with sufficient confidence
     */
    fun onSteeringAndBrake(
        steering: SteeringCommand,
        gesture: GestureState,
        trackingValid: Boolean = true,
        nowMs: Long = System.currentTimeMillis()
    ) {
        if (!enabled.get()) return
        val service = HandDriveAccessibilityService.getInstance()
        if (service == null) {
            safeFail("Accessibility disconnected")
            return
        }

        // Safety: invalid tracking → full release
        if (!trackingValid) {
            if (brakeHeld || throttleHeld || steeringActive) {
                releaseAllInternal(service)
            }
            return
        }

        // Brake has priority over throttle
        if (gesture.brakeOn && !brakeHeld) {
            // Turn off throttle first
            if (throttleHeld) dispatchThrottleUp(service)
            dispatchBrakeDown(service)
        } else if (!gesture.brakeOn && brakeHeld) {
            dispatchBrakeUp(service)
            // Restore throttle after brake release
            if (!throttleHeld) dispatchThrottleDown(service, nowMs)
        }

        // Throttle: ON when tracking valid and brake off
        if (!gesture.brakeOn && !brakeHeld) {
            ensureThrottle(service, nowMs)
        }

        // Steering updates (does not release throttle)
        updateSteering(service, steering, nowMs)
    }

    fun execute(command: InputCommand) {
        if (command is InputCommand.ReleaseAll) {
            releaseAll()
            return
        }

        val service = HandDriveAccessibilityService.getInstance()
        if (service == null) {
            lastError = "Accessibility service not connected"
            inputState = InputState.ERROR
            return
        }
        when (command) {
            is InputCommand.Steer -> {
                if (!enabled.get()) enable()
                updateSteering(
                    service,
                    SteeringCommand(command.value, command.value * 90f, abs(command.value) < 0.02f, System.currentTimeMillis()),
                    System.currentTimeMillis()
                )
            }
            InputCommand.BrakeDown -> {
                if (throttleHeld) dispatchThrottleUp(service)
                dispatchBrakeDown(service)
            }
            InputCommand.BrakeUp -> dispatchBrakeUp(service)
            InputCommand.ThrottleDown -> dispatchThrottleDown(service, System.currentTimeMillis())
            InputCommand.ThrottleUp -> dispatchThrottleUp(service)
            InputCommand.ReleaseAll -> releaseAll()
            is InputCommand.TestTap -> {
                service.dispatchTap(command.x, command.y) { ok ->
                    if (!ok) lastError = "Test tap failed"
                }
                inputState = InputState.IDLE
            }
            is InputCommand.TestSteer -> {
                if (!enabled.get()) enable()
                updateSteering(
                    service,
                    SteeringCommand(command.value, command.value * 90f, false, System.currentTimeMillis()),
                    System.currentTimeMillis()
                )
            }
            InputCommand.TestBrake -> {
                if (throttleHeld) dispatchThrottleUp(service)
                dispatchBrakeDown(service)
                mainHandler.postDelayed({
                    dispatchBrakeUp(service)
                }, 400L)
            }
            InputCommand.TestThrottle -> {
                dispatchThrottleDown(service, System.currentTimeMillis())
                mainHandler.postDelayed({
                    dispatchThrottleUp(service)
                }, 500L)
            }
        }
    }

    fun releaseAll() {
        val service = HandDriveAccessibilityService.getInstance()
        releaseAllInternal(service)
        Log.i(TAG, "releaseAll()")
    }

    fun emergencyStop() {
        inputState = InputState.EMERGENCY_STOP
        releaseAll()
        enabled.set(false)
        inputState = InputState.EMERGENCY_STOP
    }

    private fun releaseAllInternal(service: HandDriveAccessibilityService?) {
        service?.cancelActiveGesture()
        activeStroke = null
        steeringActive = false
        if (brakeHeld) {
            brakeHeld = false
            service?.dispatchTap(layout.brakeX, layout.brakeY, 30L)
        }
        if (throttleHeld) {
            throttleHeld = false
            service?.dispatchTap(layout.throttleX, layout.throttleY, 30L)
        }
        lastSteerValue = 0f
        inputState = InputState.IDLE
        lastError = null
    }

    private fun ensureThrottle(service: HandDriveAccessibilityService, nowMs: Long) {
        when (layout.throttleMode) {
            ThrottleMode.HOLD -> {
                if (!throttleHeld) dispatchThrottleDown(service, nowMs)
            }
            ThrottleMode.TAP -> {
                if (nowMs - lastThrottleTapMs >= THROTTLE_TAP_INTERVAL_MS) {
                    service.dispatchTap(layout.throttleX, layout.throttleY, 40L)
                    lastThrottleTapMs = nowMs
                    throttleHeld = true // logical "active"
                    if (inputState != InputState.BRAKE_ON) {
                        inputState = InputState.THROTTLE_ON
                    }
                }
            }
        }
    }

    private fun updateSteering(
        service: HandDriveAccessibilityService,
        steering: SteeringCommand,
        nowMs: Long
    ) {
        val value = steering.value.coerceIn(-1f, 1f)
        val delta = abs(value - lastSteerValue)
        val elapsed = nowMs - lastSteerDispatchMs

        if (steering.isNeutral && abs(lastSteerValue) < 0.02f && !steeringActive) {
            return
        }
        if (delta < STEER_EPSILON && elapsed < STEER_MIN_INTERVAL_MS && steeringActive) return
        if (elapsed < STEER_MIN_INTERVAL_MS && steeringActive) return

        dispatchSteer(service, value, nowMs)
    }

    private fun dispatchSteer(
        service: HandDriveAccessibilityService,
        value: Float,
        nowMs: Long
    ) {
        val (targetX, targetY) = layout.steeringPointFor(value)

        if (!steeringActive) {
            val startX = layout.steeringCenterX
            val startY = layout.steeringCenterY
            val stroke = service.dispatchDrag(
                startX, startY, targetX, targetY,
                durationMs = 60L,
                willContinue = true
            ) { ok ->
                if (!ok) {
                    steeringActive = false
                    activeStroke = null
                }
            }
            activeStroke = stroke
            steeringActive = stroke != null
            currentFingerX = targetX
            currentFingerY = targetY
        } else {
            val prev = activeStroke
            if (prev != null) {
                val next = service.continueDrag(
                    prev,
                    currentFingerX, currentFingerY,
                    targetX, targetY,
                    durationMs = 60L,
                    willContinue = abs(value) > 0.05f
                ) { ok ->
                    if (!ok) {
                        steeringActive = false
                        activeStroke = null
                    }
                }
                if (next != null) {
                    activeStroke = next
                    currentFingerX = targetX
                    currentFingerY = targetY
                } else {
                    steeringActive = false
                    activeStroke = null
                }
            } else {
                steeringActive = false
            }
        }

        if (abs(value) < 0.05f && steeringActive) {
            val prev = activeStroke
            if (prev != null) {
                service.continueDrag(
                    prev,
                    currentFingerX, currentFingerY,
                    layout.steeringCenterX, layout.steeringCenterY,
                    durationMs = 40L,
                    willContinue = false
                )
            }
            steeringActive = false
            activeStroke = null
        }

        lastSteerValue = value
        lastSteerDispatchMs = nowMs
        if (!brakeHeld) {
            inputState = when {
                value < -0.05f -> InputState.STEERING_LEFT
                value > 0.05f -> InputState.STEERING_RIGHT
                throttleHeld -> InputState.THROTTLE_ON
                else -> InputState.STEERING_CENTER
            }
        }
    }

    private fun dispatchBrakeDown(service: HandDriveAccessibilityService) {
        if (brakeHeld) return
        service.dispatchTap(layout.brakeX, layout.brakeY, durationMs = 80L)
        service.dispatchDrag(
            layout.brakeX, layout.brakeY,
            layout.brakeX, layout.brakeY,
            durationMs = 120L,
            willContinue = true
        )
        brakeHeld = true
        inputState = InputState.BRAKE_ON
    }

    private fun dispatchBrakeUp(service: HandDriveAccessibilityService) {
        if (!brakeHeld) return
        service.dispatchTap(layout.brakeX, layout.brakeY, durationMs = 30L)
        brakeHeld = false
        inputState = if (throttleHeld) InputState.THROTTLE_ON else InputState.IDLE
    }

    private fun dispatchThrottleDown(service: HandDriveAccessibilityService, nowMs: Long) {
        if (throttleHeld || brakeHeld) return
        when (layout.throttleMode) {
            ThrottleMode.HOLD -> {
                service.dispatchTap(layout.throttleX, layout.throttleY, 60L)
                service.dispatchDrag(
                    layout.throttleX, layout.throttleY,
                    layout.throttleX, layout.throttleY,
                    durationMs = 100L,
                    willContinue = true
                )
                throttleHeld = true
            }
            ThrottleMode.TAP -> {
                service.dispatchTap(layout.throttleX, layout.throttleY, 40L)
                lastThrottleTapMs = nowMs
                throttleHeld = true
            }
        }
        if (!brakeHeld) inputState = InputState.THROTTLE_ON
    }

    private fun dispatchThrottleUp(service: HandDriveAccessibilityService) {
        if (!throttleHeld) return
        service.dispatchTap(layout.throttleX, layout.throttleY, 30L)
        throttleHeld = false
        if (!brakeHeld && inputState == InputState.THROTTLE_ON) {
            inputState = InputState.IDLE
        }
    }

    private fun safeFail(msg: String) {
        lastError = msg
        releaseAll()
        inputState = InputState.ERROR
        enabled.set(false)
    }

    companion object {
        private const val TAG = "GestureController"
        private const val STEER_EPSILON = 0.04f
        private const val STEER_MIN_INTERVAL_MS = 50L
        private const val THROTTLE_TAP_INTERVAL_MS = 200L
    }
}
