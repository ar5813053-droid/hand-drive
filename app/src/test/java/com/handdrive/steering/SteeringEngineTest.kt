package com.handdrive.steering

import com.handdrive.domain.CalibrationData
import com.handdrive.domain.Landmark
import com.handdrive.domain.TrackingResult
import com.handdrive.domain.TrackingState
import com.handdrive.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

class SteeringEngineTest {

    private lateinit var engine: SteeringEngine
    private val cal = CalibrationData(
        centerMetric = 0f,
        leftMetric = -0.6f,
        rightMetric = 0.6f,
        isCalibrated = true
    )
    private val baseSettings = AppSettings(
        sensitivity = 1f,
        smoothing = 0f, // no lag for unit tests
        deadZone = 0.05f,
        maxAngleDegrees = 90f,
        invertSteering = false, // DEFAULT inverted
        autoCenterEnabled = false
    )

    @Before
    fun setUp() {
        engine = SteeringEngine()
    }

    private fun trackingWithMetric(metric: Float): TrackingResult {
        // wrist at origin, middle MCP offset to produce desired atan2 metric
        // metric = atan2(dx, -dy)
        val dx = kotlin.math.sin(metric.toDouble()).toFloat()
        val dy = -kotlin.math.cos(metric.toDouble()).toFloat()
        val landmarks = MutableList(21) { Landmark(0f, 0f, 0f) }
        landmarks[0] = Landmark(0.5f, 0.5f, 0f)
        landmarks[9] = Landmark(0.5f + dx * 0.2f, 0.5f + dy * 0.2f, 0f)
        return TrackingResult(
            landmarks = landmarks,
            confidence = 0.9f,
            handedness = "Right",
            timestampMs = 1000L,
            state = TrackingState.TRACKING
        )
    }

    @Test
    fun centerMapsToNeutral() {
        val cmd = engine.process(trackingWithMetric(0f), cal, baseSettings, 1000L)
        assertTrue(abs(cmd.value) < 0.1f)
        assertTrue(cmd.isNeutral || abs(cmd.value) < 0.15f)
    }

    @Test
    fun defaultInverted_handRightProducesLeftSteering() {
        // Positive metric = hand turned "right" in image space
        val cmd = engine.process(trackingWithMetric(0.5f), cal, baseSettings, 1000L)
        // DEFAULT invertSteering=false → negate → negative (LEFT)
        assertTrue("Expected negative steering for hand-right, got ${cmd.value}", cmd.value < -0.2f)
    }

    @Test
    fun defaultInverted_handLeftProducesRightSteering() {
        val cmd = engine.process(trackingWithMetric(-0.5f), cal, baseSettings, 1000L)
        assertTrue("Expected positive steering for hand-left, got ${cmd.value}", cmd.value > 0.2f)
    }

    @Test
    fun invertSteeringReversesMapping() {
        val inverted = baseSettings.copy(invertSteering = true)
        val cmd = engine.process(trackingWithMetric(0.5f), cal, inverted, 1000L)
        // Natural: hand right → positive
        assertTrue("Expected positive with invert ON, got ${cmd.value}", cmd.value > 0.2f)
    }

    @Test
    fun deadZoneKeepsSmallMovementsNeutral() {
        val cmd = engine.process(trackingWithMetric(0.02f), cal, baseSettings.copy(deadZone = 0.15f), 1000L)
        assertTrue(abs(cmd.value) < 0.05f)
    }

    @Test
    fun sensitivityScalesOutput() {
        engine.reset()
        val low = engine.process(trackingWithMetric(0.5f), cal, baseSettings.copy(sensitivity = 0.5f), 1000L)
        engine.reset()
        val high = engine.process(trackingWithMetric(0.5f), cal, baseSettings.copy(sensitivity = 1.5f), 2000L)
        assertTrue(abs(high.value) > abs(low.value))
    }

    @Test
    fun trackingLostNeutralizes() {
        engine.process(trackingWithMetric(0.5f), cal, baseSettings, 1000L)
        val lost = TrackingResult.lost(2000L)
        val cmd = engine.process(lost, cal, baseSettings.copy(autoCenterEnabled = false), 2000L)
        assertEquals(0f, cmd.value, 0.05f)
        assertTrue(cmd.isNeutral)
    }

    @Test
    fun mapToNormalized_leftCenterRight() {
        assertEquals(-1f, engine.mapToNormalized(-0.6f, cal), 0.05f)
        assertEquals(0f, engine.mapToNormalized(0f, cal), 0.05f)
        assertEquals(1f, engine.mapToNormalized(0.6f, cal), 0.05f)
    }
}
