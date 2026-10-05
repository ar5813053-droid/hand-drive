package com.handdrive.input

import com.handdrive.profiles.ControlLayout
import com.handdrive.profiles.NormPoint
import com.handdrive.profiles.ScreenOrientation
import com.handdrive.profiles.ThrottleMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class InputLayoutTest {

    @Test
    fun steeringPointMapsEndpoints() {
        val layout = InputLayout(
            steeringCenterX = 500f, steeringCenterY = 800f,
            steeringLeftX = 200f, steeringLeftY = 800f,
            steeringRightX = 800f, steeringRightY = 800f,
            brakeX = 900f, brakeY = 850f,
            throttleX = 700f, throttleY = 850f
        )
        val (lx, _) = layout.steeringPointFor(-1f)
        val (cx, _) = layout.steeringPointFor(0f)
        val (rx, _) = layout.steeringPointFor(1f)
        assertEquals(200f, lx, 1f)
        assertEquals(500f, cx, 1f)
        assertEquals(800f, rx, 1f)
    }

    @Test
    fun fromControlLayoutUsesNormalizedCoords() {
        val control = ControlLayout(
            steeringCenter = NormPoint(0.5f, 0.8f),
            steeringLeft = NormPoint(0.2f, 0.8f),
            steeringRight = NormPoint(0.8f, 0.8f),
            brake = NormPoint(0.9f, 0.85f),
            throttle = NormPoint(0.7f, 0.85f),
            calibrated = true,
            calibrationOrientation = ScreenOrientation.LANDSCAPE
        )
        val layout = InputLayout.fromControlLayout(control, 1000f, 2000f, ThrottleMode.HOLD)
        assertEquals(500f, layout.steeringCenterX, 0.1f)
        assertEquals(1600f, layout.steeringCenterY, 0.1f)
        assertEquals(900f, layout.brakeX, 0.1f)
        assertTrue(layout.calibrated)
        assertEquals(ThrottleMode.HOLD, layout.throttleMode)
    }

    @Test
    fun steeringXForCompatibleWithPhase6() {
        val layout = InputLayout.defaults(1000f, 2000f)
        assertTrue(abs(layout.steeringXFor(0f) - layout.steeringCenterX) < 1f)
        assertTrue(layout.steeringXFor(-1f) < layout.steeringCenterX)
        assertTrue(layout.steeringXFor(1f) > layout.steeringCenterX)
    }
}
