package com.handdrive.domain

/**
 * Normalized 2D/3D landmark point (values typically in 0..1 image space for x/y).
 */
data class Landmark(
    val x: Float,
    val y: Float,
    val z: Float = 0f
)

/**
 * Result of one hand-tracking frame.
 * Independent of Android UI / CameraX / Accessibility.
 */
data class TrackingResult(
    val landmarks: List<Landmark>,
    /** Overall presence / detection confidence in 0..1 */
    val confidence: Float,
    /** "Left" or "Right" when known, null otherwise */
    val handedness: String?,
    val timestampMs: Long,
    val state: TrackingState
) {
    val isValid: Boolean
        get() = state == TrackingState.TRACKING &&
            landmarks.size >= 21 &&
            confidence >= MIN_CONFIDENCE

    companion object {
        const val MIN_CONFIDENCE = 0.5f

        fun lost(timestampMs: Long = System.currentTimeMillis()) = TrackingResult(
            landmarks = emptyList(),
            confidence = 0f,
            handedness = null,
            timestampMs = timestampMs,
            state = TrackingState.LOST
        )
    }
}

enum class TrackingState {
    TRACKING,
    LOST,
    LOW_CONFIDENCE
}

/**
 * Normalized steering output.
 *
 * Convention:
 *   -1.0 = full LEFT
 *    0.0 = CENTER
 *   +1.0 = full RIGHT
 *
 * DEFAULT mapping (Invert Steering = OFF):
 *   Hand turns RIGHT → steering becomes LEFT  (negative)
 *   Hand turns LEFT  → steering becomes RIGHT (positive)
 *
 * When Invert Steering is ON, the mapping is reversed.
 */
data class SteeringCommand(
    /** Normalized steering in [-1, 1] */
    val value: Float,
    /** Degrees for display (value * maxAngleDegrees) */
    val angleDegrees: Float,
    val isNeutral: Boolean,
    val timestampMs: Long
) {
    companion object {
        fun neutral(timestampMs: Long = System.currentTimeMillis()) = SteeringCommand(
            value = 0f,
            angleDegrees = 0f,
            isNeutral = true,
            timestampMs = timestampMs
        )
    }
}

/**
 * Brake / gesture state derived from hand landmarks.
 */
data class GestureState(
    val brakeOn: Boolean,
    val openPalmConfidence: Float,
    val timestampMs: Long
) {
    companion object {
        fun off(timestampMs: Long = System.currentTimeMillis()) = GestureState(
            brakeOn = false,
            openPalmConfidence = 0f,
            timestampMs = timestampMs
        )
    }
}

/**
 * User hand calibration: center / left / right reference angles (radians or degrees).
 * Stored as the raw orientation metric used by SteeringEngine.
 */
data class CalibrationData(
    val centerMetric: Float,
    val leftMetric: Float,
    val rightMetric: Float,
    val isCalibrated: Boolean = true
) {
    companion object {
        /** Default spans roughly -45° … +45° in the orientation metric space. */
        val DEFAULT = CalibrationData(
            centerMetric = 0f,
            leftMetric = -0.6f,
            rightMetric = 0.6f,
            isCalibrated = false
        )
    }
}

/**
 * Camera lens facing.
 */
enum class CameraFacing {
    FRONT,
    REAR
}

/**
 * Aggregated controller runtime status for UI.
 */
data class ControllerStatus(
    val isActive: Boolean = false,
    val cameraFacing: CameraFacing = CameraFacing.FRONT,
    val cameraReady: Boolean = false,
    val permissionGranted: Boolean = false,
    val tracking: TrackingResult = TrackingResult.lost(),
    val steering: SteeringCommand = SteeringCommand.neutral(),
    val gesture: GestureState = GestureState.off(),
    val errorMessage: String? = null
)
