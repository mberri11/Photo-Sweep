package com.simobr.photosweep.ui.sweep

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.simobr.photosweep.ui.theme.PsColor

/**
 * The measurable half of the photo card's date strip.
 *
 * It lives outside the composable so it can be asserted by a JVM unit test. A colour that
 * only exists as an argument inside a `@Composable` is a colour no test can reach, and this
 * one has already been wrong once: the strip was a two-stop gradient sized to the text
 * Column, which put the first line of text at roughly a tenth of the scrim's opacity. Over a
 * bright photo the capture stamp simply vanished.
 *
 * The fix is three stops and a run-up: the gradient starts [gradientRunUp] above the first
 * line so the text sits in the strong end of it rather than at the leading edge, and the
 * detail line is a dimmed [PsColor.Frame] rather than [PsColor.Steel] — Steel is a
 * mid-grey chosen against a known dark panel, and over an unknown photo it is unreadable no
 * matter how much scrim is behind it.
 */
internal object DateStripSpec {

    /** Pure gradient above the first text line, before any padding the text itself needs. */
    val gradientRunUp = 28.dp

    /** The strip's own breathing room, unchanged from the original card. */
    val textPadH = 16.dp
    val textPadV = 14.dp

    /** Top inset of the gradient container: [gradientRunUp] on top of the text's own padding. */
    val topPad = gradientRunUp + textPadV

    /**
     * Gradient stops, top to bottom. Three, not two: the midpoint carries most of the
     * darkening so the capture stamp lands on ~0.72 alpha instead of ~0.1.
     */
    val stops: Array<Pair<Float, Color>> = arrayOf(
        0f to Color.Transparent,
        0.45f to PsColor.Midnight.copy(alpha = 0.72f),
        1f to PsColor.Midnight.copy(alpha = 0.92f),
    )

    /** The capture stamp. Unchanged. */
    val stampColour: Color = PsColor.Frame

    /** The detail line: file name, size, dimensions. Was Steel. */
    val detailColour: Color = PsColor.Frame.copy(alpha = 0.8f)
}
