package com.simobr.photosweep.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.simobr.photosweep.R

/**
 * Frozen design tokens for Photo Sweep.
 *
 * Every value here is fixed by the design and is asserted literally by
 * `TokensResolutionTest`. Do not change a number in this file without changing the test in
 * the same commit — the test exists precisely so that a drifting token is a build failure
 * rather than something a human has to spot by eye.
 */

/** Frozen palette. */
object PsColor {
    /** Page ground. */
    val Midnight = Color(0xFF080C14)

    /** Raised surfaces: cards, sheets, rows. */
    val Panel = Color(0xFF141D2E)

    /** Primary text and the photo card rim. */
    val Frame = Color(0xFFE6EDF5)

    /** Keep — the swipe-right affordance. */
    val Keep = Color(0xFF2BC493)

    /** Sweep — the swipe-left, destructive affordance. */
    val Sweep = Color(0xFFFF6A55)

    /** Accent. */
    val Gold = Color(0xFFFFC94D)

    /** Accent, hot variant. */
    val GoldHot = Color(0xFFFFE49A)

    /** Secondary text. */
    val Steel = Color(0xFF6B7A8C)

    /** Tertiary text, disabled states, hairlines. */
    val SteelDim = Color(0xFF3F4C5D)
}

/** Frozen metrics. */
object PsDim {
    val photoCardW = 306.dp
    val photoCardH = 448.dp
    val photoCardRadius = 20.dp

    val cardRim = 2.dp

    /** Scale of the next card peeking behind the top card. */
    const val ghostScale = 0.94f

    /** Alpha of the next card peeking behind the top card. */
    const val ghostAlpha = 0.30f

    /** Degrees of card rotation at a full sweep (left). Negative: counter-clockwise. */
    const val maxRotationSweep = -11f

    /** Degrees of card rotation at a full keep (right). */
    const val maxRotationKeep = 8f

    val screenPadH = 20.dp
    val pileRowHeight = 92.dp
    val bottomNavHeight = 64.dp

    /** Alpha of the edge capsules when idle, before a drag lights them up. */
    const val edgeCapsuleRestAlpha = 0.09f
}

/**
 * Manrope, bundled as three static instances. No downloadable fonts: the app is offline,
 * and a font that arrives over the network is a font that sometimes does not arrive.
 *
 * The three entries are asserted by `FontResolutionTest`.
 */
val Manrope = FontFamily(
    Font(R.font.manrope_medium, FontWeight.W500),
    Font(R.font.manrope_bold, FontWeight.W700),
    Font(R.font.manrope_extrabold, FontWeight.W800),
)

/**
 * Tabular figures.
 *
 * Mandatory on every style that renders a number which changes while it is on screen:
 * [PsType.bigNumber], [PsType.pileMeta], [PsType.photoMeta], and the sweep index counter.
 * Manrope ships the OpenType `tnum` feature, which forces every digit to one advance width.
 * Without it "34 / 218" and the rolling MB chip re-measure on every frame of the roll
 * animation and shift horizontally — the app reads as broken. `TabularFiguresTest` is the
 * regression guard.
 */
private const val TNUM = "tnum"

/** Frozen type scale. */
object PsType {
    val screenTitle = TextStyle(
        fontFamily = Manrope,
        fontSize = 22.sp,
        fontWeight = FontWeight.W800,
        letterSpacing = (-0.01).em,
    )

    val bigNumber = TextStyle(
        fontFamily = Manrope,
        fontSize = 44.sp,
        fontWeight = FontWeight.W800,
        letterSpacing = (-0.02).em,
        fontFeatureSettings = TNUM,
    )

    val pileName = TextStyle(
        fontFamily = Manrope,
        fontSize = 15.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = 0.em,
    )

    val pileMeta = TextStyle(
        fontFamily = Manrope,
        fontSize = 12.sp,
        fontWeight = FontWeight.W500,
        letterSpacing = 0.em,
        fontFeatureSettings = TNUM,
    )

    val photoMeta = TextStyle(
        fontFamily = Manrope,
        fontSize = 11.sp,
        fontWeight = FontWeight.W500,
        letterSpacing = 0.02.em,
        fontFeatureSettings = TNUM,
    )

    /**
     * Section captions are rendered in uppercase. `TextStyle` carries no case transform, so
     * the call site must uppercase the string itself — the 0.24em tracking is drawn for
     * capitals and looks wrong on lowercase.
     */
    val sectionCaption = TextStyle(
        fontFamily = Manrope,
        fontSize = 10.sp,
        fontWeight = FontWeight.W800,
        letterSpacing = 0.24.em,
    )

    val buttonLabel = TextStyle(
        fontFamily = Manrope,
        fontSize = 15.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = 0.01.em,
    )

    /** lineHeight is the 1.55 multiplier resolved against the 14sp size. */
    val body = TextStyle(
        fontFamily = Manrope,
        fontSize = 14.sp,
        fontWeight = FontWeight.W500,
        letterSpacing = 0.em,
        lineHeight = 21.7.sp,
    )
}
