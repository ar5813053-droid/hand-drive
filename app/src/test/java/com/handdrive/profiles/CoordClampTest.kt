package com.handdrive.profiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordClampTest {
    @Test
    fun coerceNormPoint() {
        val p = NormPoint(1.5f, -0.3f)
        val c = NormPoint(p.x.coerceIn(0f, 1f), p.y.coerceIn(0f, 1f))
        assertEquals(1f, c.x, 0f)
        assertEquals(0f, c.y, 0f)
    }

    @Test
    fun noDivideByZeroInToPixel() {
        val p = NormPoint(0.5f, 0.5f)
        val (x, y) = p.toPixel(0f, 0f) // still multiplies by 0
        assertEquals(0f, x, 0f)
        assertEquals(0f, y, 0f)
    }

    @Test
    fun defaultLayoutIsValid() {
        val l = ControlLayout()
        assertTrue(l.steeringCenter.x in 0f..1f)
        assertTrue(l.brake.y in 0f..1f)
    }
}
