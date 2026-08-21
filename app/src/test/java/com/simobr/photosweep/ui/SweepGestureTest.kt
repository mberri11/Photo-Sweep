package com.simobr.photosweep.ui

import com.simobr.photosweep.ui.sweep.SweepDirection
import com.simobr.photosweep.ui.sweep.SweepGesture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The feel of the gesture, as arithmetic.
 *
 * These numbers are the difference between a card that reads as a physical object and one
 * that reads as a div, so they are pinned rather than left to drift.
 */
class SweepGestureTest {

    private val halfWidth = 400f
    private val cardWidth = 800f

    @Test
    fun `a full sweep leans eleven degrees counter-clockwise`() {
        assertEquals(-11f, SweepGesture.rotation(-halfWidth, halfWidth), 0.001f)
    }

    @Test
    fun `a full keep leans only eight degrees`() {
        assertEquals(8f, SweepGesture.rotation(halfWidth, halfWidth), 0.001f)
    }

    @Test
    fun `the asymmetry survives past the extremes`() {
        // Sweep is the loud gesture and keep the quiet one; dragging further must not let
        // keep catch up with sweep.
        assertEquals(-11f, SweepGesture.rotation(-halfWidth * 4, halfWidth), 0.001f)
        assertEquals(8f, SweepGesture.rotation(halfWidth * 4, halfWidth), 0.001f)
    }

    @Test
    fun `rest is level`() {
        assertEquals(0f, SweepGesture.rotation(0f, halfWidth), 0.001f)
    }

    @Test
    fun `a zero-width card does not divide by zero`() {
        assertEquals(0f, SweepGesture.rotation(120f, 0f), 0.001f)
    }

    @Test
    fun `just under twenty-eight percent springs back`() {
        val justUnder = cardWidth * 0.279f
        assertNull(SweepGesture.decide(-justUnder, cardWidth, 0f, 2000f))
        assertNull(SweepGesture.decide(justUnder, cardWidth, 0f, 2000f))
    }

    @Test
    fun `twenty-eight percent commits`() {
        assertEquals(
            SweepDirection.Sweep,
            SweepGesture.decide(-cardWidth * 0.28f, cardWidth, 0f, 2000f),
        )
        assertEquals(
            SweepDirection.Keep,
            SweepGesture.decide(cardWidth * 0.28f, cardWidth, 0f, 2000f),
        )
    }

    @Test
    fun `a fast fling commits from almost nowhere`() {
        assertEquals(
            SweepDirection.Sweep,
            SweepGesture.decide(-8f, cardWidth, -2500f, 2000f),
        )
        assertEquals(
            SweepDirection.Keep,
            SweepGesture.decide(8f, cardWidth, 2500f, 2000f),
        )
    }

    @Test
    fun `a flick decides by its own direction not by where the card sits`() {
        // Dragged left, then flicked right: the user changed their mind mid-gesture and the
        // card must follow the hand, not the history.
        assertEquals(
            SweepDirection.Keep,
            SweepGesture.decide(-40f, cardWidth, 3000f, 2000f),
        )
    }

    @Test
    fun `a slow drag below threshold is not a fling`() {
        assertNull(SweepGesture.decide(-100f, cardWidth, -1999f, 2000f))
    }

    @Test
    fun `edge glow runs zero to one and saturates at the commit threshold`() {
        assertEquals(0f, SweepGesture.edgeGlow(0f, cardWidth), 0.001f)
        assertEquals(0.5f, SweepGesture.edgeGlow(-cardWidth * 0.14f, cardWidth), 0.01f)
        assertEquals(1f, SweepGesture.edgeGlow(-cardWidth * 0.28f, cardWidth), 0.001f)
        assertEquals(1f, SweepGesture.edgeGlow(-cardWidth, cardWidth), 0.001f)
    }
}
