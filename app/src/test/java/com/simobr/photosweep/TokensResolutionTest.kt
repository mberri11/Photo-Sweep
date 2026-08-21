package com.simobr.photosweep

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.simobr.photosweep.ui.theme.PsColor
import com.simobr.photosweep.ui.theme.PsDim
import com.simobr.photosweep.ui.theme.PsType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Literal resolution of every frozen token.
 *
 * Deliberately dumb: each assertion restates the number from the design rather than deriving
 * it, so that changing a token without changing the design is a failing build. There is no
 * loop over the tokens on purpose — a loop would compare the file to itself.
 */
class TokensResolutionTest {

    private fun Color.argb(): Long = toArgb().toLong() and 0xFFFFFFFFL

    private fun assertDp(expected: Float, actual: Dp) =
        assertEquals(expected, actual.value, 0f)

    private fun assertSp(expected: Float, actual: TextUnit) {
        assertEquals(true, actual.isSp)
        assertEquals(expected, actual.value, 0f)
    }

    private fun assertEm(expected: Float, actual: TextUnit) {
        assertEquals(true, actual.isEm)
        assertEquals(expected, actual.value, 1e-6f)
    }

    // ---- PsColor -----------------------------------------------------------------------

    @Test
    fun colorsResolveToFrozenLongs() {
        assertEquals(0xFF080C14L, PsColor.Midnight.argb())
        assertEquals(0xFF141D2EL, PsColor.Panel.argb())
        assertEquals(0xFFE6EDF5L, PsColor.Frame.argb())
        assertEquals(0xFF2BC493L, PsColor.Keep.argb())
        assertEquals(0xFFFF6A55L, PsColor.Sweep.argb())
        assertEquals(0xFFFFC94DL, PsColor.Gold.argb())
        assertEquals(0xFFFFE49AL, PsColor.GoldHot.argb())
        assertEquals(0xFF6B7A8CL, PsColor.Steel.argb())
        assertEquals(0xFF3F4C5DL, PsColor.SteelDim.argb())
    }

    // ---- PsDim -------------------------------------------------------------------------

    @Test
    fun dimensionsResolveToFrozenDp() {
        assertDp(306f, PsDim.photoCardW)
        assertDp(448f, PsDim.photoCardH)
        assertDp(20f, PsDim.photoCardRadius)
        assertDp(2f, PsDim.cardRim)
        assertDp(20f, PsDim.screenPadH)
        assertDp(92f, PsDim.pileRowHeight)
        assertDp(64f, PsDim.bottomNavHeight)
    }

    @Test
    fun scalarsResolveToFrozenFloats() {
        assertEquals(0.94f, PsDim.ghostScale, 0f)
        assertEquals(0.30f, PsDim.ghostAlpha, 0f)
        assertEquals(-11f, PsDim.maxRotationSweep, 0f)
        assertEquals(8f, PsDim.maxRotationKeep, 0f)
        assertEquals(0.09f, PsDim.edgeCapsuleRestAlpha, 0f)
    }

    // ---- PsType ------------------------------------------------------------------------

    @Test
    fun screenTitleResolves() {
        assertSp(22f, PsType.screenTitle.fontSize)
        assertEquals(FontWeight.W800, PsType.screenTitle.fontWeight)
        assertEm(-0.01f, PsType.screenTitle.letterSpacing)
    }

    @Test
    fun bigNumberResolves() {
        assertSp(44f, PsType.bigNumber.fontSize)
        assertEquals(FontWeight.W800, PsType.bigNumber.fontWeight)
        assertEm(-0.02f, PsType.bigNumber.letterSpacing)
    }

    @Test
    fun pileNameResolves() {
        assertSp(15f, PsType.pileName.fontSize)
        assertEquals(FontWeight.W700, PsType.pileName.fontWeight)
        assertEm(0f, PsType.pileName.letterSpacing)
    }

    @Test
    fun pileMetaResolves() {
        assertSp(12f, PsType.pileMeta.fontSize)
        assertEquals(FontWeight.W500, PsType.pileMeta.fontWeight)
        assertEm(0f, PsType.pileMeta.letterSpacing)
    }

    @Test
    fun photoMetaResolves() {
        assertSp(11f, PsType.photoMeta.fontSize)
        assertEquals(FontWeight.W500, PsType.photoMeta.fontWeight)
        assertEm(0.02f, PsType.photoMeta.letterSpacing)
    }

    @Test
    fun sectionCaptionResolves() {
        assertSp(10f, PsType.sectionCaption.fontSize)
        assertEquals(FontWeight.W800, PsType.sectionCaption.fontWeight)
        assertEm(0.24f, PsType.sectionCaption.letterSpacing)
    }

    @Test
    fun buttonLabelResolves() {
        assertSp(15f, PsType.buttonLabel.fontSize)
        assertEquals(FontWeight.W700, PsType.buttonLabel.fontWeight)
        assertEm(0.01f, PsType.buttonLabel.letterSpacing)
    }

    @Test
    fun bodyResolves() {
        assertSp(14f, PsType.body.fontSize)
        assertEquals(FontWeight.W500, PsType.body.fontWeight)
        assertEm(0f, PsType.body.letterSpacing)
        // 1.55 line-height multiplier resolved against 14sp.
        assertSp(21.7f, PsType.body.lineHeight)
    }

    // ---- Tabular figures ---------------------------------------------------------------

    /**
     * The `tnum` feature is what stops "34 / 218" and the rolling MB chip from jittering
     * horizontally mid-animation. `TabularFiguresTest` proves the font honours it on a
     * device; this proves the styles still ask for it.
     */
    @Test
    fun tabularFiguresAreRequestedOnEveryNumericStyle() {
        assertEquals("tnum", PsType.bigNumber.fontFeatureSettings)
        assertEquals("tnum", PsType.pileMeta.fontFeatureSettings)
        assertEquals("tnum", PsType.photoMeta.fontFeatureSettings)
    }

    /** The non-numeric styles must not carry feature settings picked up by copy-paste. */
    @Test
    fun nonNumericStylesRequestNoFontFeatures() {
        val nonNumeric: List<TextStyle> = listOf(
            PsType.screenTitle,
            PsType.pileName,
            PsType.sectionCaption,
            PsType.buttonLabel,
            PsType.body,
        )
        nonNumeric.forEach { assertNull(it.fontFeatureSettings) }
    }
}
