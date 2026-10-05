package com.handdrive.input

import com.handdrive.profiles.ControlLayout
import com.handdrive.profiles.NormPoint
import com.handdrive.profiles.ScreenOrientation
import com.handdrive.profiles.ThrottleMode

/**
 * High-level input commands produced from SteeringCommand + GestureState.
 * Consumed by GestureController → AccessibilityService.
 */
sealed class InputCommand {
    data class Steer(val value: Float) : InputCommand()
    data object BrakeDown : InputCommand()
    data object BrakeUp : InputCommand()
    data object ThrottleDown : InputCommand()
    data object ThrottleUp : InputCommand()
    data object ReleaseAll : InputCommand()
    data class TestTap(val x: Float, val y: Float) : InputCommand()
    data class TestSteer(val value: Float) : InputCommand()
    data object TestBrake : InputCommand()
    data object TestThrottle : InputCommand()
    data class CustomTap(val x: Float, val y: Float) : InputCommand()
    data class CustomHold(val x: Float, val y: Float, val down: Boolean) : InputCommand()
}

enum class InputState {
    IDLE,
    STEERING_LEFT,
    STEERING_RIGHT,
    STEERING_CENTER,
    BRAKE_ON,
    THROTTLE_ON,
    EMERGENCY_STOP,
    ERROR
}

/**
 * Runtime pixel layout derived from a profile's normalized ControlLayout
 * plus current screen size.
 */
data class InputLayout(
    val steeringCenterX: Float,
    val steeringCenterY: Float,
    val steeringLeftX: Float,
    val steeringLeftY: Float,
    val steeringRightX: Float,
    val steeringRightY: Float,
    val brakeX: Float,
    val brakeY: Float,
    val throttleX: Float,
    val throttleY: Float,
    val throttleMode: ThrottleMode = ThrottleMode.HOLD,
    val fromProfile: Boolean = false,
    val calibrated: Boolean = false,
    val profileOrientation: ScreenOrientation = ScreenOrientation.PORTRAIT
) {
    /** Map normalized steering −1…+1 to pixel X/Y along left–center–right segment. */
    fun steeringPointFor(normalized: Float): Pair<Float, Float> {
        val t = normalized.coerceIn(-1f, 1f)
        return if (t <= 0f) {
            // -1 → left, 0 → center
            val u = t + 1f // 0..1
            val x = steeringLeftX + (steeringCenterX - steeringLeftX) * u
            val y = steeringLeftY + (steeringCenterY - steeringLeftY) * u
            x to y
        } else {
            // 0 → center, +1 → right
            val x = steeringCenterX + (steeringRightX - steeringCenterX) * t
            val y = steeringCenterY + (steeringRightY - steeringCenterY) * t
            x to y
        }
    }

    /** Legacy helper used by Phase 6 tests — horizontal only. */
    fun steeringXFor(normalized: Float): Float = steeringPointFor(normalized).first

    companion object {
        fun defaults(screenWidth: Float, screenHeight: Float) = InputLayout(
            steeringCenterX = screenWidth * 0.25f,
            steeringCenterY = screenHeight * 0.75f,
            steeringLeftX = screenWidth * 0.10f,
            steeringLeftY = screenHeight * 0.75f,
            steeringRightX = screenWidth * 0.40f,
            steeringRightY = screenHeight * 0.75f,
            brakeX = screenWidth * 0.80f,
            brakeY = screenHeight * 0.80f,
            throttleX = screenWidth * 0.65f,
            throttleY = screenHeight * 0.80f,
            fromProfile = false,
            calibrated = false
        )

        fun fromControlLayout(
            layout: ControlLayout,
            screenW: Float,
            screenH: Float,
            throttleMode: ThrottleMode = ThrottleMode.HOLD
        ): InputLayout {
            fun px(p: NormPoint) = p.toPixel(screenW, screenH)
            val sc = px(layout.steeringCenter)
            val sl = px(layout.steeringLeft)
            val sr = px(layout.steeringRight)
            val br = px(layout.brake)
            val th = px(layout.throttle)
            return InputLayout(
                steeringCenterX = sc.first,
                steeringCenterY = sc.second,
                steeringLeftX = sl.first,
                steeringLeftY = sl.second,
                steeringRightX = sr.first,
                steeringRightY = sr.second,
                brakeX = br.first,
                brakeY = br.second,
                throttleX = th.first,
                throttleY = th.second,
                throttleMode = throttleMode,
                fromProfile = true,
                calibrated = layout.calibrated,
                profileOrientation = layout.calibrationOrientation
            )
        }
    }
}

enum class AccessibilityStatus {
    NOT_ENABLED,
    CONNECTED,
    DISCONNECTED
}

data class InputStatus(
    val accessibility: AccessibilityStatus = AccessibilityStatus.NOT_ENABLED,
    val inputState: InputState = InputState.IDLE,
    val throttleOn: Boolean = false,
    val brakeOn: Boolean = false,
    val lastError: String? = null,
    val layout: InputLayout? = null,
    val orientationMismatch: Boolean = false
)
