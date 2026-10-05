package com.handdrive.gestures

import com.handdrive.domain.GestureState
import com.handdrive.domain.Landmark
import com.handdrive.domain.TrackingResult
import com.handdrive.domain.TrackingState
import com.handdrive.settings.AppSettings
import kotlin.math.sqrt

/**
 * Pure gesture detector — independent of SteeringEngine / Camera / UI.
 *
 * Open palm → BRAKE ON
 * Closed / non-open → BRAKE OFF
 * Tracking lost / low confidence → BRAKE OFF
 */
class GestureDetector {

    private var brakeOn: Boolean = false
    private var openSinceMs: Long = 0L
    private var closedSinceMs: Long = 0L

    fun reset() {
        brakeOn = false
        openSinceMs = 0L
        closedSinceMs = 0L
    }

    fun process(
        tracking: TrackingResult,
        settings: AppSettings,
        nowMs: Long = System.currentTimeMillis()
    ): GestureState {
        if (!settings.openPalmBrakeEnabled) {
            brakeOn = false
            return GestureState.off(nowMs)
        }

        // Safety: lost / low confidence → brake OFF
        if (!tracking.isValid || tracking.state != TrackingState.TRACKING) {
            brakeOn = false
            openSinceMs = 0L
            closedSinceMs = nowMs
            return GestureState.off(nowMs)
        }

        val score = openPalmScore(tracking.landmarks)
        val threshold = settings.gestureSensitivity.coerceIn(0.3f, 0.95f)
        // Hysteresis: slightly lower threshold to release
        val releaseThreshold = (threshold - 0.12f).coerceAtLeast(0.25f)
        val delayMs = settings.detectionDelayMs.coerceIn(40L, 500L)

        val isOpen = if (brakeOn) score >= releaseThreshold else score >= threshold

        if (isOpen) {
            closedSinceMs = 0L
            if (openSinceMs == 0L) openSinceMs = nowMs
            if (!brakeOn && (nowMs - openSinceMs) >= delayMs) {
                brakeOn = true
            }
        } else {
            openSinceMs = 0L
            if (closedSinceMs == 0L) closedSinceMs = nowMs
            // Release is slightly faster for safety
            val releaseDelay = (delayMs * 0.6f).toLong().coerceAtLeast(40L)
            if (brakeOn && (nowMs - closedSinceMs) >= releaseDelay) {
                brakeOn = false
            }
        }

        return GestureState(
            brakeOn = brakeOn,
            openPalmConfidence = score,
            timestampMs = nowMs
        )
    }

    /**
     * Open-palm score 0..1 based on finger extension relative to palm.
     * Uses multiple landmarks — not a single point.
     *
     * MediaPipe hand indices:
     *  0 wrist
     *  1–4 thumb, 5–8 index, 9–12 middle, 13–16 ring, 17–20 pinky
     *  Tips: 4, 8, 12, 16, 20
     *  PIPs: 3, 6, 10, 14, 18
     *  MCPs: 2, 5, 9, 13, 17
     */
    fun openPalmScore(landmarks: List<Landmark>): Float {
        if (landmarks.size < 21) return 0f

        val wrist = landmarks[0]
        val middleMcp = landmarks[9]
        val palmSize = distance(wrist, middleMcp).coerceAtLeast(1e-4f)

        // Finger tip indices and corresponding PIP
        val fingers = listOf(
            8 to 6,   // index
            12 to 10, // middle
            16 to 14, // ring
            20 to 18  // pinky
        )

        var extended = 0
        var total = fingers.size
        for ((tipIdx, pipIdx) in fingers) {
            val tip = landmarks[tipIdx]
            val pip = landmarks[pipIdx]
            // Extended if tip is farther from wrist than PIP, and tip-PIP is meaningful
            val tipDist = distance(wrist, tip)
            val pipDist = distance(wrist, pip)
            val segment = distance(pip, tip)
            if (tipDist > pipDist * 1.05f && segment > palmSize * 0.15f) {
                extended++
            }
        }

        // Thumb: compare tip (4) to IP (3) relative to wrist
        val thumbTip = landmarks[4]
        val thumbIp = landmarks[3]
        if (distance(wrist, thumbTip) > distance(wrist, thumbIp) * 1.02f) {
            extended++
            total++
        } else {
            total++
        }

        return (extended.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }

    private fun distance(a: Landmark, b: Landmark): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    fun isBrakeOn(): Boolean = brakeOn
}
