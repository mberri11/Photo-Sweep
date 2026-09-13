package com.simobr.photosweep.ui

import androidx.compose.ui.unit.dp
import com.simobr.photosweep.ui.sweep.CARD_WIDTH_FRACTION
import com.simobr.photosweep.ui.sweep.sweepCardHeight
import com.simobr.photosweep.ui.sweep.sweepCardWidth
import com.simobr.photosweep.ui.theme.PsDim
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The card is the one part of the sweep screen that is no longer a literal.
 *
 * Two properties have to hold at every width, and neither is visible by looking at a device in
 * one orientation: the card never exceeds the frozen 306dp, and its shape never changes. The
 * second matters more than it looks — the card crops the photo to its aspect ratio, so a card
 * that grew taller on one device would show a different crop of the same photo than another.
 */
class SweepCardSizeTest {

    @Test
    fun `the fraction is the documented one`() {
        assertEquals(0.78f, CARD_WIDTH_FRACTION, 0f)
    }

    @Test
    fun `a 400dp window gets the full frozen width`() {
        // 400 * 0.78 = 312, which is past the cap, so the cap wins.
        assertEquals(306f, sweepCardWidth(400.dp).value, 1e-3f)
        assertEquals(PsDim.photoCardW, sweepCardWidth(400.dp))
    }

    @Test
    fun `a 380dp window shrinks the card to the fraction`() {
        assertEquals(296.4f, sweepCardWidth(380.dp).value, 1e-3f)
    }

    @Test
    fun `the card never exceeds the frozen width at any window size`() {
        (280..900).forEach { w ->
            val width = sweepCardWidth(w.dp)
            assertTrue(
                "at ${w}dp the card was ${width.value}dp, wider than the frozen 306dp",
                width.value <= PsDim.photoCardW.value + 1e-3f,
            )
        }
    }

    @Test
    fun `the aspect ratio is 306 to 448 at every width from 280 to 900dp`() {
        val frozen = 306f / 448f
        (280..900).forEach { w ->
            val width = sweepCardWidth(w.dp)
            val height = sweepCardHeight(width)
            assertEquals(
                "aspect ratio drifted at a ${w}dp window",
                frozen,
                width.value / height.value,
                0.001f,
            )
        }
    }

    /** The cap is reached at exactly 306 / 0.78 dp of available width. */
    @Test
    fun `the cap engages where the fraction crosses the frozen width`() {
        val crossover = PsDim.photoCardW.value / CARD_WIDTH_FRACTION
        assertEquals(392.3f, crossover, 0.1f)
        assertTrue(sweepCardWidth((crossover + 1f).dp) == PsDim.photoCardW)
        assertTrue(sweepCardWidth((crossover - 10f).dp).value < PsDim.photoCardW.value)
    }
}
