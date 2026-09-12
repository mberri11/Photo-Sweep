package com.simobr.photosweep.ui

import com.simobr.photosweep.data.media.Photo
import com.simobr.photosweep.ui.sweep.SweepDecision
import com.simobr.photosweep.ui.sweep.SweepDirection
import com.simobr.photosweep.ui.sweep.SweepUiState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The counter the user reads.
 *
 * On a device the first card of a 1,721-photo pile read `0 / 1721`, which is the internal
 * cursor printed straight to the screen. The cursor is right; printing it is not.
 */
class SweepUiStateTest {

    private fun photo(id: Long) = Photo(
        id = id,
        displayName = "img_$id.jpg",
        sizeBytes = 1_000_000L,
        bucketDisplayName = "Camera",
        relativePath = "DCIM/Camera/",
        width = 4032,
        height = 3024,
        isFavorite = false,
        mimeType = "image/jpeg",
        rawDateTakenMs = 1_756_000_000_000L,
        rawDateAddedSec = null,
        rawDateModifiedSec = null,
    )

    private fun decision(id: Long) = SweepDecision(
        photoId = id,
        direction = SweepDirection.Keep,
        sizeBytes = 1_000_000L,
        decidedAt = 0L,
    )

    private fun state(total: Int, decided: Int) = SweepUiState(
        pileId = "camera",
        pileTitle = "Camera",
        photos = (1L..total).map(::photo),
        decisions = (1L..decided).map(::decision),
        isLoading = false,
    )

    @Test
    fun `the first card of a full pile reads one, not zero`() {
        val s = state(total = 218, decided = 0)
        assertEquals(0, s.currentIndex)
        assertEquals(1, s.displayIndex)
    }

    @Test
    fun `a finished pile reads the total, not one past it`() {
        val s = state(total = 218, decided = 218)
        assertEquals(218, s.currentIndex)
        assertEquals(218, s.displayIndex)
    }

    @Test
    fun `an empty pile reads zero, not one`() {
        val s = state(total = 0, decided = 0)
        assertEquals(0, s.displayIndex)
    }

    @Test
    fun `the display position is always one ahead of the cursor mid-pile`() {
        (0..217).forEach { decided ->
            assertEquals(decided + 1, state(total = 218, decided = decided).displayIndex)
        }
    }

    /** The cursor drives the geometry and must not have moved with the label. */
    @Test
    fun `progress still tracks the cursor, not the display position`() {
        val s = state(total = 4, decided = 1)
        assertEquals(0.25f, s.progress, 0.0001f)
        assertEquals(2, s.displayIndex)
    }
}
