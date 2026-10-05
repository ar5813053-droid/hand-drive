package com.handdrive.profiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationEditorCoordTest {

    @Test
    fun normalizedMapsAcrossResolutions() {
        val p = NormPoint(0.25f, 0.8f)
        val a = p.toPixel(1920f, 1080f)
        val b = p.toPixel(2400f, 1080f)
        assertEquals(480f, a.first, 0.5f)
        assertEquals(864f, a.second, 0.5f)
        assertEquals(600f, b.first, 0.5f)
    }

    @Test
    fun layoutCalibratedFlag() {
        val layout = ControlLayout(
            steeringCenter = NormPoint(0.5f, 0.75f),
            steeringLeft = NormPoint(0.2f, 0.75f),
            steeringRight = NormPoint(0.8f, 0.75f),
            brake = NormPoint(0.9f, 0.85f),
            throttle = NormPoint(0.7f, 0.85f),
            calibrated = true,
            calibrationScreenWidth = 2400,
            calibrationScreenHeight = 1080,
            calibrationOrientation = ScreenOrientation.LANDSCAPE
        )
        assertTrue(layout.calibrated)
        assertTrue(layout.isSteeringCalibrated())
        val p = GameProfile(name = "G", layout = layout)
        assertTrue(p.isReady())
    }

    @Test
    fun cancelDoesNotForceCalibratedWithoutSave() {
        val p = GameProfile.create("X")
        assertFalse(p.layout.calibrated)
    }

    @Test
    fun customControlPersistsInLayoutJson() {
        val cc = CustomControl(name = "Nitro", point = NormPoint(0.92f, 0.5f), activation = ControlActivation.TAP)
        val layout = ControlLayout(customControls = listOf(cc), calibrated = true)
        val round = ControlLayout.fromJson(layout.toJson())
        assertEquals(1, round.customControls.size)
        assertEquals("Nitro", round.customControls[0].name)
        assertEquals(0.92f, round.customControls[0].point.x, 0.01f)
    }
}
