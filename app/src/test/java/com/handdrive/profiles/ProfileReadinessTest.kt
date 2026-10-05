package com.handdrive.profiles

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileReadinessTest {

    @Test
    fun uncalibratedProfileNotReady() {
        val p = GameProfile.create("Test")
        assertFalse(p.isReady())
        assertTrue(p.readinessIssues().isNotEmpty())
    }

    @Test
    fun calibratedSteeringRangeIsReady() {
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
        val p = GameProfile(name = "Ready", layout = layout)
        assertTrue(p.isReady())
        assertTrue(p.readinessIssues().isEmpty())
    }

    @Test
    fun collapsedSteeringNotReady() {
        val layout = ControlLayout(
            steeringCenter = NormPoint(0.5f, 0.8f),
            steeringLeft = NormPoint(0.5f, 0.8f),
            steeringRight = NormPoint(0.5f, 0.8f),
            calibrated = true
        )
        val p = GameProfile(name = "Bad", layout = layout)
        assertFalse(p.isReady())
    }

    @Test
    fun compatibilityDefaultsUntested() {
        val p = GameProfile.create("X")
        assertTrue(p.compatibilityStatus == CompatibilityStatus.UNTESTED)
    }

    @Test
    fun normPointClampsOutOfRange() {
        val n = NormPoint(1.5f, -0.2f)
        val (x, y) = n.toPixel(1000f, 1000f)
        assertTrue(x <= 1000f)
        assertTrue(y >= 0f)
    }

    @Test
    fun differentResolutionsScale() {
        val n = NormPoint(0.5f, 0.5f)
        val a = n.toPixel(1920f, 1080f)
        val b = n.toPixel(2400f, 1080f)
        assertTrue(a.first < b.first)
        assertTrue(kotlin.math.abs(a.second - b.second) < 0.1f)
    }
}
