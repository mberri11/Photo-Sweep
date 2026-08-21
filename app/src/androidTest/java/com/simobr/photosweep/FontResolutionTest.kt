package com.simobr.photosweep

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.res.ResourcesCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.simobr.photosweep.ui.theme.Manrope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Proves the three bundled Manrope TTFs are real, parseable fonts on a device, and that the
 * [Manrope] family exposes exactly the three weights the type scale asks for.
 *
 * A missing or corrupt res/font file does not fail the build — Android silently falls back
 * to the system sans, and the app merely looks slightly wrong. That is what this catches.
 */
class FontResolutionTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun allThreeWeightsResolveNonNull() {
        assertNotNull(ResourcesCompat.getFont(context, R.font.manrope_medium))
        assertNotNull(ResourcesCompat.getFont(context, R.font.manrope_bold))
        assertNotNull(ResourcesCompat.getFont(context, R.font.manrope_extrabold))
    }

    @Test
    fun fontFamilyHasExactlyThreeEntries() {
        val fonts: List<Font> = Manrope as FontListFontFamily
        assertEquals(3, fonts.size)
    }

    @Test
    fun fontFamilyWeightsAreW500W700W800() {
        val fonts: List<Font> = Manrope as FontListFontFamily
        assertEquals(
            listOf(FontWeight.W500, FontWeight.W700, FontWeight.W800),
            fonts.map { it.weight },
        )
    }
}
