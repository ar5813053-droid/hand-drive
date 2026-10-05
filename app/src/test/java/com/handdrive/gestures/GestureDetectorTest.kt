package com.handdrive.gestures

import com.handdrive.domain.Landmark
import com.handdrive.domain.TrackingResult
import com.handdrive.domain.TrackingState
import com.handdrive.settings.AppSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GestureDetectorTest {

    private lateinit var detector: GestureDetector
    private val settings = AppSettings(
        openPalmBrakeEnabled = true,
        gestureSensitivity = 0.5f,
        detectionDelayMs = 50L
    )

    @Before
    fun setUp() {
        detector = GestureDetector()
    }

    /** Build landmarks approximating an open palm (extended fingers). */
    private fun openPalmLandmarks(): List<Landmark> {
        val list = MutableList(21) { Landmark(0.5f, 0.5f, 0f) }
        list[0] = Landmark(0.5f, 0.8f, 0f) // wrist
        list[9] = Landmark(0.5f, 0.5f, 0f) // middle MCP
        // Extended tips far from wrist
        list[8] = Landmark(0.3f, 0.15f, 0f)
        list[6] = Landmark(0.35f, 0.35f, 0f)
        list[12] = Landmark(0.5f, 0.1f, 0f)
        list[10] = Landmark(0.5f, 0.3f, 0f)
        list[16] = Landmark(0.65f, 0.15f, 0f)
        list[14] = Landmark(0.6f, 0.35f, 0f)
        list[20] = Landmark(0.75f, 0.2f, 0f)
        list[18] = Landmark(0.7f, 0.38f, 0f)
        list[4] = Landmark(0.2f, 0.4f, 0f)
        list[3] = Landmark(0.3f, 0.5f, 0f)
        return list
    }

    /** Fingers curled — tips near PIPs / wrist. */
    private fun closedPalmLandmarks(): List<Landmark> {
        val list = MutableList(21) { Landmark(0.5f, 0.5f, 0f) }
        list[0] = Landmark(0.5f, 0.8f, 0f)
        list[9] = Landmark(0.5f, 0.55f, 0f)
        // Tips close to PIPs
        for (i in list.indices) {
            if (i != 0 && i != 9) list[i] = Landmark(0.5f, 0.6f, 0f)
        }
        return list
    }

    private fun tracking(landmarks: List<Landmark>, conf: Float = 0.9f) = TrackingResult(
        landmarks = landmarks,
        confidence = conf,
        handedness = "Right",
        timestampMs = 1000L,
        state = if (conf >= 0.5f) TrackingState.TRACKING else TrackingState.LOW_CONFIDENCE
    )

    @Test
    fun openPalmEventuallyTurnsBrakeOn() {
        val t = tracking(openPalmLandmarks())
        // Need to hold for detectionDelayMs
        detector.process(t, settings, 1000L)
        val after = detector.process(t, settings, 1100L)
        assertTrue("Brake should be ON after stable open palm", after.brakeOn)
    }

    @Test
    fun closedPalmKeepsBrakeOff() {
        val t = tracking(closedPalmLandmarks())
        detector.process(t, settings, 1000L)
        val after = detector.process(t, settings, 1200L)
        assertFalse(after.brakeOn)
    }

    @Test
    fun trackingLostForcesBrakeOff() {
        val open = tracking(openPalmLandmarks())
        detector.process(open, settings, 1000L)
        detector.process(open, settings, 1100L)
        assertTrue(detector.isBrakeOn())
        val lost = TrackingResult.lost(2000L)
        val after = detector.process(lost, settings, 2000L)
        assertFalse(after.brakeOn)
    }

    @Test
    fun lowConfidenceForcesBrakeOff() {
        val open = tracking(openPalmLandmarks())
        detector.process(open, settings, 1000L)
        detector.process(open, settings, 1100L)
        assertTrue(detector.isBrakeOn())
        val low = tracking(openPalmLandmarks(), conf = 0.2f).copy(state = TrackingState.LOW_CONFIDENCE)
        val after = detector.process(low, settings, 2000L)
        assertFalse(after.brakeOn)
    }

    @Test
    fun openPalmScoreIsHigherForOpenThanClosed() {
        val openScore = detector.openPalmScore(openPalmLandmarks())
        val closedScore = detector.openPalmScore(closedPalmLandmarks())
        assertTrue("open=$openScore closed=$closedScore", openScore > closedScore)
        assertTrue(openScore > 0.5f)
    }
}
