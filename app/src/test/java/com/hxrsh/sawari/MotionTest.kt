package com.hxrsh.sawari

import com.hxrsh.sawari.ui.common.isReduceMotionRequested
import org.junit.Assert.assertEquals
import org.junit.Test

class MotionTest {
    @Test fun `animator scale zero means reduce motion`() {
        // (scale, expected)
        listOf(0f to true, 0.0f to true, 0.5f to false, 1f to false, 1.5f to false, 10f to false).forEach { (scale, expected) ->
            assertEquals("scale $scale", expected, isReduceMotionRequested(scale))
        }
    }
}
