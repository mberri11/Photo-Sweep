package com.simobr.photosweep

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.simobr.photosweep.ui.SweepBackGuard
import com.simobr.photosweep.ui.theme.PhotoSweepTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The back gesture on the one screen where it can cost the user an afternoon.
 *
 * Driven through the activity's real `OnBackPressedDispatcher`, not by invoking a lambda: what
 * is under test is that a back press actually reaches this screen's handler and that the
 * handler's branch depends on the mark count.
 */
class SweepBackDialogTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private var left = 0
    private var discarded = 0

    private fun setGuard(sweptCount: Int) {
        rule.setContent {
            PhotoSweepTheme {
                SweepBackGuard(
                    sweptCount = sweptCount,
                    onLeave = { left++ },
                    onDiscard = { discarded++ },
                )
            }
        }
        rule.waitForIdle()
    }

    private fun pressBack() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    @Test
    fun backWithOneMarkAsksBeforeLeaving() {
        setGuard(sweptCount = 1)
        pressBack()

        rule.onNodeWithText("Keep your marks?").assertIsDisplayed()
        rule.onNodeWithText("Keep marks").assertIsDisplayed()
        rule.onNodeWithText("Discard").assertIsDisplayed()

        assertEquals("the screen must not leave until the user answers", 0, left)
        assertEquals(0, discarded)
    }

    @Test
    fun backWithNoMarksLeavesWithoutAsking() {
        setGuard(sweptCount = 0)
        pressBack()

        rule.onNodeWithText("Keep your marks?").assertDoesNotExist()
        assertEquals("with nothing marked, back must just leave", 1, left)
        assertEquals(0, discarded)
    }

    @Test
    fun keepMarksLeavesAndDiscardsNothing() {
        setGuard(sweptCount = 12)
        pressBack()

        rule.onNodeWithText("Keep marks").performClick()
        rule.waitForIdle()

        assertEquals(1, left)
        assertEquals(0, discarded)
    }

    @Test
    fun discardClearsTheMarksInsteadOfLeavingQuietly() {
        setGuard(sweptCount = 12)
        pressBack()

        rule.onNodeWithText("Discard").performClick()
        rule.waitForIdle()

        assertEquals(1, discarded)
        assertEquals("discard must not also take the keep path", 0, left)
    }
}
