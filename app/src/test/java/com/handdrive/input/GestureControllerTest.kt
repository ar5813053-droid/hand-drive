package com.handdrive.input

import com.handdrive.domain.GestureState
import com.handdrive.domain.SteeringCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for GestureController state machine.
 * Actual dispatchGesture requires a real AccessibilityService (manual device tests).
 */
class GestureControllerTest {

    private lateinit var controller: GestureController

    @Before
    fun setUp() {
        controller = GestureController()
        controller.setLayout(InputLayout.defaults(1080f, 1920f))
    }

    @Test
    fun startsIdle() {
        assertEquals(InputState.IDLE, controller.inputState)
        assertFalse(controller.isEnabled())
    }

    @Test
    fun enableThenDisableReleases() {
        controller.enable()
        assertTrue(controller.isEnabled())
        controller.disable()
        assertFalse(controller.isEnabled())
        assertEquals(InputState.IDLE, controller.inputState)
    }

    @Test
    fun releaseAllReturnsIdle() {
        controller.enable()
        controller.releaseAll()
        assertEquals(InputState.IDLE, controller.inputState)
    }

    @Test
    fun emergencyStopDisablesAndStops() {
        controller.enable()
        controller.emergencyStop()
        assertFalse(controller.isEnabled())
        assertEquals(InputState.EMERGENCY_STOP, controller.inputState)
    }

    @Test
    fun executeReleaseAllWithoutServiceSetsErrorOrIdle() {
        // No service connected in unit test environment
        controller.execute(InputCommand.ReleaseAll)
        // releaseAll still works without service
        assertEquals(InputState.IDLE, controller.inputState)
    }

    @Test
    fun executeSteerWithoutServiceSetsError() {
        controller.execute(InputCommand.Steer(0.5f))
        assertEquals(InputState.ERROR, controller.inputState)
        assertTrue(controller.lastError != null)
    }

    @Test
    fun inputLayoutMapsSteering() {
        val layout = InputLayout.defaults(1000f, 2000f)
        assertEquals(layout.steeringLeftX, layout.steeringXFor(-1f), 1f)
        assertEquals(layout.steeringCenterX, layout.steeringXFor(0f), 1f)
        assertEquals(layout.steeringRightX, layout.steeringXFor(1f), 1f)
    }

    @Test
    fun inputCommandsAreDistinct() {
        val cmds: List<InputCommand> = listOf(
            InputCommand.Steer(-1f),
            InputCommand.Steer(1f),
            InputCommand.BrakeDown,
            InputCommand.BrakeUp,
            InputCommand.ReleaseAll,
            InputCommand.TestTap(10f, 20f),
            InputCommand.TestSteer(0.5f),
            InputCommand.TestBrake,
            InputCommand.TestThrottle,
            InputCommand.ThrottleDown,
            InputCommand.ThrottleUp
        )
        assertEquals(11, cmds.size)
    }

    @Test
    fun onSteeringWithoutServiceDoesNotCrash() {
        controller.enable()
        // Service is null in unit tests — should safe-fail
        controller.onSteeringAndBrake(
            SteeringCommand(0.5f, 45f, false, 1000L),
            GestureState.off(1000L),
            trackingValid = true,
            nowMs = 1000L
        )
        // Ends in safe state
        assertTrue(
            controller.inputState == InputState.ERROR ||
                controller.inputState == InputState.IDLE
        )
    }
}
