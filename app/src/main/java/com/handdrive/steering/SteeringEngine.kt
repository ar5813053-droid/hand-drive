package com.handdrive.steering

import com.handdrive.domain.CalibrationData
import com.handdrive.domain.Landmark
import com.handdrive.domain.SteeringCommand
import com.handdrive.domain.TrackingResult
import com.handdrive.domain.TrackingState
import com.handdrive.settings.AppSettings
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

/**
 * Pure steering engine — no Android / CameraX / MediaPipe / UI dependencies.
 *
 * Input:  TrackingResult + CalibrationData + AppSettings
 * Output: SteeringCommand  (normalized −1 … +1)
 *
 * DEFAULT invertSteering = false:
 *   Hand turns RIGHT → steering LEFT  (negative)
 *   Hand turns LEFT  → steering RIGHT (positive)
 */
class SteeringEngine {

    private var smoothedValue: Float = 0f
    private var lastTimestampMs: Long = 0L

    fun reset() {
        smoothedValue = 0f
        lastTimestampMs = 0L
    }

    /**
     * Process one tracking frame and return the current steering command.
     */
    fun process(
        tracking: TrackingResult,
        calibration: CalibrationData,
        settings: AppSettings,
        nowMs: Long = System.currentTimeMillis()
    ): SteeringCommand {
        // Safety: lost / low confidence → neutralize
        if (!tracking.isValid || tracking.state != TrackingState.TRACKING) {
            return neutralize(settings, nowMs)
        }

        val rawMetric = computeOrientationMetric(tracking.landmarks) ?: run {
            return neutralize(settings, nowMs)
        }

        // Map metric through calibration to normalized −1…+1
        var normalized = mapToNormalized(rawMetric, calibration)

        // DEFAULT inverted behavior: negate so hand-right → steer-left
        if (!settings.invertSteering) {
            normalized = -normalized
        }
        // When invertSteering is true we keep the natural mapping (no extra negate)

        // Sensitivity
        normalized *= settings.sensitivity

        // Clamp to max
        val maxNorm = 1f
        normalized = normalized.coerceIn(-maxNorm, maxNorm)

        // Dead zone
        if (abs(normalized) < settings.deadZone) {
            normalized = 0f
        } else {
            // Rescale so values just outside dead zone start from 0
            val sign = if (normalized > 0) 1f else -1f
            val mag = (abs(normalized) - settings.deadZone) / (1f - settings.deadZone)
            normalized = sign * mag.coerceIn(0f, 1f)
        }

        // Exponential smoothing
        val alpha = (1f - settings.smoothing).coerceIn(0.05f, 1f)
        smoothedValue = alpha * normalized + (1f - alpha) * smoothedValue

        // Auto-center when near zero
        if (settings.autoCenterEnabled && abs(smoothedValue) < settings.deadZone * 1.5f) {
            val pull = settings.autoCenterSpeed.coerceIn(0.01f, 0.5f)
            smoothedValue *= (1f - pull)
            if (abs(smoothedValue) < 0.01f) smoothedValue = 0f
        }

        lastTimestampMs = nowMs
        val angle = smoothedValue * settings.maxAngleDegrees
        return SteeringCommand(
            value = smoothedValue.coerceIn(-1f, 1f),
            angleDegrees = angle,
            isNeutral = abs(smoothedValue) < 0.02f,
            timestampMs = nowMs
        )
    }

    private fun neutralize(settings: AppSettings, nowMs: Long): SteeringCommand {
        if (settings.autoCenterEnabled) {
            val pull = max(settings.autoCenterSpeed, 0.2f)
            smoothedValue *= (1f - pull)
            if (abs(smoothedValue) < 0.02f) smoothedValue = 0f
        } else {
            smoothedValue = 0f
        }
        lastTimestampMs = nowMs
        return SteeringCommand(
            value = smoothedValue,
            angleDegrees = smoothedValue * settings.maxAngleDegrees,
            isNeutral = abs(smoothedValue) < 0.02f,
            timestampMs = nowMs
        )
    }

    /**
     * Orientation metric from wrist → middle-finger MCP vector.
     * MediaPipe indices: 0 = wrist, 9 = middle finger MCP.
     * Returns atan2-based signed angle roughly in −π…π, then scaled.
     */
    fun computeOrientationMetric(landmarks: List<Landmark>): Float? {
        if (landmarks.size < 21) return null
        val wrist = landmarks[0]
        val middleMcp = landmarks[9]
        val dx = middleMcp.x - wrist.x
        val dy = middleMcp.y - wrist.y
        // atan2(dx, -dy) so upright hand ≈ 0 when pointing "up" in image space
        return atan2(dx, -dy)
    }

    /**
     * Map a raw metric through left/center/right calibration into −1…+1.
     * leftMetric → −1, centerMetric → 0, rightMetric → +1
     */
    fun mapToNormalized(metric: Float, cal: CalibrationData): Float {
        return when {
            metric <= cal.centerMetric -> {
                val span = cal.centerMetric - cal.leftMetric
                if (span < 1e-5f) 0f
                else {
                    val t = (cal.centerMetric - metric) / span
                    (-t).coerceIn(-1f, 0f)
                }
            }
            else -> {
                val span = cal.rightMetric - cal.centerMetric
                if (span < 1e-5f) 0f
                else {
                    val t = (metric - cal.centerMetric) / span
                    t.coerceIn(0f, 1f)
                }
            }
        }
    }

    /** Expose current smoothed value for tests */
    fun currentSmoothed(): Float = smoothedValue
}
