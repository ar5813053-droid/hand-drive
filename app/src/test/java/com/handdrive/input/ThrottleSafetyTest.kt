package com.handdrive.input

import com.handdrive.domain.GestureState
import com.handdrive.domain.SteeringCommand
import com.handdrive.profiles.ThrottleMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ThrottleSafetyTest {

    private lateinit var controller: GestureController

    @Before
    fun setUp() {
        controller = GestureController()
        controller.setLayout(
            InputLayout.defaults(1080f, 1920f).copy(throttleMode = ThrottleMode.HOLD)
        )
    }

    @Test
    fun trackingInvalidDoesNotCrashAndStaysSafe() {
        controller.enable()
        controller.onSteeringAndBrake(
            SteeringCommand(0.5f, 45f, false, 1000L),
            GestureState(brakeOn = false, openPalmConfidence = 0f, timestampMs = 1000L),
            trackingValid = false,
            nowMs = 1000L
        )
        // No service → safeFail or idle
        assertTrue(
            controller.inputState == InputState.ERROR ||
                controller.inputState == InputState.IDLE
        )
        assertFalse(controller.isBrakeHeld)
        assertFalse(controller.isThrottleHeld)
    }

    @Test
    fun emergencyStopClearsThrottleAndBrakeFlags() {
        controller.enable()
        controller.emergencyStop()
        assertFalse(controller.isEnabled())
        assertFalse(controller.isBrakeHeld)
        assertFalse(controller.isThrottleHeld)
        assertEquals(InputState.EMERGENCY_STOP, controller.inputState)
    }

    @Test
    fun releaseAllClearsFlags() {
        controller.enable()
        controller.releaseAll()
        assertFalse(controller.isBrakeHeld)
        assertFalse(controller.isThrottleHeld)
        assertEquals(InputState.IDLE, controller.inputState)
    }

    @Test
    fun layoutSteeringEndpointsDiffer() {
        val layout = InputLayout.defaults(2000f, 1000f)
        val left = layout.steeringPointFor(-1f)
        val center = layout.steeringPointFor(0f)
        val right = layout.steeringPointFor(1f)
        assertTrue(left.first < center.first)
        assertTrue(right.first > center.first)
    }
}
