package com.handdrive.input

import android.accessibilityservice.GestureDescription
import android.util.Log
import com.handdrive.accessibility.HandDriveAccessibilityService
import com.handdrive.domain.GestureState
import com.handdrive.domain.SteeringCommand
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * Translates SteeringCommand + GestureState into throttled Accessibility gestures.
 * Does NOT contain hand tracking, sensitivity, dead-zone, or palm detection logic.
 */
class GestureController {

    private val enabled = AtomicBoolean(false)
    private var layout: InputLayout = InputLayout.defaults(1080f, 1920f)

    private var lastSteerValue: Float = 0f
    private var lastSteerDispatchMs: Long = 0L
    private var brakeHeld: Boolean = false
    private var activeStroke: GestureDescription.StrokeDescription? = null
    private var currentFingerX: Float = 0f
    private var currentFingerY: Float = 0f
    private var steeringActive: Boolean = false

    @Volatile
    var inputState: InputState = InputState.IDLE
        private set

    @Volatile
    var lastError: String? = null
        private set

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
     * Consume live steering + brake from the existing engines.
     * Throttled — not every camera frame.
     */
    fun onSteeringAndBrake(
        steering: SteeringCommand,
        gesture: GestureState,
        nowMs: Long = System.currentTimeMillis()
    ) {
        if (!enabled.get()) return
        val service = HandDriveAccessibilityService.getInstance()
        if (service == null) {
            safeFail("Accessibility disconnected")
            return
        }

        // Brake first (safety priority)
        if (gesture.brakeOn && !brakeHeld) {
            dispatchBrakeDown(service)
        } else if (!gesture.brakeOn && brakeHeld) {
            dispatchBrakeUp(service)
        }

        // Steering — only if change is meaningful and throttle elapsed
        val value = steering.value.coerceIn(-1f, 1f)
        val delta = abs(value - lastSteerValue)
        val elapsed = nowMs - lastSteerDispatchMs

        if (steering.isNeutral && abs(lastSteerValue) < 0.02f && !steeringActive) {
            return
        }

        if (delta < STEER_EPSILON && elapsed < STEER_MIN_INTERVAL_MS && steeringActive) {
            return
        }

        if (elapsed < STEER_MIN_INTERVAL_MS && steeringActive) {
            return
        }

        dispatchSteer(service, value, nowMs)
    }

    fun execute(command: InputCommand) {
        val service = HandDriveAccessibilityService.getInstance()
        if (service == null) {
            lastError = "Accessibility service not connected"
            inputState = InputState.ERROR
            return
        }
        when (command) {
            is InputCommand.Steer -> {
                if (!enabled.get()) enable()
                dispatchSteer(service, command.value.coerceIn(-1f, 1f), System.currentTimeMillis())
            }
            InputCommand.BrakeDown -> dispatchBrakeDown(service)
            InputCommand.BrakeUp -> dispatchBrakeUp(service)
            InputCommand.ReleaseAll -> releaseAll()
            is InputCommand.TestTap -> {
                service.dispatchTap(command.x, command.y) { ok ->
                    if (!ok) lastError = "Test tap failed"
                }
                inputState = InputState.IDLE
            }
            is InputCommand.TestSteer -> {
                if (!enabled.get()) enable()
                dispatchSteer(service, command.value.coerceIn(-1f, 1f), System.currentTimeMillis())
            }
            InputCommand.TestBrake -> {
                dispatchBrakeDown(service)
                // Auto-release after short hold for test
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    dispatchBrakeUp(service)
                }, 400L)
            }
        }
    }

    fun releaseAll() {
        val service = HandDriveAccessibilityService.getInstance()
        service?.cancelActiveGesture()
        activeStroke = null
        steeringActive = false
        if (brakeHeld) {
            brakeHeld = false
            // Best-effort brake release tap-up at brake position
            service?.dispatchTap(layout.brakeX, layout.brakeY, 30L)
        }
        lastSteerValue = 0f
        inputState = InputState.IDLE
        lastError = null
        Log.i(TAG, "releaseAll()")
    }

    fun emergencyStop() {
        inputState = InputState.EMERGENCY_STOP
        releaseAll()
        enabled.set(false)
        inputState = InputState.EMERGENCY_STOP
    }

    private fun dispatchSteer(
        service: HandDriveAccessibilityService,
        value: Float,
        nowMs: Long
    ) {
        val targetX = layout.steeringXFor(value)
        val targetY = layout.steeringCenterY

        if (!steeringActive) {
            // Start a new stroke from center toward target
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
                        // Restart next cycle from center
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

        // If near neutral, end the stroke
        if (abs(value) < 0.05f && steeringActive) {
            // Final non-continuing stroke to lift
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
        inputState = when {
            value < -0.05f -> InputState.STEERING_LEFT
            value > 0.05f -> InputState.STEERING_RIGHT
            else -> InputState.STEERING_CENTER
        }
    }

    private fun dispatchBrakeDown(service: HandDriveAccessibilityService) {
        service.dispatchTap(layout.brakeX, layout.brakeY, durationMs = 80L) { ok ->
            if (!ok) lastError = "Brake down failed"
        }
        // Hold-style: short continuing stroke at brake point
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
        service.dispatchTap(layout.brakeX, layout.brakeY, durationMs = 30L)
        brakeHeld = false
        if (inputState == InputState.BRAKE_ON) {
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
        /** Minimum change in normalized steering to re-dispatch */
        private const val STEER_EPSILON = 0.04f
        /** Minimum ms between steering gesture updates */
        private const val STEER_MIN_INTERVAL_MS = 50L
    }
}
