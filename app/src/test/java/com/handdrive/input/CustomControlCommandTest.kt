package com.handdrive.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomControlCommandTest {

    @Test
    fun customTapAndHoldAreDistinctCommands() {
        val tap = InputCommand.CustomTap(100f, 200f)
        val hold = InputCommand.CustomHold(100f, 200f, down = true)
        assertTrue(tap is InputCommand.CustomTap)
        assertTrue(hold is InputCommand.CustomHold)
        assertEquals(100f, (tap as InputCommand.CustomTap).x, 0.01f)
        assertTrue((hold as InputCommand.CustomHold).down)
    }

    @Test
    fun customHoldUpIsNotDown() {
        val up = InputCommand.CustomHold(10f, 20f, down = false)
        assertTrue(!up.down)
    }
}
