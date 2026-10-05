package com.handdrive.profiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameProfileTest {

    @Test
    fun createHasDefaultControls() {
        val p = GameProfile.create("Asphalt 9")
        assertEquals("Asphalt 9", p.name)
        assertFalse(p.layout.calibrated)
        assertEquals(ThrottleMode.HOLD, p.throttleMode)
    }

    @Test
    fun jsonRoundTrip() {
        val layout = ControlLayout(
            steeringCenter = NormPoint(0.5f, 0.8f),
            steeringLeft = NormPoint(0.2f, 0.8f),
            steeringRight = NormPoint(0.8f, 0.8f),
            brake = NormPoint(0.9f, 0.85f),
            throttle = NormPoint(0.7f, 0.85f),
            calibrated = true,
            calibrationScreenWidth = 2400,
            calibrationScreenHeight = 1080,
            calibrationOrientation = ScreenOrientation.LANDSCAPE
        )
        val p = GameProfile(
            name = "CarX",
            layout = layout,
            sensitivity = 1.2f,
            invertSteering = true,
            throttleMode = ThrottleMode.TAP
        )
        val restored = GameProfile.fromJson(p.toJson())
        assertEquals(p.name, restored.name)
        assertEquals(p.sensitivity, restored.sensitivity, 0.01f)
        assertTrue(restored.invertSteering)
        assertEquals(ThrottleMode.TAP, restored.throttleMode)
        assertTrue(restored.layout.calibrated)
        assertEquals(0.5f, restored.layout.steeringCenter.x, 0.01f)
        assertEquals(ScreenOrientation.LANDSCAPE, restored.layout.calibrationOrientation)
    }

    @Test
    fun normPointPixelConversion() {
        val n = NormPoint(0.25f, 0.75f)
        val (px, py) = n.toPixel(2000f, 1000f)
        assertEquals(500f, px, 0.1f)
        assertEquals(750f, py, 0.1f)
        val back = NormPoint.fromPixel(px, py, 2000f, 1000f)
        assertEquals(0.25f, back.x, 0.01f)
        assertEquals(0.75f, back.y, 0.01f)
    }

    @Test
    fun customControlRoundTrip() {
        val c = CustomControl(name = "Nitro", point = NormPoint(0.9f, 0.5f), activation = ControlActivation.TAP)
        val r = CustomControl.fromJson(c.toJson())
        assertEquals("Nitro", r.name)
        assertEquals(0.9f, r.point.x, 0.01f)
        assertEquals(ControlActivation.TAP, r.activation)
    }
}
