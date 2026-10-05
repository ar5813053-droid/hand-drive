package com.handdrive.domain

import com.handdrive.steering.SteeringEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationTest {

    private val engine = SteeringEngine()

    @Test
    fun defaultCalibrationIsNotMarkedCalibrated() {
        assertFalse(CalibrationData.DEFAULT.isCalibrated)
    }

    @Test
    fun calibratedDataMapsEndpoints() {
        val cal = CalibrationData(-0.1f, -0.8f, 0.7f, isCalibrated = true)
        assertEquals(-1f, engine.mapToNormalized(-0.8f, cal), 0.05f)
        assertEquals(0f, engine.mapToNormalized(-0.1f, cal), 0.05f)
        assertEquals(1f, engine.mapToNormalized(0.7f, cal), 0.05f)
    }

    @Test
    fun degenerateSpanDoesNotCrash() {
        val cal = CalibrationData(0f, 0f, 0f, isCalibrated = true)
        val v = engine.mapToNormalized(0.5f, cal)
        assertTrue(v in -1f..1f)
    }
}
