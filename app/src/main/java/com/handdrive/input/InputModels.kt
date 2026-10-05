package com.handdrive.input

/**
 * High-level input commands produced from SteeringCommand + GestureState.
 * Consumed by GestureController → AccessibilityService.
 * Contains NO hand-tracking or steering math.
 */
sealed class InputCommand {
    /** Normalized steering in [-1, 1]. -1 = full left, +1 = full right. */
    data class Steer(val value: Float) : InputCommand()

    data object BrakeDown : InputCommand()
    data object BrakeUp : InputCommand()
    data object ReleaseAll : InputCommand()

    // Explicit test commands (Controller Accessibility Test section)
    data class TestTap(val x: Float, val y: Float) : InputCommand()
    data class TestSteer(val value: Float) : InputCommand()
    data object TestBrake : InputCommand()
}

/**
 * Logical state of the input injection layer.
 */
enum class InputState {
    IDLE,
    STEERING_LEFT,
    STEERING_RIGHT,
    STEERING_CENTER,
    BRAKE_ON,
    EMERGENCY_STOP,
    ERROR
}

/**
 * Screen-relative control coordinates for steering and brake.
 * Phase 7 will persist these per-game; for Phase 6 we use configurable defaults.
 *
 * Coordinates are absolute pixels on the default display.
 */
data class InputLayout(
    val steeringCenterX: Float,
    val steeringCenterY: Float,
    /** Horizontal radius from center to full left/right */
    val steeringRangePx: Float,
    val brakeX: Float,
    val brakeY: Float
) {
    fun steeringXFor(normalized: Float): Float {
        // normalized -1 (left) … +1 (right)
        return steeringCenterX + normalized * steeringRangePx
    }

    companion object {
        /**
         * Sensible defaults for a typical phone in landscape-ish racing UI:
         * steering lower-left third, brake lower-right.
         * Overridden at runtime from real display metrics when possible.
         */
        fun defaults(screenWidth: Float, screenHeight: Float) = InputLayout(
            steeringCenterX = screenWidth * 0.25f,
            steeringCenterY = screenHeight * 0.75f,
            steeringRangePx = screenWidth * 0.18f,
            brakeX = screenWidth * 0.80f,
            brakeY = screenHeight * 0.80f
        )
    }
}

/**
 * Accessibility connection status for UI.
 */
enum class AccessibilityStatus {
    NOT_ENABLED,
    CONNECTED,
    DISCONNECTED
}

data class InputStatus(
    val accessibility: AccessibilityStatus = AccessibilityStatus.NOT_ENABLED,
    val inputState: InputState = InputState.IDLE,
    val lastError: String? = null,
    val layout: InputLayout? = null
)
