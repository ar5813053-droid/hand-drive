package com.handdrive.profiles

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Calibration must NOT require isReady() — users calibrate before Start.
 * Start Controller may require readiness; calibration must not.
 */
class CalibrationGateTest {

    @Test
    fun uncalibratedProfileCanStillExistForCalibration() {
        val p = GameProfile.create("New Game")
        assertFalse(p.isReady())
        // Profile object is valid and selectable even when not calibrated
        assertTrue(p.name.isNotBlank())
        assertFalse(p.layout.calibrated)
    }

    @Test
    fun createAlwaysYieldsUsableProfile() {
        val p = GameProfile.create("")
        assertTrue(p.name.isNotBlank())
    }

    @Test
    fun readinessIsIndependentOfCalibrationEntry() {
        // Documented contract: readinessIssues for Start; calibration only needs a profile id
        val p = GameProfile.create("X")
        val issues = p.readinessIssues()
        assertTrue(issues.isNotEmpty())
        // Still has id for calibration save target
        assertTrue(p.id.isNotBlank())
    }
}
