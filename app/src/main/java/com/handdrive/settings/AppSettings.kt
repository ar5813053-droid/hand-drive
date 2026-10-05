package com.handdrive.settings

import com.handdrive.domain.CameraFacing

/**
 * All persisted user settings.
 * Defaults match the product specification.
 */
data class AppSettings(
    // Camera
    val cameraFacing: CameraFacing = CameraFacing.FRONT,

    // Steering
    /** 0.5 … 2.0 — multiplies the normalized angle */
    val sensitivity: Float = 1.0f,
    /** 0.0 … 0.95 — exponential smoothing factor (higher = smoother / more lag) */
    val smoothing: Float = 0.35f,
    /** 0.0 … 0.3 — neutral zone around center in normalized space */
    val deadZone: Float = 0.08f,
    /** Maximum visual/output angle in degrees (display + clamp) */
    val maxAngleDegrees: Float = 90f,
    /**
     * DEFAULT false = inverted mapping (hand RIGHT → steer LEFT).
     * true = natural mapping (hand RIGHT → steer RIGHT).
     */
    val invertSteering: Boolean = false,
    val autoCenterEnabled: Boolean = true,
    /** How fast auto-center pulls toward 0 (0.01 … 0.5 per frame-ish) */
    val autoCenterSpeed: Float = 0.12f,

    // Gestures
    val openPalmBrakeEnabled: Boolean = true,
    /** Minimum open-palm score 0..1 */
    val gestureSensitivity: Float = 0.55f,
    /** Frames (approx) the open-palm must stay stable before brake ON */
    val detectionDelayMs: Long = 120L
) {
    companion object {
        val DEFAULT = AppSettings()
    }
}
