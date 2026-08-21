package com.simobr.photosweep.ui.sweep

import com.simobr.photosweep.ui.theme.PsDim

/** Which way a card went. */
enum class SweepDirection {
    /** Left. Marks the photo for trashing — still reversible until the confirm screen. */
    Sweep,

    /** Right. Keeps it. */
    Keep,
}

/**
 * The geometry of the swipe, extracted from the composable so it can be tested without a
 * touchscreen.
 *
 * Nothing here allocates or touches Compose state; it is arithmetic on floats.
 */
object SweepGesture {

    /** A card committed once it has travelled this fraction of its own width. */
    const val COMMIT_FRACTION = 0.28f

    /** …or once it is thrown this fast, however far it got. */
    const val FLING_VELOCITY_DP_PER_SECOND = 800f

    /**
     * Card tilt for a given horizontal displacement.
     *
     * The asymmetry is deliberate. Sweeping is the loud gesture and leans a full 11°; keeping
     * is quiet and barely tips at 8°. The two directions should not feel like mirror images,
     * because they do not mean mirror-image things.
     */
    fun rotation(offsetX: Float, halfWidthPx: Float): Float {
        if (halfWidthPx <= 0f) return 0f
        val fraction = offsetX / halfWidthPx
        return if (fraction < 0f) {
            (fraction * -PsDim.maxRotationSweep).coerceAtLeast(PsDim.maxRotationSweep)
        } else {
            (fraction * PsDim.maxRotationKeep).coerceAtMost(PsDim.maxRotationKeep)
        }
    }

    /**
     * Whether a release commits, and to which side.
     *
     * @param offsetX where the card ended up, relative to rest. Negative is left.
     * @param cardWidthPx the card's width.
     * @param velocityXPxPerSecond horizontal throw speed at release.
     * @param flingThresholdPxPerSecond [FLING_VELOCITY_DP_PER_SECOND] converted to pixels by
     *   the caller, which is the only place that knows the display density.
     * @return the committed direction, or null to spring back.
     */
    fun decide(
        offsetX: Float,
        cardWidthPx: Float,
        velocityXPxPerSecond: Float,
        flingThresholdPxPerSecond: Float,
    ): SweepDirection? {
        val travelled = kotlin.math.abs(offsetX) >= cardWidthPx * COMMIT_FRACTION
        if (travelled) return if (offsetX < 0f) SweepDirection.Sweep else SweepDirection.Keep

        val thrown = kotlin.math.abs(velocityXPxPerSecond) >= flingThresholdPxPerSecond
        // A flick decides by its own direction, not by where the card happens to sit: a user
        // who drags left then flicks right has changed their mind mid-gesture.
        if (thrown) return if (velocityXPxPerSecond < 0f) SweepDirection.Sweep else SweepDirection.Keep

        return null
    }

    /** How lit an edge capsule should be, 0 at rest and 1 at the commit threshold. */
    fun edgeGlow(offsetX: Float, cardWidthPx: Float): Float {
        if (cardWidthPx <= 0f) return 0f
        val progress = kotlin.math.abs(offsetX) / (cardWidthPx * COMMIT_FRACTION)
        return progress.coerceIn(0f, 1f)
    }
}
