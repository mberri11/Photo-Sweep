package com.simobr.photosweep

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import com.simobr.photosweep.ui.theme.PsType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The tnum test.
 *
 * Manrope's proportional digits are not the same width — in the ExtraBold instance "1" is
 * 908 font units and "8" is 1246. Six of each is a ~2000-unit difference, which is why a
 * counter rendered without tabular figures visibly shifts sideways as it counts. The `tnum`
 * feature maps every digit to a single 1240-unit advance.
 *
 * So: measure "111111" and "888888" in [PsType.bigNumber] and require identical widths. If
 * anyone drops `fontFeatureSettings = "tnum"`, or swaps in a font without the feature, this
 * fails. Nobody would catch it by looking at a static screenshot.
 */
class TabularFiguresTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bigNumberDigitsAllShareOneAdvanceWidth() {
        var narrowest by mutableStateOf(-1)
        var widest by mutableStateOf(-1)

        composeRule.setContent {
            val measurer = rememberTextMeasurer()
            narrowest = measurer.measure(AnnotatedString("111111"), PsType.bigNumber).size.width
            widest = measurer.measure(AnnotatedString("888888"), PsType.bigNumber).size.width
        }
        composeRule.waitForIdle()

        assertTrue("text was never measured", narrowest > 0 && widest > 0)
        assertEquals(
            "bigNumber lost its tabular figures: '111111' and '888888' measured differently",
            widest,
            narrowest,
        )
    }

    /** The same guarantee for the two small numeric styles. */
    @Test
    fun numericMetaStylesShareOneAdvanceWidth() {
        var pileOnes by mutableStateOf(-1)
        var pileEights by mutableStateOf(-1)
        var photoOnes by mutableStateOf(-1)
        var photoEights by mutableStateOf(-1)

        composeRule.setContent {
            val measurer = rememberTextMeasurer()
            pileOnes = measurer.measure(AnnotatedString("111111"), PsType.pileMeta).size.width
            pileEights = measurer.measure(AnnotatedString("888888"), PsType.pileMeta).size.width
            photoOnes = measurer.measure(AnnotatedString("111111"), PsType.photoMeta).size.width
            photoEights = measurer.measure(AnnotatedString("888888"), PsType.photoMeta).size.width
        }
        composeRule.waitForIdle()

        assertTrue("text was never measured", pileOnes > 0 && photoOnes > 0)
        assertEquals("pileMeta lost its tabular figures", pileEights, pileOnes)
        assertEquals("photoMeta lost its tabular figures", photoEights, photoOnes)
    }
}
