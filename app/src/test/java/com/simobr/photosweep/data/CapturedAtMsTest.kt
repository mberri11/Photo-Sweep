package com.simobr.photosweep.data

import com.simobr.photosweep.data.media.Photo
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The unit trap.
 *
 * MediaStore reports capture time in three columns with two different units: `datetaken` in
 * milliseconds, `date_added` and `date_modified` in seconds. Multiplying the wrong one — or
 * failing to multiply at all — dates a 2026 photo to 1970 or to the year 57000, and the only
 * symptom is that the month piles look wrong, which nobody notices until a user complains.
 *
 * So every combination is pinned here, including all eight null permutations.
 */
class CapturedAtMsTest {

    private companion object {
        const val TAKEN_MS = 1_756_000_000_000L      // ms since epoch
        const val ADDED_SEC = 1_755_000_000L         // seconds
        const val MODIFIED_SEC = 1_754_000_000L      // seconds
    }

    private fun resolve(taken: Long?, added: Long?, modified: Long?) =
        Photo.resolveCapturedAtMs(taken, added, modified)

    // ---- all eight null combinations ---------------------------------------------------

    @Test
    fun `1 of 8 - all three present - datetaken wins and is not multiplied`() {
        assertEquals(TAKEN_MS, resolve(TAKEN_MS, ADDED_SEC, MODIFIED_SEC))
    }

    @Test
    fun `2 of 8 - datetaken and dateadded - datetaken wins`() {
        assertEquals(TAKEN_MS, resolve(TAKEN_MS, ADDED_SEC, null))
    }

    @Test
    fun `3 of 8 - datetaken and datemodified - datetaken wins`() {
        assertEquals(TAKEN_MS, resolve(TAKEN_MS, null, MODIFIED_SEC))
    }

    @Test
    fun `4 of 8 - datetaken only`() {
        assertEquals(TAKEN_MS, resolve(TAKEN_MS, null, null))
    }

    @Test
    fun `5 of 8 - no datetaken - dateadded wins and is multiplied by 1000`() {
        assertEquals(ADDED_SEC * 1000L, resolve(null, ADDED_SEC, MODIFIED_SEC))
    }

    @Test
    fun `6 of 8 - dateadded only - multiplied by 1000`() {
        assertEquals(ADDED_SEC * 1000L, resolve(null, ADDED_SEC, null))
    }

    @Test
    fun `7 of 8 - datemodified only - multiplied by 1000`() {
        assertEquals(MODIFIED_SEC * 1000L, resolve(null, null, MODIFIED_SEC))
    }

    @Test
    fun `8 of 8 - all null - zero rather than a crash`() {
        assertEquals(0L, resolve(null, null, null))
    }

    // ---- zero is MediaStore's other way of saying null ---------------------------------

    @Test
    fun `zero datetaken falls through instead of dating the photo to 1970`() {
        assertEquals(ADDED_SEC * 1000L, resolve(0L, ADDED_SEC, MODIFIED_SEC))
    }

    @Test
    fun `zero datetaken and zero dateadded fall through to datemodified`() {
        assertEquals(MODIFIED_SEC * 1000L, resolve(0L, 0L, MODIFIED_SEC))
    }

    @Test
    fun `all zero resolves to zero`() {
        assertEquals(0L, resolve(0L, 0L, 0L))
    }

    // ---- the unit error this whole class exists to catch --------------------------------

    @Test
    fun `seconds are never mistaken for milliseconds`() {
        // If date_added were read as milliseconds it would resolve to 1970-01-21.
        val wrongIfUnmultiplied = ADDED_SEC
        val actual = resolve(null, ADDED_SEC, null)
        assertEquals(ADDED_SEC * 1000L, actual)
        assertEquals(1000L, actual / wrongIfUnmultiplied)
    }

    @Test
    fun `milliseconds are never multiplied again`() {
        // If datetaken were multiplied it would resolve to the year 57000.
        assertEquals(TAKEN_MS, resolve(TAKEN_MS, null, null))
    }

    // ---- the property on Photo uses the same function ------------------------------------

    @Test
    fun `Photo capturedAtMs matches the resolver`() {
        val photo = photo(taken = null, added = ADDED_SEC, modified = MODIFIED_SEC)
        assertEquals(resolve(null, ADDED_SEC, MODIFIED_SEC), photo.capturedAtMs)
    }

    private fun photo(taken: Long?, added: Long?, modified: Long?) = Photo(
        id = 1L,
        displayName = "x.jpg",
        sizeBytes = 1L,
        bucketDisplayName = null,
        relativePath = null,
        width = 1,
        height = 1,
        isFavorite = false,
        mimeType = "image/jpeg",
        rawDateTakenMs = taken,
        rawDateAddedSec = added,
        rawDateModifiedSec = modified,
    )
}
