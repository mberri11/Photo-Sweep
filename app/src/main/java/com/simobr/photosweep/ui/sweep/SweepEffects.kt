package com.simobr.photosweep.ui.sweep

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.simobr.photosweep.ui.theme.PsColor

/** Alive for 300ms, then a 200ms fade. 0.6 is where one ends and the other begins. */
const val COMET_ALIVE_FRACTION = 0.6f
const val COMET_TOTAL_MS = 500
const val KEEP_PULSE_MS = 380

/**
 * Opacity envelope shared by both release effects: flat while alive, linear out while fading.
 * Pure, so the timing can be reasoned about without watching it.
 */
fun releaseAlpha(progress: Float): Float = when {
    progress <= 0f -> 0f
    progress < COMET_ALIVE_FRACTION -> 1f
    progress >= 1f -> 0f
    else -> 1f - (progress - COMET_ALIVE_FRACTION) / (1f - COMET_ALIVE_FRACTION)
}

/**
 * The coral comet left behind by a sweep.
 *
 * Drawn **where the card has been**, not where it is going. A trail that runs ahead of the
 * card reads as the app throwing the photo away; a trail behind it reads as the photo having
 * been pulled out of the stack by the user. The gesture belongs to the person doing it.
 *
 * Three stacked radial gradients — a bright head just behind the departing card, then two
 * progressively wider and dimmer bodies spreading to the right — plus three sparks scattered
 * along the tail.
 *
 * @param progress 0f..1f across [COMET_TOTAL_MS].
 */
@Composable
fun CometTrail(progress: Float, modifier: Modifier = Modifier) {
    val alpha = releaseAlpha(progress)
    if (alpha <= 0f) return

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // The tail spreads as it ages, the way a real one would.
        val spread = 1f + progress * 0.45f

        val lobes = listOf(
            Triple(0.30f, 0.52f, 0.34f) to 0.85f,
            Triple(0.55f, 0.57f, 0.46f) to 0.45f,
            Triple(0.80f, 0.63f, 0.58f) to 0.22f,
        )
        lobes.forEach { (geometry, intensity) ->
            val (fx, fy, fr) = geometry
            val centre = Offset(w * fx, h * fy)
            val radius = w * fr * spread
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        PsColor.Sweep.copy(alpha = intensity * alpha),
                        PsColor.Sweep.copy(alpha = intensity * alpha * 0.35f),
                        Color.Transparent,
                    ),
                    center = centre,
                    radius = radius,
                ),
                radius = radius,
                center = centre,
            )
        }

        // Three sparks, drifting outward along the tail as it fades.
        val sparks = listOf(
            Triple(0.46f, 0.44f, 3.4f),
            Triple(0.66f, 0.68f, 2.6f),
            Triple(0.86f, 0.50f, 2.0f),
        )
        sparks.forEach { (fx, fy, r) ->
            val drift = progress * w * 0.06f
            drawCircle(
                color = PsColor.GoldHot.copy(alpha = alpha * 0.9f),
                radius = r * density,
                center = Offset(w * fx + drift, h * fy - drift * 0.4f),
            )
        }
    }
}

/**
 * The keep acknowledgement: one soft green pulse from the right edge, and a short wake on the
 * left where the card just was.
 *
 * Deliberately much less than the comet. Keeping a photo is the common case and the quiet
 * one — if it celebrated as loudly as a sweep, a run of two hundred keeps would be exhausting
 * and the two gestures would stop feeling different.
 */
@Composable
fun KeepPulse(progress: Float, modifier: Modifier = Modifier) {
    val alpha = releaseAlpha(progress)
    if (alpha <= 0f) return

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Pulse in from the right edge.
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    PsColor.Keep.copy(alpha = 0.16f * alpha),
                    PsColor.Keep.copy(alpha = 0.34f * alpha),
                ),
                startX = w * (0.55f - progress * 0.15f),
                endX = w,
            ),
        )

        // Short wake behind the card, which went right — so the wake is on the left.
        val wakeCentre = Offset(w * (0.34f + progress * 0.10f), h * 0.5f)
        val wakeRadius = w * 0.28f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    PsColor.Keep.copy(alpha = 0.22f * alpha),
                    Color.Transparent,
                ),
                center = wakeCentre,
                radius = wakeRadius,
            ),
            radius = wakeRadius,
            center = wakeCentre,
        )
    }
}
