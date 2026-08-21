package com.simobr.photosweep

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.simobr.photosweep.data.media.Photo
import com.simobr.photosweep.ui.sweep.SweepScreen
import com.simobr.photosweep.ui.sweep.SweepTags
import com.simobr.photosweep.ui.sweep.SweepUiState
import com.simobr.photosweep.ui.theme.PhotoSweepTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The commit threshold, driven through the real gesture detector on a real touchscreen.
 *
 * `SweepGestureTest` proves the arithmetic. This proves the arithmetic is actually wired to
 * the pointer input — the two can be individually correct and still not connected.
 */
class SweepScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private fun photo(id: Long) = Photo(
        id = id,
        displayName = "img_$id.jpg",
        sizeBytes = 2_000_000L,
        bucketDisplayName = "Screenshots",
        relativePath = "DCIM/Screenshots/",
        width = 1080,
        height = 2400,
        isFavorite = false,
        mimeType = "image/jpeg",
        rawDateTakenMs = 1_756_000_000_000L,
        rawDateAddedSec = null,
        rawDateModifiedSec = null,
    )

    private val state = SweepUiState(
        pileId = "screenshots",
        pileTitle = "Screenshots",
        photos = listOf(photo(1), photo(2), photo(3)),
        isLoading = false,
    )

    private fun setScreen(
        onSweep: () -> Unit = {},
        onKeep: () -> Unit = {},
    ) {
        rule.setContent {
            PhotoSweepTheme {
                SweepScreen(
                    state = state,
                    onSweep = onSweep,
                    onKeep = onKeep,
                    onUndo = {},
                    onDismissToast = {},
                    onClose = {},
                )
            }
        }
    }

    @Test
    fun dragPastThresholdCommitsASweep() {
        var swept = 0
        setScreen(onSweep = { swept++ })

        rule.onNodeWithTag(SweepTags.CARD).performTouchInput {
            down(center)
            // 50% of the card's width — comfortably past the 28% commit threshold, and slow
            // enough that it is distance committing it rather than a fling.
            moveBy(Offset(-width * 0.5f, 0f), delayMillis = 250)
            up()
        }

        rule.waitUntil(timeoutMillis = 5_000) { swept == 1 }
        assertEquals(1, swept)
    }

    @Test
    fun dragPastThresholdRightCommitsAKeep() {
        var kept = 0
        setScreen(onKeep = { kept++ })

        rule.onNodeWithTag(SweepTags.CARD).performTouchInput {
            down(center)
            moveBy(Offset(width * 0.5f, 0f), delayMillis = 250)
            up()
        }

        rule.waitUntil(timeoutMillis = 5_000) { kept == 1 }
        assertEquals(1, kept)
    }

    @Test
    fun dragBelowThresholdSpringsBackAndCommitsNothing() {
        var swept = 0
        var kept = 0
        setScreen(onSweep = { swept++ }, onKeep = { kept++ })

        val restBounds = rule.onNodeWithTag(SweepTags.CARD).getBoundsInRoot()

        rule.onNodeWithTag(SweepTags.CARD).performTouchInput {
            down(center)
            // Well under 28%, in slow steps so the release carries almost no velocity and
            // cannot be mistaken for a fling.
            repeat(5) { moveBy(Offset(-width * 0.02f, 0f), delayMillis = 120) }
            up()
        }

        // Long enough for the spring to settle.
        rule.mainClock.advanceTimeBy(1_500)
        rule.waitForIdle()

        assertEquals("a below-threshold drag must not commit a sweep", 0, swept)
        assertEquals("a below-threshold drag must not commit a keep", 0, kept)

        val settled = rule.onNodeWithTag(SweepTags.CARD).getBoundsInRoot()
        assertEquals(
            "the card did not spring back to rest",
            restBounds.left.value,
            settled.left.value,
            1.5f,
        )
    }

    @Test
    fun exactlyOneGhostCardIsBehindTheTopCard() {
        setScreen()
        // Three photos in the pile, one ghost. Not two, not a fanned deck.
        rule.onNodeWithTag(SweepTags.GHOST).assertExists()
    }
}
