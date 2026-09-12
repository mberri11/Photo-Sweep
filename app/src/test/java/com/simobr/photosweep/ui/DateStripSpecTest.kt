package com.simobr.photosweep.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.simobr.photosweep.ui.sweep.DateStripSpec
import com.simobr.photosweep.ui.theme.PsColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The date strip's contrast spec.
 *
 * A colour on screen is not testable here — that is a device question. What is testable is
 * that the constants the strip is drawn from still say what the fix said they should, so a
 * later edit that quietly puts Steel back, or flattens the gradient to two stops, fails here
 * instead of on somebody's white photo.
 */
class DateStripSpecTest {

    @Test
    fun `the gradient has a run-up above the first line of text`() {
        assertEquals(28.dp, DateStripSpec.gradientRunUp)
        assertEquals(
            "the container's top inset is the run-up plus the text's own padding",
            DateStripSpec.gradientRunUp + DateStripSpec.textPadV,
            DateStripSpec.topPad,
        )
        assertEquals(42.dp, DateStripSpec.topPad)
    }

    @Test
    fun `the gradient is three stops, ending near opaque Midnight`() {
        val stops = DateStripSpec.stops
        assertEquals(3, stops.size)

        assertEquals(0f, stops[0].first)
        assertEquals(Color.Transparent, stops[0].second)

        assertEquals(0.45f, stops[1].first)
        assertEquals(PsColor.Midnight.copy(alpha = 0.72f), stops[1].second)

        assertEquals(1f, stops[2].first)
        assertEquals(PsColor.Midnight.copy(alpha = 0.92f), stops[2].second)
    }

    @Test
    fun `the stops are monotonic in both position and opacity`() {
        val stops = DateStripSpec.stops
        stops.toList().zipWithNext { a, b ->
            assertTrue("stop positions must ascend", b.first > a.first)
            assertTrue("scrim must only ever darken downward", b.second.alpha >= a.second.alpha)
        }
    }

    /**
     * Steel is a mid-grey picked against a known dark panel. Over an unknown photo it is
     * unreadable regardless of how much scrim sits behind it.
     */
    @Test
    fun `the detail line is dimmed Frame, never Steel`() {
        assertNotEquals(PsColor.Steel, DateStripSpec.detailColour)
        assertEquals(PsColor.Frame.copy(alpha = 0.8f), DateStripSpec.detailColour)
        assertEquals(PsColor.Frame, DateStripSpec.stampColour)
    }
}
